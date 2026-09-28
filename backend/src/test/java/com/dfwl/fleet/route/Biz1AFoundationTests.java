package com.dfwl.fleet.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.master.dto.request.DriverRequest;
import com.dfwl.fleet.master.service.DriverAccountService;
import com.dfwl.fleet.master.service.MasterDataService;
import com.dfwl.fleet.route.dto.request.RouteReasonRequest;
import com.dfwl.fleet.route.dto.request.RouteRequest;
import com.dfwl.fleet.route.dto.request.UnloadRequest;
import com.dfwl.fleet.route.repository.RouteParticipantRepository;
import com.dfwl.fleet.route.service.RouteService;
import com.dfwl.fleet.system.dto.request.RolePermissionRequest;
import com.dfwl.fleet.system.dto.request.UserRequest;
import com.dfwl.fleet.system.service.AuditWriteService;
import com.dfwl.fleet.system.service.SystemManagementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:biz1adb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class Biz1AFoundationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MasterDataService masters;
    @Autowired DriverAccountService accounts;
    @Autowired SystemManagementService system;
    @Autowired RouteService routes;
    @Autowired RouteParticipantRepository participants;
    @Autowired AuditWriteService audit;

    @BeforeEach
    void setup() {
        for (String table : List.of("audit_log", "route_participant_salary_history", "route_participant",
                "route_weight_version", "route_status_history", "route_task", "driver_salary_history",
                "driver_salary_entry", "driver_vehicle_history", "driver_vehicle_current", "vehicle",
                "driver", "product", "customer", "sys_role_permission", "sys_permission", "sys_user", "sys_role")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (1,'ADMIN','管理员',1),(2,'DRIVER','司机',1)");
        jdbc.update("INSERT INTO customer (id, customer_name, status) VALUES (1,'客户',1)");
        jdbc.update("INSERT INTO product (id, product_name, status) VALUES (1,'产品',1)");
        jdbc.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load,
                                     load_standard_type, load_standard_percent, status)
                VALUES (1,'测试A001',1,'ELECTRIC',30,'PERCENT',0.9000,1)
                """);
        jdbc.update("INSERT INTO sys_permission (id, permission_code, permission_name, permission_type) VALUES "
                + "(1,'route:transfer','线路转派','API'),(2,'route:salary:adjust','线路工资调整','API')");
    }

    @Test
    void driverAccountIsAtomicAndUserManagementCannotAssignDriverRole() {
        assertThatThrownBy(() -> system.createUser(new UserRequest("13911110001", "test", 2L, 1), 1))
                .isInstanceOf(BusinessException.class);
        long driver = masters.createDriver(driver("INTERNAL", "13911110001")).id();
        assertThatThrownBy(() -> accounts.createOrLink(driver, null, 1)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isZero();
        long user = accounts.createOrLink(driver, "local-test-password", 1);
        assertThat(accounts.createOrLink(driver, null, 1)).isEqualTo(user);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT user_id FROM driver WHERE id = ?", Long.class, driver)).isEqualTo(user);
        assertThatThrownBy(() -> system.updateUser(user,
                new UserRequest("13911110001", null, 1L, 1), 1)).isInstanceOf(BusinessException.class);
    }

    @Test
    void outsourcedBindingIsRejectedAndCandidateListContainsOnlyInternal() {
        long internal = masters.createDriver(driver("INTERNAL", "13911110002")).id();
        long outsourced = masters.createDriver(driver("OUTSOURCED", "13911110003")).id();
        assertThat(masters.listBindingCandidates(1, 20).records()).extracting("id").containsExactly(internal);
        assertThatThrownBy(() -> masters.bindDriverVehicle(outsourced, 1, 1))
                .isInstanceOf(BusinessException.class);
        masters.bindDriverVehicle(internal, 1, 1);
        assertThat(masters.findDriver(internal).currentVehicleId()).isEqualTo(1);
        assertThatThrownBy(() -> masters.updateDriver(internal, driver("OUTSOURCED", "13911110002"), 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void participantLifecycleAndCompletedOnlyVoidPreserveHistory() {
        long driver = masters.createDriver(driver("INTERNAL", "13911110004")).id();
        masters.bindDriverVehicle(driver, 1, 1);
        long route = routes.create(new RouteRequest(LocalDate.of(2026, 9, 28), 1L, 1L,
                "OUTBOUND", "A", "B", driver, new BigDecimal("10.00"), null, null), 1).id();
        assertThatThrownBy(() -> routes.voidRoute(route, new RouteReasonRequest("原因"), 1))
                .isInstanceOf(BusinessException.class);
        routes.publish(route, new com.dfwl.fleet.route.dto.request.RoutePublishRequest(java.math.BigDecimal.ZERO, null), 1);
        assertThat(participants.list(route)).hasSize(1);
        assertThat(participants.current(route).orElseThrow().status()).isEqualTo("ASSIGNED");
        assertThatThrownBy(() -> routes.publish(route, new com.dfwl.fleet.route.dto.request.RoutePublishRequest(java.math.BigDecimal.ZERO, null), 1)).isInstanceOf(BusinessException.class);
        routes.depart(route, 1);
        assertThat(participants.current(route).orElseThrow().status()).isEqualTo("IN_TRANSIT");
        assertThatThrownBy(() -> routes.voidRoute(route, new RouteReasonRequest("原因"), 1))
                .isInstanceOf(BusinessException.class);
        routes.unload(route, new UnloadRequest(new BigDecimal("30"), new BigDecimal("10")), 1);
        assertThat(participants.current(route).orElseThrow().status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> routes.voidRoute(route, new RouteReasonRequest("  "), 1))
                .isInstanceOf(BusinessException.class);
        routes.voidRoute(route, new RouteReasonRequest("完成后作废", true), 1);
        routes.reactivate(route, 1);
        assertThat(routes.find(route).status()).isEqualTo("COMPLETED");
        assertThat(participants.list(route)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM route_status_history WHERE route_id = ?", Integer.class, route))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE business_id = ?", Integer.class, route))
                .isEqualTo(2);
    }

    @Test
    void permissionConfigurationAndAuditRedactionUseExistingTables() {
        system.replaceRolePermissions(1, new RolePermissionRequest(List.of("route:transfer", "route:salary:adjust")), 9);
        assertThat(system.findRole(1).permissions()).hasSize(2);
        audit.write("TEST", "ROLE", 1, "CHECK", Map.of("password", "secret-value", "status", "OLD"),
                Map.of("status", "NEW"), 9, "token=secret-value");
        String before = jdbc.queryForObject("SELECT before_json FROM audit_log WHERE operation_type='CHECK'", String.class);
        String reason = jdbc.queryForObject("SELECT reason FROM audit_log WHERE operation_type='CHECK'", String.class);
        assertThat(before).contains("[REDACTED]").doesNotContain("secret-value");
        assertThat(reason).doesNotContain("secret-value");
    }

    @Test
    void participantModelSupportsThreeOrderedDriversAndExactQueries() {
        long driver = masters.createDriver(driver("INTERNAL", "13911110005")).id();
        masters.bindDriverVehicle(driver, 1, 1);
        long route = routes.create(new RouteRequest(LocalDate.of(2026, 9, 28), 1L, 1L,
                "OUTBOUND", "A", "B", driver, new BigDecimal("10.00"), null, null), 1).id();
        routes.publish(route, new com.dfwl.fleet.route.dto.request.RoutePublishRequest(java.math.BigDecimal.ZERO, null), 1);
        jdbc.update("UPDATE route_participant SET status = 'TRANSFERRED' WHERE route_id = ?", route);
        for (int sequence = 2; sequence <= 3; sequence++) {
            jdbc.update("""
                    INSERT INTO route_participant (route_id, sequence_no, driver_id, driver_type_snapshot,
                        company_vehicle_id, source_type, status, assigned_at, created_by)
                    VALUES (?, ?, ?, 'INTERNAL', 1, 'TRANSFER', ?, CURRENT_TIMESTAMP, 1)
                    """, route, sequence, driver + sequence, sequence == 2 ? "TRANSFERRED" : "ASSIGNED");
        }
        jdbc.update("""
                UPDATE route_task SET current_participant_id =
                    (SELECT id FROM route_participant WHERE route_id = ? AND sequence_no = 3)
                WHERE id = ?
                """, route, route);
        assertThat(participants.list(route)).extracting(RouteParticipantRepository.Participant::sequenceNo)
                .containsExactly(1, 2, 3);
        assertThat(participants.current(route).orElseThrow().sequenceNo()).isEqualTo(3);
        assertThat(participants.listByDriver(driver)).hasSize(1);
        assertThat(participants.listByCompanyVehicle(1)).hasSize(3);
    }

    @Test
    void participantSalaryAllowsZeroButRejectsNegativeAndKeepsHistory() {
        long driver = masters.createDriver(driver("INTERNAL", "13911110006")).id();
        masters.bindDriverVehicle(driver, 1, 1);
        long route = routes.create(new RouteRequest(LocalDate.of(2026, 9, 28), 1L, 1L,
                "OUTBOUND", "A", "B", driver, new BigDecimal("10.00"), null, null), 1).id();
        routes.publish(route, new com.dfwl.fleet.route.dto.request.RoutePublishRequest(new BigDecimal("10.00"), null), 1);
        participants.updateInitialSalary(route, BigDecimal.ZERO, 1);
        assertThat(participants.current(route).orElseThrow().salaryAmount()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM route_participant_salary_history", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> participants.updateInitialSalary(route, new BigDecimal("-1"), 1))
                .isInstanceOf(BusinessException.class);
    }

    private DriverRequest driver(String type, String phone) {
        return new DriverRequest(type, "司机", phone, null, null, null, null, 1);
    }
}
