package com.dfwl.fleet.master.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.master.dto.response.BindingHistoryResponse;
import com.dfwl.fleet.master.dto.request.CustomerRequest;
import com.dfwl.fleet.master.dto.response.CustomerResponse;
import com.dfwl.fleet.master.dto.request.DriverRequest;
import com.dfwl.fleet.master.dto.response.DriverResponse;
import com.dfwl.fleet.master.dto.request.ProductRequest;
import com.dfwl.fleet.master.dto.response.ProductResponse;
import com.dfwl.fleet.master.dto.request.TrailerRequest;
import com.dfwl.fleet.master.dto.response.TrailerResponse;
import com.dfwl.fleet.master.dto.request.VehicleRequest;
import com.dfwl.fleet.master.dto.response.VehicleResponse;
import java.sql.Date;
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

    public PageResponse<CustomerResponse> listCustomers(int pageNo, int pageSize) {
        long total = count("customer", "1 = 1");
        List<CustomerResponse> records = jdbcTemplate.query("""
                SELECT id, customer_code, customer_name, status, remark
                FROM customer
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new CustomerResponse(
                rs.getLong("id"),
                rs.getString("customer_code"),
                rs.getString("customer_name"),
                rs.getInt("status"),
                rs.getString("remark")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public Optional<CustomerResponse> findCustomer(long id) {
        return jdbcTemplate.query("""
                SELECT id, customer_code, customer_name, status, remark
                FROM customer WHERE id = ?
                """, (rs, rowNum) -> new CustomerResponse(
                rs.getLong("id"),
                rs.getString("customer_code"),
                rs.getString("customer_name"),
                rs.getInt("status"),
                rs.getString("remark")), id).stream().findFirst();
    }

    public long createCustomer(CustomerRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO customer (customer_code, customer_name, status, remark)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.customerCode());
            ps.setString(2, request.customerName());
            ps.setInt(3, request.status());
            ps.setString(4, request.remark());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateCustomer(long id, CustomerRequest request) {
        return jdbcTemplate.update("""
                UPDATE customer SET customer_code = ?, customer_name = ?, status = ?, remark = ? WHERE id = ?
                """, request.customerCode(), request.customerName(), request.status(), request.remark(), id) == 1;
    }

    public boolean updateCustomerStatus(long id, int status) {
        return jdbcTemplate.update("UPDATE customer SET status = ? WHERE id = ?", status, id) == 1;
    }

    public PageResponse<ProductResponse> listProducts(int pageNo, int pageSize) {
        long total = count("product", "1 = 1");
        List<ProductResponse> records = jdbcTemplate.query("""
                SELECT id, product_code, product_name, status, remark
                FROM product
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ProductResponse(
                rs.getLong("id"),
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getInt("status"),
                rs.getString("remark")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public Optional<ProductResponse> findProduct(long id) {
        return jdbcTemplate.query("""
                SELECT id, product_code, product_name, status, remark
                FROM product WHERE id = ?
                """, (rs, rowNum) -> new ProductResponse(
                rs.getLong("id"),
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getInt("status"),
                rs.getString("remark")), id).stream().findFirst();
    }

    public long createProduct(ProductRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO product (product_code, product_name, status, remark)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.productCode());
            ps.setString(2, request.productName());
            ps.setInt(3, request.status());
            ps.setString(4, request.remark());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateProduct(long id, ProductRequest request) {
        return jdbcTemplate.update("""
                UPDATE product SET product_code = ?, product_name = ?, status = ?, remark = ? WHERE id = ?
                """, request.productCode(), request.productName(), request.status(), request.remark(), id) == 1;
    }

    public boolean updateProductStatus(long id, int status) {
        return jdbcTemplate.update("UPDATE product SET status = ? WHERE id = ?", status, id) == 1;
    }

    public PageResponse<DriverResponse> listDrivers(int pageNo, int pageSize) {
        long total = count("driver", "deleted_at IS NULL");
        List<DriverResponse> records = jdbcTemplate.query("""
                SELECT d.*, dvc.vehicle_id AS current_vehicle_id
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                WHERE d.deleted_at IS NULL
                ORDER BY d.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new DriverResponse(
                rs.getLong("id"),
                readLong(rs, "user_id"),
                rs.getString("driver_type"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("vehicle_license_no"),
                rs.getString("driving_license_no"),
                rs.getString("id_card_no"),
                rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate(),
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public Optional<DriverResponse> findDriver(long id) {
        return jdbcTemplate.query("""
                SELECT d.*, dvc.vehicle_id AS current_vehicle_id
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                WHERE d.id = ? AND d.deleted_at IS NULL
                """, (rs, rowNum) -> new DriverResponse(
                rs.getLong("id"),
                readLong(rs, "user_id"),
                rs.getString("driver_type"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("vehicle_license_no"),
                rs.getString("driving_license_no"),
                rs.getString("id_card_no"),
                rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate(),
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id")), id).stream().findFirst();
    }

    public Optional<DriverResponse> findDriverByUserIdOrPhone(long userId, String phone) {
        return jdbcTemplate.query("""
                SELECT d.*, dvc.vehicle_id AS current_vehicle_id
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                WHERE d.deleted_at IS NULL AND (d.user_id = ? OR d.phone = ?)
                ORDER BY CASE WHEN d.user_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """, (rs, rowNum) -> new DriverResponse(
                rs.getLong("id"),
                readLong(rs, "user_id"),
                rs.getString("driver_type"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("vehicle_license_no"),
                rs.getString("driving_license_no"),
                rs.getString("id_card_no"),
                rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate(),
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id")), userId, phone, userId).stream().findFirst();
    }

    public long createDriver(DriverRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO driver (driver_type, name, phone, vehicle_license_no, driving_license_no, id_card_no, birth_date, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.driverType());
            ps.setString(2, request.name());
            ps.setString(3, request.phone());
            ps.setString(4, request.vehicleLicenseNo());
            ps.setString(5, request.drivingLicenseNo());
            ps.setString(6, request.idCardNo());
            ps.setDate(7, request.birthDate() == null ? null : Date.valueOf(request.birthDate()));
            ps.setInt(8, request.status());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateDriver(long id, DriverRequest request) {
        return jdbcTemplate.update("""
                UPDATE driver
                SET driver_type = ?, name = ?, phone = ?, vehicle_license_no = ?, driving_license_no = ?,
                    id_card_no = ?, birth_date = ?, status = ?
                WHERE id = ? AND deleted_at IS NULL
                """, request.driverType(), request.name(), request.phone(), request.vehicleLicenseNo(),
                request.drivingLicenseNo(), request.idCardNo(),
                request.birthDate() == null ? null : Date.valueOf(request.birthDate()), request.status(), id) == 1;
    }

    public PageResponse<VehicleResponse> listVehicles(int pageNo, int pageSize) {
        long total = count("vehicle", "deleted_at IS NULL");
        List<VehicleResponse> records = jdbcTemplate.query("""
                SELECT v.*, dvc.driver_id AS current_driver_id, vtc.trailer_id AS current_trailer_id
                FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = v.id
                WHERE v.deleted_at IS NULL
                ORDER BY v.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new VehicleResponse(
                rs.getLong("id"),
                rs.getString("plate_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getString("energy_type"),
                rs.getBigDecimal("max_load"),
                rs.getString("load_standard_type"),
                rs.getBigDecimal("load_standard_percent"),
                rs.getBigDecimal("load_standard_min_load"),
                rs.getInt("status"),
                readLong(rs, "current_driver_id"),
                readLong(rs, "current_trailer_id")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public PageResponse<VehicleResponse> listVehiclesForDriver(long driverId, int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT v.id)
                FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                LEFT JOIN route_task r ON r.departure_vehicle_id = v.id AND r.departure_driver_id = ? AND r.deleted_at IS NULL
                WHERE v.deleted_at IS NULL
                  AND (dvc.driver_id = ? OR r.id IS NOT NULL)
                """, Long.class, driverId, driverId);
        List<VehicleResponse> records = jdbcTemplate.query("""
                SELECT DISTINCT v.*, dvc.driver_id AS current_driver_id, vtc.trailer_id AS current_trailer_id
                FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = v.id
                LEFT JOIN route_task r ON r.departure_vehicle_id = v.id AND r.departure_driver_id = ? AND r.deleted_at IS NULL
                WHERE v.deleted_at IS NULL
                  AND (dvc.driver_id = ? OR r.id IS NOT NULL)
                ORDER BY v.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new VehicleResponse(
                rs.getLong("id"),
                rs.getString("plate_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getString("energy_type"),
                rs.getBigDecimal("max_load"),
                rs.getString("load_standard_type"),
                rs.getBigDecimal("load_standard_percent"),
                rs.getBigDecimal("load_standard_min_load"),
                rs.getInt("status"),
                readLong(rs, "current_driver_id"),
                readLong(rs, "current_trailer_id")), driverId, driverId, pageSize, offset(pageNo, pageSize));
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
                      SELECT 1 FROM route_task r
                      WHERE r.departure_driver_id = ? AND r.departure_vehicle_id = v.id AND r.deleted_at IS NULL
                    )
                  )
                """, Integer.class, vehicleId, driverId, driverId);
        return count != null && count > 0;
    }

    public Optional<VehicleResponse> findVehicle(long id) {
        return jdbcTemplate.query("""
                SELECT v.*, dvc.driver_id AS current_driver_id, vtc.trailer_id AS current_trailer_id
                FROM vehicle v
                LEFT JOIN driver_vehicle_current dvc ON dvc.vehicle_id = v.id
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = v.id
                WHERE v.id = ? AND v.deleted_at IS NULL
                """, (rs, rowNum) -> new VehicleResponse(
                rs.getLong("id"),
                rs.getString("plate_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getString("energy_type"),
                rs.getBigDecimal("max_load"),
                rs.getString("load_standard_type"),
                rs.getBigDecimal("load_standard_percent"),
                rs.getBigDecimal("load_standard_min_load"),
                rs.getInt("status"),
                readLong(rs, "current_driver_id"),
                readLong(rs, "current_trailer_id")), id).stream().findFirst();
    }

    public long createVehicle(VehicleRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO vehicle (plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                         load_standard_percent, load_standard_min_load, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.plateNo());
            ps.setInt(2, Boolean.TRUE.equals(request.insuranceComplete()) ? 1 : 0);
            ps.setString(3, request.energyType());
            ps.setBigDecimal(4, request.maxLoad());
            ps.setString(5, request.loadStandardType());
            ps.setBigDecimal(6, request.loadStandardPercent());
            ps.setBigDecimal(7, request.loadStandardMinLoad());
            ps.setInt(8, request.status());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateVehicle(long id, VehicleRequest request) {
        return jdbcTemplate.update("""
                UPDATE vehicle
                SET plate_no = ?, insurance_complete = ?, energy_type = ?, max_load = ?, load_standard_type = ?,
                    load_standard_percent = ?, load_standard_min_load = ?, status = ?
                WHERE id = ? AND deleted_at IS NULL
                """, request.plateNo(), Boolean.TRUE.equals(request.insuranceComplete()) ? 1 : 0,
                request.energyType(), request.maxLoad(), request.loadStandardType(), request.loadStandardPercent(),
                request.loadStandardMinLoad(), request.status(), id) == 1;
    }

    public boolean deleteVehicle(long id) {
        return jdbcTemplate.update("UPDATE vehicle SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL", id) == 1;
    }

    public PageResponse<TrailerResponse> listTrailers(int pageNo, int pageSize) {
        long total = count("trailer", "deleted_at IS NULL");
        List<TrailerResponse> records = jdbcTemplate.query("""
                SELECT t.*, vtc.vehicle_id AS current_vehicle_id
                FROM trailer t
                LEFT JOIN vehicle_trailer_current vtc ON vtc.trailer_id = t.id
                WHERE t.deleted_at IS NULL
                ORDER BY t.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new TrailerResponse(
                rs.getLong("id"),
                rs.getString("trailer_no"),
                rs.getString("plate_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id")), pageSize, offset(pageNo, pageSize));
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public Optional<TrailerResponse> findTrailer(long id) {
        return jdbcTemplate.query("""
                SELECT t.*, vtc.vehicle_id AS current_vehicle_id
                FROM trailer t
                LEFT JOIN vehicle_trailer_current vtc ON vtc.trailer_id = t.id
                WHERE t.id = ? AND t.deleted_at IS NULL
                """, (rs, rowNum) -> new TrailerResponse(
                rs.getLong("id"),
                rs.getString("trailer_no"),
                rs.getString("plate_no"),
                rs.getInt("insurance_complete") == 1,
                rs.getInt("status"),
                readLong(rs, "current_vehicle_id")), id).stream().findFirst();
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

    public List<BindingHistoryResponse> vehicleBindingHistory(long vehicleId) {
        return jdbcTemplate.query("""
                SELECT id, vehicle_id AS subject_id, driver_id AS target_id, bind_time, unbind_time, bind_by, unbind_by, unbind_reason
                FROM driver_vehicle_history
                WHERE vehicle_id = ?
                ORDER BY bind_time DESC
                """, (rs, rowNum) -> new BindingHistoryResponse(
                rs.getLong("id"),
                rs.getLong("subject_id"),
                rs.getLong("target_id"),
                rs.getTimestamp("bind_time").toLocalDateTime(),
                rs.getTimestamp("unbind_time") == null ? null : rs.getTimestamp("unbind_time").toLocalDateTime(),
                rs.getLong("bind_by"),
                readLong(rs, "unbind_by"),
                rs.getString("unbind_reason")), vehicleId);
    }

    public boolean hasInTransitDriverOrVehicle(long driverId, long vehicleId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM route_task
                WHERE status = 'IN_TRANSIT'
                  AND (assigned_driver_id = ? OR departure_driver_id = ? OR import_vehicle_id = ? OR departure_vehicle_id = ?)
                """, Integer.class, driverId, driverId, vehicleId, vehicleId);
        return count != null && count > 0;
    }

    public boolean hasInTransitVehicleOrTrailer(long vehicleId, long trailerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM route_task
                WHERE status = 'IN_TRANSIT'
                  AND (import_vehicle_id = ? OR departure_vehicle_id = ? OR departure_trailer_id = ?)
                """, Integer.class, vehicleId, vehicleId, trailerId);
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
