package com.dfwl.fleet.route.repository;

import com.dfwl.fleet.route.dto.request.RouteTemplateRequest;
import com.dfwl.fleet.route.dto.response.RouteTemplateResponse;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RouteTemplateRepository {
    private final JdbcTemplate jdbc;

    public RouteTemplateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<RouteTemplateResponse> list() {
        return jdbc.query("SELECT * FROM route_template ORDER BY enabled DESC, id DESC", this::map);
    }

    public Optional<RouteTemplateResponse> find(long id) {
        return jdbc.query("SELECT * FROM route_template WHERE id = ?", this::map, id).stream().findFirst();
    }

    public Optional<RouteTemplateResponse> findEnabledByCode(String code) {
        return jdbc.query("SELECT * FROM route_template WHERE stable_code = ? AND enabled = 1", this::map, code)
                .stream().findFirst();
    }

    public boolean productEnabled(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id = ? AND enabled = 1", Integer.class, id);
        return count != null && count == 1;
    }

    public long create(RouteTemplateRequest request, long actor) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO route_template (stable_code, name, customer_name, product_id, loading_place,
                        unloading_place, default_unit_price, default_information_fee, default_driver_salary,
                        enabled, remark, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.stableCode().trim());
            ps.setString(2, request.name().trim());
            ps.setString(3, request.customerName().trim());
            ps.setLong(4, request.productId());
            ps.setString(5, request.loadingPlace().trim());
            ps.setString(6, request.unloadingPlace().trim());
            ps.setBigDecimal(7, request.defaultUnitPrice());
            ps.setBigDecimal(8, request.defaultInformationFee());
            ps.setBigDecimal(9, request.defaultDriverSalary());
            ps.setString(10, request.remark());
            ps.setLong(11, actor);
            ps.setLong(12, actor);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public boolean update(long id, RouteTemplateRequest request, long actor) {
        return jdbc.update("""
                UPDATE route_template SET stable_code = ?, name = ?, customer_name = ?, product_id = ?,
                    loading_place = ?, unloading_place = ?, default_unit_price = ?, default_information_fee = ?,
                    default_driver_salary = ?, remark = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = ? AND version = ?
                """, request.stableCode().trim(), request.name().trim(), request.customerName().trim(),
                request.productId(), request.loadingPlace().trim(), request.unloadingPlace().trim(),
                request.defaultUnitPrice(), request.defaultInformationFee(), request.defaultDriverSalary(),
                request.remark(), actor, id, request.expectedVersion()) == 1;
    }

    public boolean setEnabled(long id, int version, boolean enabled, long actor) {
        return jdbc.update("""
                UPDATE route_template SET enabled = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1 WHERE id = ? AND version = ?
                """, enabled ? 1 : 0, actor, id, version) == 1;
    }

    private RouteTemplateResponse map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new RouteTemplateResponse(rs.getLong("id"), rs.getString("stable_code"), rs.getString("name"),
                rs.getString("customer_name"), rs.getLong("product_id"), rs.getString("loading_place"),
                rs.getString("unloading_place"), rs.getBigDecimal("default_unit_price"),
                rs.getBigDecimal("default_information_fee"), rs.getBigDecimal("default_driver_salary"),
                rs.getInt("enabled") == 1, rs.getString("remark"), rs.getInt("version"),
                created == null ? null : created.toLocalDateTime(), updated == null ? null : updated.toLocalDateTime());
    }
}
