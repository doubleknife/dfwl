package com.dfwl.fleet.master.validation;

import com.dfwl.fleet.master.dto.request.DriverRequest;
import com.dfwl.fleet.master.dto.request.VehicleRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** One source of create-field and uniqueness validation for forms and the initial Excel import. */
@Component
public class MasterDataInputValidator {
    public record Issue(String field, String reason) { }
    public record Existing(Set<String> phones, Set<String> accounts, Set<String> idCards,
                           Set<String> drivingLicenses, Set<String> plates, Set<String> vehicleLicenses) { }

    private final JdbcTemplate jdbc;
    public MasterDataInputValidator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Existing existing(List<DriverRequest> drivers, List<VehicleRequest> vehicles) {
        return new Existing(lookup("driver", "phone", drivers.stream().map(DriverRequest::phone).toList()),
                lookup("sys_user", "phone", drivers.stream().map(DriverRequest::phone).toList()),
                lookup("driver", "id_card_no", drivers.stream().map(DriverRequest::idCardNo)
                        .map(value -> value == null ? null : value.trim().toUpperCase()).toList()),
                lookup("driver", "driving_license_no", drivers.stream().map(DriverRequest::drivingLicenseNo)
                        .map(value -> value == null ? null : value.trim()).toList()),
                lookup("vehicle", "plate_no", vehicles.stream().map(VehicleRequest::plateNo).toList()),
                lookup("vehicle", "vehicle_license_no", vehicles.stream().map(VehicleRequest::vehicleLicenseNo)
                        .map(value -> value == null ? null : value.trim()).toList()));
    }

    private Set<String> lookup(String table, String column, List<String> values) {
        List<String> keys = values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (keys.isEmpty()) return Set.of();
        String sql = "SELECT " + column + " FROM " + table + " WHERE " + column + " IN ("
                + String.join(",", Collections.nCopies(keys.size(), "?")) + ")";
        return new HashSet<>(jdbc.queryForList(sql, String.class, keys.toArray()));
    }

    public List<Issue> driver(DriverRequest request, Existing existing, boolean accountRequired) {
        List<Issue> issues = new ArrayList<>();
        if (!"INTERNAL".equals(request.driverType()) && !"OUTSOURCED".equals(request.driverType()))
            issues.add(new Issue("司机类型", "司机类型不正确"));
        required(issues, "驾驶员姓名", request.name(), 50);
        required(issues, "电话", request.phone(), 32);
        if (text(request.phone()) && !request.phone().matches("1[0-9]{10}"))
            issues.add(new Issue("电话", "电话须为 11 位手机号"));
        required(issues, "驾驶证号", request.drivingLicenseNo(), 64);
        required(issues, "身份证号", request.idCardNo(), 18);
        if (text(request.idCardNo()) && MainlandIdCard.birthDateOrNull(request.idCardNo().toUpperCase()) == null)
            issues.add(new Issue("身份证号", "身份证号的格式、出生日期或校验码不正确"));
        if (request.status() == null || request.status() != 0 && request.status() != 1)
            issues.add(new Issue("状态", "状态不正确"));
        if (existing != null) {
            if (existing.phones().contains(request.phone())) issues.add(new Issue("电话", "司机电话已存在"));
            if (accountRequired && existing.accounts().contains(request.phone()))
                issues.add(new Issue("电话", "该手机号已有登录账号，不能新增司机"));
            if (existing.idCards().contains(request.idCardNo() == null ? null : request.idCardNo().trim().toUpperCase()))
                issues.add(new Issue("身份证号", "身份证号已被其他司机使用"));
            if (existing.drivingLicenses().contains(request.drivingLicenseNo() == null ? null : request.drivingLicenseNo().trim()))
                issues.add(new Issue("驾驶证号", "驾驶证号已被其他司机使用"));
        }
        if (accountRequired && (request.initialPassword() == null || request.initialPassword().length() < 6))
            issues.add(new Issue("初始密码", "初始密码至少 6 位"));
        return issues;
    }

    public List<Issue> vehicle(VehicleRequest request, Existing existing) {
        List<Issue> issues = new ArrayList<>();
        required(issues, "车牌号", request.plateNo(), 32);
        required(issues, "行车证号", request.vehicleLicenseNo(), 64);
        if (request.insuranceComplete() == null) issues.add(new Issue("车辆保险状态(是否齐全)", "保险状态必填"));
        if (!"ELECTRIC".equals(request.energyType()) && !"GAS".equals(request.energyType()))
            issues.add(new Issue("能源类型", "能源类型只能为电或气"));
        positiveTons(issues, "最大载重（吨）", request.maxLoad());
        if (!"FIXED".equals(request.loadStandardType()) && !"PERCENT".equals(request.loadStandardType()))
            issues.add(new Issue("达标方式", "达标方式不正确"));
        if ("FIXED".equals(request.loadStandardType())) {
            positiveTons(issues, "达标吨数（吨）", request.loadStandardMinLoad());
            if (request.loadStandardPercent() != null) issues.add(new Issue("达标方式", "固定吨数不能同时填写比例"));
            if (request.maxLoad() != null && request.loadStandardMinLoad() != null
                    && request.loadStandardMinLoad().compareTo(request.maxLoad()) > 0)
                issues.add(new Issue("达标吨数（吨）", "达标吨数不得超过最大载重"));
        } else if ("PERCENT".equals(request.loadStandardType())) {
            if (request.loadStandardPercent() == null || request.loadStandardPercent().signum() <= 0
                    || request.loadStandardPercent().compareTo(BigDecimal.ONE) > 0)
                issues.add(new Issue("达标比例", "达标比例须大于 0 且不超过 1"));
            if (request.loadStandardMinLoad() != null) issues.add(new Issue("达标方式", "比例达标不能同时填写固定吨数"));
        }
        if (request.status() == null || request.status() != 0 && request.status() != 1)
            issues.add(new Issue("状态", "状态不正确"));
        if (existing != null) {
            if (existing.plates().contains(request.plateNo())) issues.add(new Issue("车牌号", "车牌号已存在"));
            if (existing.vehicleLicenses().contains(request.vehicleLicenseNo() == null ? null : request.vehicleLicenseNo().trim()))
                issues.add(new Issue("行车证号", "行驶证号已被其他车辆使用"));
        }
        return issues;
    }

    private void positiveTons(List<Issue> issues, String field, BigDecimal value) {
        if (value == null || value.signum() <= 0) issues.add(new Issue(field, "必须大于 0"));
        else if (value.scale() > 3 || value.precision() - value.scale() > 15)
            issues.add(new Issue(field, "最多 3 位小数且不得超出数据库精度"));
    }
    private boolean text(String value) { return value != null && !value.isBlank(); }
    private void required(List<Issue> issues, String field, String value, int max) {
        if (!text(value)) issues.add(new Issue(field, "必填"));
        else if (value.length() > max) issues.add(new Issue(field, "最多 " + max + " 个字符"));
    }
}
