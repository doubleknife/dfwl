package com.dfwl.fleet.master.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.master.dto.response.BindingHistoryResponse;
import com.dfwl.fleet.master.dto.request.DriverRequest;
import com.dfwl.fleet.master.dto.response.DriverResponse;
import com.dfwl.fleet.master.dto.request.ProductRequest;
import com.dfwl.fleet.master.dto.response.ProductResponse;
import com.dfwl.fleet.master.dto.request.TrailerRequest;
import com.dfwl.fleet.master.dto.response.TrailerResponse;
import com.dfwl.fleet.master.dto.request.VehicleRequest;
import com.dfwl.fleet.master.dto.response.VehicleResponse;
import com.dfwl.fleet.master.validation.MainlandIdCard;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MasterDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public MasterDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<ProductResponse> listProducts(int pageNo, int pageSize) {
        long total = count("product", "1 = 1");
        List<ProductResponse> records = jdbcTemplate.query("""
                SELECT id, name, enabled, version
                FROM product
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ProductResponse(
                rs.getLong("id"),
                rs.getString("name"), rs.getInt("enabled") == 1,
                rs.getInt("version")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public Optional<ProductResponse> findProduct(long id) {
        return jdbcTemplate.query("""
                SELECT id, name, enabled, version
                FROM product WHERE id = ?
                """, (rs, rowNum) -> new ProductResponse(
                rs.getLong("id"),
                rs.getString("name"), rs.getInt("enabled") == 1,
                rs.getInt("version")), id).stream().findFirst();
    }

    public long createProduct(ProductRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO product (name, enabled)
                    VALUES (?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.name().trim());
            ps.setInt(2, request.enabled() ? 1 : 0);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateProduct(long id, ProductRequest request) {
        return jdbcTemplate.update("""
                UPDATE product SET name = ?, enabled = ?, version = version + 1 WHERE id = ? AND version = ?
                """, request.name().trim(), request.enabled() ? 1 : 0, id, request.expectedVersion()) == 1;
    }

    public boolean updateProductStatus(long id, int status) {
        return jdbcTemplate.update("UPDATE product SET enabled = ?, version = version + 1 WHERE id = ?",
                status, id) == 1;
    }

    public PageResponse<DriverResponse> listDrivers(int pageNo, int pageSize, String keyword, String driverType, Integer status) {
        String search = keyword == null ? "" : keyword.trim();
        String type = driverType == null ? "" : driverType;
        int state = status == null ? -1 : status;
        String pattern = "%" + search + "%";
        long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM driver d
                WHERE d.deleted_at IS NULL
                  AND (? = '' OR d.driver_type = ?)
                  AND (? = -1 OR d.status = ?)
                  AND (? = '' OR d.name LIKE ? OR d.phone LIKE ?)
                """, Long.class, type, type, state, state, search, pattern, pattern);
        List<DriverResponse> records = jdbcTemplate.query("""
                SELECT d.*, dvc.vehicle_id AS current_vehicle_id, cv.plate_no AS current_vehicle_plate_no,
                       ct.plate_no AS current_trailer_plate_no
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                LEFT JOIN vehicle cv ON cv.id = dvc.vehicle_id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = cv.id
                LEFT JOIN trailer ct ON ct.id = vtc.trailer_id
                WHERE d.deleted_at IS NULL
                  AND (? = '' OR d.driver_type = ?)
                  AND (? = -1 OR d.status = ?)
                  AND (? = '' OR d.name LIKE ? OR d.phone LIKE ?)
                ORDER BY d.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new DriverResponse(
                rs.getLong("id"),
                readLong(rs, "user_id"),
                rs.getString("driver_type"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("driving_license_no"),
                rs.getString("id_card_no"),
                MainlandIdCard.birthDateOrNull(rs.getString("id_card_no")),
                MainlandIdCard.age(MainlandIdCard.birthDateOrNull(rs.getString("id_card_no"))),
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id"), rs.getString("current_vehicle_plate_no"),
                rs.getString("current_trailer_plate_no")),
                type, type, state, state, search, pattern, pattern, pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public PageResponse<com.dfwl.fleet.master.dto.response.BindingDriverOption> listBindingCandidates(int pageNo, int pageSize) {
        long total = count("driver", "deleted_at IS NULL AND status = 1 AND driver_type = 'INTERNAL'");
        List<com.dfwl.fleet.master.dto.response.BindingDriverOption> records = jdbcTemplate.query("""
                SELECT d.id,d.name,d.phone,d.driver_type,d.status,
                       dvc.vehicle_id AS current_vehicle_id, cv.plate_no AS current_vehicle_plate_no
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                LEFT JOIN vehicle cv ON cv.id = dvc.vehicle_id
                WHERE d.deleted_at IS NULL AND d.status = 1 AND d.driver_type = 'INTERNAL'
                ORDER BY d.id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new com.dfwl.fleet.master.dto.response.BindingDriverOption(
                rs.getLong("id"), rs.getString("name"), rs.getString("phone"), rs.getString("driver_type"),
                rs.getInt("status"), readLong(rs, "current_vehicle_id"),
                rs.getString("current_vehicle_plate_no")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public PageResponse<com.dfwl.fleet.master.dto.response.BindingVehicleOption> listBindingVehicles(int pageNo, int pageSize) {
        return listBindingVehicles(pageNo, pageSize, false);
    }

    public PageResponse<com.dfwl.fleet.master.dto.response.BindingVehicleOption> listBindingVehicles(
            int pageNo, int pageSize, boolean onlyUnboundDriver) {
        int unboundOnly = onlyUnboundDriver ? 1 : 0;
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                WHERE v.deleted_at IS NULL
                  AND (? = 0 OR (v.status = 1 AND dvc.driver_id IS NULL))
                """, Long.class, unboundOnly);
        List<com.dfwl.fleet.master.dto.response.BindingVehicleOption> records = jdbcTemplate.query("""
                SELECT v.id,v.plate_no,v.status,vtc.trailer_id AS current_trailer_id,
                       t.plate_no AS current_trailer_plate_no
                FROM vehicle v
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id=v.id
                LEFT JOIN trailer t ON t.id=vtc.trailer_id
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id=v.id
                WHERE v.deleted_at IS NULL
                  AND (? = 0 OR (v.status = 1 AND dvc.driver_id IS NULL))
                ORDER BY v.id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new com.dfwl.fleet.master.dto.response.BindingVehicleOption(
                rs.getLong("id"), rs.getString("plate_no"), rs.getInt("status"),
                readLong(rs, "current_trailer_id"), rs.getString("current_trailer_plate_no")),
                unboundOnly, pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public PageResponse<com.dfwl.fleet.master.dto.response.BindingTrailerOption> listBindingTrailers(int pageNo, int pageSize) {
        return listBindingTrailers(pageNo, pageSize, false);
    }

    public PageResponse<com.dfwl.fleet.master.dto.response.BindingTrailerOption> listBindingTrailers(
            int pageNo, int pageSize, boolean onlyUnboundVehicle) {
        int unboundOnly = onlyUnboundVehicle ? 1 : 0;
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM trailer t
                LEFT JOIN vehicle_trailer_current vtc ON vtc.trailer_id = t.id
                WHERE t.deleted_at IS NULL
                  AND (? = 0 OR (t.status = 1 AND vtc.vehicle_id IS NULL))
                """, Long.class, unboundOnly);
        List<com.dfwl.fleet.master.dto.response.BindingTrailerOption> records = jdbcTemplate.query("""
                SELECT t.id,t.plate_no,t.trailer_no,t.status,vtc.vehicle_id AS current_vehicle_id,
                       v.plate_no AS current_vehicle_plate_no
                FROM trailer t
                LEFT JOIN vehicle_trailer_current vtc ON vtc.trailer_id=t.id
                LEFT JOIN vehicle v ON v.id=vtc.vehicle_id
                WHERE t.deleted_at IS NULL
                  AND (? = 0 OR (t.status = 1 AND vtc.vehicle_id IS NULL))
                ORDER BY t.id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new com.dfwl.fleet.master.dto.response.BindingTrailerOption(
                rs.getLong("id"), rs.getString("plate_no"), rs.getString("trailer_no"), rs.getInt("status"),
                readLong(rs, "current_vehicle_id"), rs.getString("current_vehicle_plate_no")),
                unboundOnly, pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public Optional<DriverResponse> findDriver(long id) {
        return jdbcTemplate.query("""
                SELECT d.*, dvc.vehicle_id AS current_vehicle_id, cv.plate_no AS current_vehicle_plate_no,
                       ct.plate_no AS current_trailer_plate_no
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                LEFT JOIN vehicle cv ON cv.id = dvc.vehicle_id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = cv.id
                LEFT JOIN trailer ct ON ct.id = vtc.trailer_id
                WHERE d.id = ? AND d.deleted_at IS NULL
                """, (rs, rowNum) -> new DriverResponse(
                rs.getLong("id"),
                readLong(rs, "user_id"),
                rs.getString("driver_type"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("driving_license_no"),
                rs.getString("id_card_no"),
                MainlandIdCard.birthDateOrNull(rs.getString("id_card_no")),
                MainlandIdCard.age(MainlandIdCard.birthDateOrNull(rs.getString("id_card_no"))),
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id"), rs.getString("current_vehicle_plate_no"),
                rs.getString("current_trailer_plate_no")), id).stream().findFirst();
    }

    public Optional<DriverResponse> findDriverByUserIdOrPhone(long userId, String phone) {
        return jdbcTemplate.query("""
                SELECT d.*, dvc.vehicle_id AS current_vehicle_id, cv.plate_no AS current_vehicle_plate_no,
                       ct.plate_no AS current_trailer_plate_no
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                LEFT JOIN vehicle cv ON cv.id = dvc.vehicle_id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = cv.id
                LEFT JOIN trailer ct ON ct.id = vtc.trailer_id
                WHERE d.deleted_at IS NULL AND (d.user_id = ? OR d.phone = ?)
                ORDER BY CASE WHEN d.user_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """, (rs, rowNum) -> new DriverResponse(
                rs.getLong("id"),
                readLong(rs, "user_id"),
                rs.getString("driver_type"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("driving_license_no"),
                rs.getString("id_card_no"),
                MainlandIdCard.birthDateOrNull(rs.getString("id_card_no")),
                MainlandIdCard.age(MainlandIdCard.birthDateOrNull(rs.getString("id_card_no"))),
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id"), rs.getString("current_vehicle_plate_no"),
                rs.getString("current_trailer_plate_no")), userId, phone, userId).stream().findFirst();
    }

    public long createDriver(DriverRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO driver (driver_type, name, phone, driving_license_no, id_card_no, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.driverType());
            ps.setString(2, request.name());
            ps.setString(3, request.phone());
            ps.setString(4, request.drivingLicenseNo().trim());
            ps.setString(5, request.idCardNo().trim().toUpperCase(java.util.Locale.ROOT));
            ps.setInt(6, request.status());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateDriver(long id, DriverRequest request) {
        return jdbcTemplate.update("""
                UPDATE driver
                SET driver_type = ?, name = ?, phone = ?, driving_license_no = ?,
                    id_card_no = ?, status = ?
                WHERE id = ? AND deleted_at IS NULL
                """, request.driverType(), request.name(), request.phone(),
                request.drivingLicenseNo().trim(), request.idCardNo().trim().toUpperCase(java.util.Locale.ROOT),
                request.status(), id) == 1;
    }

    public boolean hasDriverTaskHistory(long driverId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transport_participant WHERE driver_id = ?", Integer.class, driverId);
        return count != null && count > 0;
    }

    public boolean driverCertificateExists(String column, String value, long exceptId) {
        if (!"id_card_no".equals(column) && !"driving_license_no".equals(column)) {
            throw new IllegalArgumentException("Unsupported certificate column");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver WHERE " + column + " = ? AND id <> ?",
                Integer.class, value, exceptId);
        return count != null && count > 0;
    }

    public boolean vehicleLicenseExists(String value, long exceptId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle WHERE vehicle_license_no = ? AND id <> ?",
                Integer.class, value, exceptId);
        return count != null && count > 0;
    }

    public void lockDriver(long driverId) {
        jdbcTemplate.queryForList("SELECT id FROM driver WHERE id = ? AND deleted_at IS NULL FOR UPDATE", Long.class, driverId);
    }

    public void lockVehicle(long vehicleId) {
        jdbcTemplate.queryForList("SELECT id FROM vehicle WHERE id = ? AND deleted_at IS NULL FOR UPDATE", Long.class, vehicleId);
    }

    public void lockTrailer(long trailerId) {
        jdbcTemplate.queryForList("SELECT id FROM trailer WHERE id = ? AND deleted_at IS NULL FOR UPDATE", Long.class, trailerId);
    }

    public int unfinishedTasksForVehicle(long vehicleId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT t.id) FROM transport_task t
                JOIN transport_participant p ON p.id = t.current_participant_id
                WHERE p.company_vehicle_id = ? AND p.is_current = 1
                  AND t.deleted_at IS NULL
                  AND t.status IN ('PUBLISHED','IN_TRANSIT','TRANSFER_PENDING')
                """, Integer.class, vehicleId);
        return count == null ? 0 : count;
    }

    public int unfinishedTasksForTrailer(long trailerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT t.id) FROM transport_task t
                JOIN transport_participant p ON p.id = t.current_participant_id
                WHERE p.trailer_id = ? AND p.is_current = 1
                  AND t.deleted_at IS NULL
                  AND t.status IN ('PUBLISHED','IN_TRANSIT','TRANSFER_PENDING')
                """, Integer.class, trailerId);
        return count == null ? 0 : count;
    }

    public int unfinishedCurrentTasks(long driverId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT t.id) FROM transport_task t
                JOIN transport_participant p ON p.id=t.current_participant_id
                WHERE p.driver_id=? AND p.is_current=1
                  AND t.status IN ('PUBLISHED','IN_TRANSIT','TRANSFER_PENDING')
                """, Integer.class, driverId);
        return count == null ? 0 : count;
    }

    public int activeDriversWithPhone(String phone) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver WHERE phone = ? AND deleted_at IS NULL", Integer.class, phone);
        return count == null ? 0 : count;
    }

    public boolean linkDriverUser(long driverId, long userId) {
        return jdbcTemplate.update("UPDATE driver SET user_id = ? WHERE id = ? AND user_id IS NULL AND deleted_at IS NULL",
                userId, driverId) == 1;
    }

    public PageResponse<VehicleResponse> listVehicles(int pageNo, int pageSize) {
        long total = count("vehicle", "deleted_at IS NULL");
        List<VehicleResponse> records = jdbcTemplate.query("""
                SELECT v.*, dvc.driver_id AS current_driver_id, vtc.trailer_id AS current_trailer_id,
                       ct.plate_no AS current_trailer_plate_no, cd.name AS current_driver_name
                FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = v.id
                LEFT JOIN trailer ct ON ct.id = vtc.trailer_id
                LEFT JOIN driver cd ON cd.id = dvc.driver_id
                WHERE v.deleted_at IS NULL
                ORDER BY v.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new VehicleResponse(
                rs.getLong("id"),
                rs.getString("plate_no"),
                rs.getString("vehicle_license_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getString("energy_type"),
                rs.getBigDecimal("max_load"),
                rs.getString("load_standard_type"),
                rs.getBigDecimal("load_standard_percent"),
                rs.getBigDecimal("load_standard_min_load"),
                rs.getInt("status"),
                readLong(rs, "current_driver_id"),
                readLong(rs, "current_trailer_id"), rs.getString("current_trailer_plate_no"), rs.getString("current_driver_name")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public PageResponse<VehicleResponse> listVehiclesForDriver(long driverId, int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT v.id)
                FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                LEFT JOIN transport_participant p ON p.company_vehicle_id = v.id
                    AND p.driver_id = ? AND p.status <> 'CANCELLED'
                WHERE v.deleted_at IS NULL
                  AND (dvc.driver_id = ? OR p.id IS NOT NULL)
                """, Long.class, driverId, driverId);
        List<VehicleResponse> records = jdbcTemplate.query("""
                SELECT DISTINCT v.*, dvc.driver_id AS current_driver_id, vtc.trailer_id AS current_trailer_id,
                       ct.plate_no AS current_trailer_plate_no, cd.name AS current_driver_name
                FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = v.id
                LEFT JOIN trailer ct ON ct.id = vtc.trailer_id
                LEFT JOIN driver cd ON cd.id = dvc.driver_id
                LEFT JOIN transport_participant p ON p.company_vehicle_id = v.id
                    AND p.driver_id = ? AND p.status <> 'CANCELLED'
                WHERE v.deleted_at IS NULL
                  AND (dvc.driver_id = ? OR p.id IS NOT NULL)
                ORDER BY v.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new VehicleResponse(
                rs.getLong("id"),
                rs.getString("plate_no"),
                rs.getString("vehicle_license_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getString("energy_type"),
                rs.getBigDecimal("max_load"),
                rs.getString("load_standard_type"),
                rs.getBigDecimal("load_standard_percent"),
                rs.getBigDecimal("load_standard_min_load"),
                rs.getInt("status"),
                readLong(rs, "current_driver_id"),
                readLong(rs, "current_trailer_id"), rs.getString("current_trailer_plate_no"), rs.getString("current_driver_name")), driverId, driverId, pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public boolean canDriverViewVehicle(long vehicleId, long driverId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM vehicle v
                WHERE v.id = ? AND v.deleted_at IS NULL
                  AND (
                    EXISTS (
                      SELECT 1 FROM driver_vehicle_current dvc
                      WHERE dvc.driver_id = ? AND dvc.vehicle_id = v.id
                    )
                    OR EXISTS (
                      SELECT 1 FROM transport_participant p
                      WHERE p.driver_id = ? AND p.company_vehicle_id = v.id
                        AND p.status <> 'CANCELLED'
                    )
                  )
                """, Integer.class, vehicleId, driverId, driverId);
        return count != null && count > 0;
    }

    public Optional<VehicleResponse> findVehicle(long id) {
        return jdbcTemplate.query("""
                SELECT v.*, dvc.driver_id AS current_driver_id, vtc.trailer_id AS current_trailer_id,
                       ct.plate_no AS current_trailer_plate_no, cd.name AS current_driver_name
                FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = v.id
                LEFT JOIN trailer ct ON ct.id = vtc.trailer_id
                LEFT JOIN driver cd ON cd.id = dvc.driver_id
                WHERE v.id = ? AND v.deleted_at IS NULL
                """, (rs, rowNum) -> new VehicleResponse(
                rs.getLong("id"),
                rs.getString("plate_no"),
                rs.getString("vehicle_license_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getString("energy_type"),
                rs.getBigDecimal("max_load"),
                rs.getString("load_standard_type"),
                rs.getBigDecimal("load_standard_percent"),
                rs.getBigDecimal("load_standard_min_load"),
                rs.getInt("status"),
                readLong(rs, "current_driver_id"),
                readLong(rs, "current_trailer_id"), rs.getString("current_trailer_plate_no"), rs.getString("current_driver_name")), id).stream().findFirst();
    }

    public long createVehicle(VehicleRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO vehicle (plate_no, vehicle_license_no, insurance_complete, energy_type, max_load, load_standard_type,
                                         load_standard_percent, load_standard_min_load, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.plateNo());
            ps.setString(2, request.vehicleLicenseNo().trim());
            ps.setInt(3, Boolean.TRUE.equals(request.insuranceComplete()) ? 1 : 0);
            ps.setString(4, request.energyType());
            ps.setBigDecimal(5, request.maxLoad());
            ps.setString(6, request.loadStandardType());
            ps.setBigDecimal(7, request.loadStandardPercent());
            ps.setBigDecimal(8, request.loadStandardMinLoad());
            ps.setInt(9, request.status());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateVehicle(long id, VehicleRequest request) {
        return jdbcTemplate.update("""
                UPDATE vehicle
                SET plate_no = ?, vehicle_license_no = ?, insurance_complete = ?, energy_type = ?, max_load = ?, load_standard_type = ?,
                    load_standard_percent = ?, load_standard_min_load = ?, status = ?
                WHERE id = ? AND deleted_at IS NULL
                """, request.plateNo(), request.vehicleLicenseNo().trim(), Boolean.TRUE.equals(request.insuranceComplete()) ? 1 : 0,
                request.energyType(), request.maxLoad(), request.loadStandardType(), request.loadStandardPercent(),
                request.loadStandardMinLoad(), request.status(), id) == 1;
    }

    public PageResponse<TrailerResponse> listTrailers(int pageNo, int pageSize) {
        long total = count("trailer", "deleted_at IS NULL");
        List<TrailerResponse> records = jdbcTemplate.query("""
                SELECT t.*, vtc.vehicle_id AS current_vehicle_id, cv.plate_no AS current_vehicle_plate_no,
                       cd.name AS current_driver_name
                FROM trailer t
                LEFT JOIN vehicle_trailer_current vtc ON vtc.trailer_id = t.id
                LEFT JOIN vehicle cv ON cv.id = vtc.vehicle_id
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = cv.id
                LEFT JOIN driver cd ON cd.id = dvc.driver_id
                WHERE t.deleted_at IS NULL
                ORDER BY t.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new TrailerResponse(
                rs.getLong("id"),
                rs.getString("trailer_no"),
                rs.getString("plate_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id"), rs.getString("current_vehicle_plate_no"),
                rs.getString("current_driver_name")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public Optional<TrailerResponse> findTrailer(long id) {
        return jdbcTemplate.query("""
                SELECT t.*, vtc.vehicle_id AS current_vehicle_id, cv.plate_no AS current_vehicle_plate_no,
                       cd.name AS current_driver_name
                FROM trailer t
                LEFT JOIN vehicle_trailer_current vtc ON vtc.trailer_id = t.id
                LEFT JOIN vehicle cv ON cv.id = vtc.vehicle_id
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = cv.id
                LEFT JOIN driver cd ON cd.id = dvc.driver_id
                WHERE t.id = ? AND t.deleted_at IS NULL
                """, (rs, rowNum) -> new TrailerResponse(
                rs.getLong("id"),
                rs.getString("trailer_no"),
                rs.getString("plate_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id"), rs.getString("current_vehicle_plate_no"),
                rs.getString("current_driver_name")), id).stream().findFirst();
    }

    public long createTrailer(TrailerRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO trailer (trailer_no, plate_no, insurance_complete, status)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.trailerNo());
            ps.setString(2, request.plateNo());
            ps.setInt(3, Boolean.TRUE.equals(request.insuranceComplete()) ? 1 : 0);
            ps.setInt(4, request.status());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateTrailer(long id, TrailerRequest request) {
        return jdbcTemplate.update("""
                UPDATE trailer
                SET trailer_no = ?, plate_no = ?, insurance_complete = ?, status = ?
                WHERE id = ? AND deleted_at IS NULL
                """, request.trailerNo(), request.plateNo(),
                Boolean.TRUE.equals(request.insuranceComplete()) ? 1 : 0, request.status(), id) == 1;
    }

    public boolean existsDriverVehicleBinding(long driverId, long vehicleId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM driver_vehicle_current
                WHERE driver_id = ? OR vehicle_id = ?
                """, Integer.class, driverId, vehicleId);
        return count != null && count > 0;
    }

    public boolean existsVehicleTrailerBinding(long vehicleId, long trailerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM vehicle_trailer_current
                WHERE vehicle_id = ? OR trailer_id = ?
                """, Integer.class, vehicleId, trailerId);
        return count != null && count > 0;
    }

    public void bindDriverVehicle(long driverId, long vehicleId, long operatorId, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by)
                VALUES (?, ?, ?, ?)
                """, driverId, vehicleId, Timestamp.valueOf(now), operatorId);
        jdbcTemplate.update("""
                INSERT INTO driver_vehicle_history (driver_id, vehicle_id, bind_time, bind_by)
                VALUES (?, ?, ?, ?)
                """, driverId, vehicleId, Timestamp.valueOf(now), operatorId);
    }

    public boolean unbindDriverVehicle(long driverId, long operatorId, String reason, LocalDateTime now) {
        List<Long> vehicleIds = jdbcTemplate.queryForList("""
                SELECT vehicle_id FROM driver_vehicle_current WHERE driver_id = ?
                """, Long.class, driverId);
        if (vehicleIds.isEmpty()) {
            return false;
        }
        long vehicleId = vehicleIds.get(0);
        jdbcTemplate.update("DELETE FROM driver_vehicle_current WHERE driver_id = ?", driverId);
        jdbcTemplate.update("""
                UPDATE driver_vehicle_history
                SET unbind_time = ?, unbind_by = ?, unbind_reason = ?
                WHERE driver_id = ? AND vehicle_id = ? AND unbind_time IS NULL
                """, Timestamp.valueOf(now), operatorId, reason, driverId, vehicleId);
        return true;
    }

    public boolean unbindDriverVehicleFromVehicle(long driverId, long vehicleId, long operatorId,
                                                  String reason, LocalDateTime now) {
        int deleted = jdbcTemplate.update("DELETE FROM driver_vehicle_current WHERE driver_id = ? AND vehicle_id = ?",
                driverId, vehicleId);
        if (deleted == 0) return false;
        jdbcTemplate.update("""
                UPDATE driver_vehicle_history SET unbind_time = ?, unbind_by = ?, unbind_reason = ?
                WHERE driver_id = ? AND vehicle_id = ? AND unbind_time IS NULL
                """, Timestamp.valueOf(now), operatorId, reason, driverId, vehicleId);
        return true;
    }

    public void bindVehicleTrailer(long vehicleId, long trailerId, long operatorId, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO vehicle_trailer_current (vehicle_id, trailer_id, bound_at, bound_by)
                VALUES (?, ?, ?, ?)
                """, vehicleId, trailerId, Timestamp.valueOf(now), operatorId);
        jdbcTemplate.update("""
                INSERT INTO vehicle_trailer_history (vehicle_id, trailer_id, bind_time, bind_by)
                VALUES (?, ?, ?, ?)
                """, vehicleId, trailerId, Timestamp.valueOf(now), operatorId);
    }

    public boolean unbindVehicleTrailer(long trailerId, long operatorId, String reason, LocalDateTime now) {
        List<Long> vehicleIds = jdbcTemplate.queryForList("""
                SELECT vehicle_id FROM vehicle_trailer_current WHERE trailer_id = ?
                """, Long.class, trailerId);
        if (vehicleIds.isEmpty()) {
            return false;
        }
        long vehicleId = vehicleIds.get(0);
        jdbcTemplate.update("DELETE FROM vehicle_trailer_current WHERE trailer_id = ?", trailerId);
        jdbcTemplate.update("""
                UPDATE vehicle_trailer_history
                SET unbind_time = ?, unbind_by = ?, unbind_reason = ?
                WHERE vehicle_id = ? AND trailer_id = ? AND unbind_time IS NULL
                """, Timestamp.valueOf(now), operatorId, reason, vehicleId, trailerId);
        return true;
    }

    public boolean unbindVehicleTrailerFromVehicle(long vehicleId, long trailerId, long operatorId,
                                                   String reason, LocalDateTime now) {
        int deleted = jdbcTemplate.update("DELETE FROM vehicle_trailer_current WHERE vehicle_id = ? AND trailer_id = ?",
                vehicleId, trailerId);
        if (deleted == 0) return false;
        jdbcTemplate.update("""
                UPDATE vehicle_trailer_history SET unbind_time = ?, unbind_by = ?, unbind_reason = ?
                WHERE vehicle_id = ? AND trailer_id = ? AND unbind_time IS NULL
                """, Timestamp.valueOf(now), operatorId, reason, vehicleId, trailerId);
        return true;
    }

    public List<BindingHistoryResponse> vehicleBindingHistory(long vehicleId) {
        return driverVehicleHistory("h.vehicle_id", vehicleId);
    }

    public List<BindingHistoryResponse> driverBindingHistory(long driverId) {
        return driverVehicleHistory("h.driver_id", driverId);
    }

    private List<BindingHistoryResponse> driverVehicleHistory(String column, long id) {
        return jdbcTemplate.query("""
                SELECT h.id, h.vehicle_id AS subject_id, h.driver_id AS target_id,
                       h.bind_time, h.unbind_time, h.bind_by, h.unbind_by, h.unbind_reason,
                       b.phone AS bind_by_phone, u.phone AS unbind_by_phone,
                       v.plate_no AS subject_name, d.name AS target_name
                FROM driver_vehicle_history h
                LEFT JOIN sys_user b ON b.id = h.bind_by
                LEFT JOIN sys_user u ON u.id = h.unbind_by
                LEFT JOIN vehicle v ON v.id = h.vehicle_id
                LEFT JOIN driver d ON d.id = h.driver_id
                WHERE 1 = 1
                """ + "AND " + column + " = ? ORDER BY h.bind_time DESC", this::mapBindingHistory, id);
    }

    public List<BindingHistoryResponse> trailerBindingHistory(long vehicleId) {
        return jdbcTemplate.query("""
                SELECT h.id, h.vehicle_id AS subject_id, h.trailer_id AS target_id,
                       h.bind_time, h.unbind_time, h.bind_by, h.unbind_by, h.unbind_reason,
                       b.phone AS bind_by_phone, u.phone AS unbind_by_phone,
                       v.plate_no AS subject_name, t.plate_no AS target_name
                FROM vehicle_trailer_history h
                LEFT JOIN sys_user b ON b.id = h.bind_by
                LEFT JOIN sys_user u ON u.id = h.unbind_by
                LEFT JOIN vehicle v ON v.id = h.vehicle_id
                LEFT JOIN trailer t ON t.id = h.trailer_id
                WHERE h.vehicle_id = ? ORDER BY h.bind_time DESC
                """, this::mapBindingHistory, vehicleId);
    }

    private BindingHistoryResponse mapBindingHistory(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BindingHistoryResponse(
                rs.getLong("id"), rs.getLong("subject_id"), rs.getLong("target_id"),
                rs.getTimestamp("bind_time").toLocalDateTime(),
                rs.getTimestamp("unbind_time") == null ? null : rs.getTimestamp("unbind_time").toLocalDateTime(),
                rs.getLong("bind_by"), readLong(rs, "unbind_by"), rs.getString("unbind_reason"),
                rs.getString("bind_by_phone"), rs.getString("unbind_by_phone"),
                rs.getString("subject_name"), rs.getString("target_name"));
    }

    public boolean hasInTransitDriverOrVehicle(long driverId, long vehicleId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM transport_task t JOIN transport_participant p ON p.task_id = t.id
                WHERE t.status IN ('IN_TRANSIT', 'TRANSFER_PENDING') AND t.deleted_at IS NULL
                  AND p.status <> 'CANCELLED'
                  AND (p.driver_id = ? OR p.company_vehicle_id = ?)
                """, Integer.class, driverId, vehicleId);
        return count != null && count > 0;
    }

    public boolean hasInTransitVehicleOrTrailer(long vehicleId, long trailerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM transport_task t JOIN transport_participant p ON p.task_id = t.id
                WHERE t.status IN ('IN_TRANSIT', 'TRANSFER_PENDING') AND t.deleted_at IS NULL
                  AND p.status <> 'CANCELLED'
                  AND (p.company_vehicle_id = ? OR p.trailer_id = ?)
                """, Integer.class, vehicleId, trailerId);
        return count != null && count > 0;
    }

    private long count(String table, String where) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
        return count == null ? 0 : count;
    }

    private int offset(int pageNo, int pageSize) {
        return Math.max(pageNo - 1, 0) * pageSize;
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
