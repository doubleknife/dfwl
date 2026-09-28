package com.dfwl.fleet.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.master.dto.request.DriverRequest;
import com.dfwl.fleet.master.service.MasterDataService;
import com.dfwl.fleet.route.dto.request.ParticipantSalaryAdjustmentRequest;
import com.dfwl.fleet.route.dto.request.RoutePublishRequest;
import com.dfwl.fleet.route.dto.request.RouteReasonRequest;
import com.dfwl.fleet.route.dto.request.RouteRequest;
import com.dfwl.fleet.route.dto.request.RouteTransferRequest;
import com.dfwl.fleet.route.dto.request.UnloadRequest;
import com.dfwl.fleet.route.repository.RouteParticipantRepository;
import com.dfwl.fleet.route.repository.RouteRepository;
import com.dfwl.fleet.route.service.RouteService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:biz1bdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class Biz1BTransferTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MasterDataService masters;
    @Autowired RouteService routes;
    @Autowired RouteParticipantRepository participants;
    @Autowired RouteRepository routeRepository;
    private int unique;

    @BeforeEach
    void setup() {
        for (String table : List.of("audit_log", "route_participant_salary_history", "route_participant",
                "route_weight_version", "route_status_history", "route_task", "driver_salary_history",
                "driver_salary_entry", "driver_vehicle_history", "driver_vehicle_current", "vehicle",
                "driver", "product", "customer", "sys_role_permission", "sys_permission", "sys_user", "sys_role"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (1,'ADMIN','管理员',1)");
        jdbc.update("INSERT INTO customer (id, customer_name, status) VALUES (1,'客户',1)");
        jdbc.update("INSERT INTO product (id, product_name, status) VALUES (1,'产品',1)");
        for (int id = 1; id <= 5; id++) jdbc.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load,
                                     load_standard_type, load_standard_percent, status)
                VALUES (?, ?, 1, 'ELECTRIC', 30, 'PERCENT', 0.9000, 1)
                """, id, "TEST" + id);
        unique = 0;
    }

    @Test
    void initialInternalAndOutsourcedSalaryRequireExplicitFactsAndAllowZero() {
        long internal = driver("INTERNAL", 1);
        long ownRoute = route(internal);
        assertThatThrownBy(() -> routes.publish(ownRoute, null, 1)).isInstanceOf(BusinessException.class);
        routes.publish(ownRoute, new RoutePublishRequest(new BigDecimal("20.00"), null), 1);
        assertThat(participants.current(ownRoute).orElseThrow().salaryAmount()).isEqualByComparingTo("20.00");
        long outsourced = driver("OUTSOURCED", 0);
        long externalRoute = route(outsourced);
        assertThatThrownBy(() -> routes.publish(externalRoute, new RoutePublishRequest(BigDecimal.ZERO, null), 1))
                .isInstanceOf(BusinessException.class);
        assertThat(participants.list(externalRoute)).isEmpty();
        routes.publish(externalRoute, new RoutePublishRequest(BigDecimal.ZERO, "外协A001"), 1);
        var p = participants.current(externalRoute).orElseThrow();
        assertThat(p.companyVehicleId()).isNull();
        assertThat(p.outsourcedPlateNo()).isEqualTo("外协A001");
        assertThat(p.salaryAmount()).isEqualByComparingTo("0.00");
        routes.depart(externalRoute, 1);
        assertThat(routes.find(externalRoute).departureVehicleId()).isNull();
        routes.unload(externalRoute, new UnloadRequest(new BigDecimal("30"), new BigDecimal("10")), 1);
        assertThat(routes.find(externalRoute).loadStandardMet()).isNull();
    }

    @Test
    void threeTransitionsIncludeUndepartedTransferAndCurrentMustDepartAgain() {
        long a = driver("INTERNAL", 1), b = driver("OUTSOURCED", 0);
        long c = driver("INTERNAL", 3), d = driver("OUTSOURCED", 0);
        long route = route(a);
        routes.publish(route, new RoutePublishRequest(new BigDecimal("10"), null), 1);
        routes.depart(route, 1);
        var before = routes.find(route);
        routes.transfer(route, transfer(route, before.version(), before.currentParticipantId(), b, "外协B", "0", "0"), 1);
        assertThat(routes.find(route).status()).isEqualTo("TRANSFER_PENDING");
        assertThat(routeRepository.canDriverDepartRoute(route, a)).isFalse();
        assertThat(routeRepository.canDriverDepartRoute(route, b)).isTrue();
        assertThatThrownBy(() -> routes.cancel(route, new RouteReasonRequest("取消"), 1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> routes.voidRoute(route, new RouteReasonRequest("作废"), 1)).isInstanceOf(BusinessException.class);
        var second = routes.find(route);
        routes.transfer(route, transfer(route, second.version(), second.currentParticipantId(), c, null, "10", "5", "0"), 1);
        var third = routes.find(route);
        routes.transfer(route, transfer(route, third.version(), third.currentParticipantId(), d, "外协D", "10", "5", "0", "0"), 1);
        assertThat(participants.list(route)).extracting(RouteParticipantRepository.Participant::sequenceNo)
                .containsExactly(1, 2, 3, 4);
        assertThat(participants.list(route)).extracting(RouteParticipantRepository.Participant::status)
                .containsExactly("TRANSFERRED", "TRANSFERRED", "TRANSFERRED", "ASSIGNED");
        assertThat(participants.effectiveSalary(route)).isEqualByComparingTo("15");
        assertThat(routes.find(route).departureTime()).isNotNull();
        routes.depart(route, 1);
        assertThat(routes.find(route).status()).isEqualTo("IN_TRANSIT");
        assertThat(participants.current(route).orElseThrow().driverId()).isEqualTo(d);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE operation_type = 'ROUTE_TRANSFER'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void salaryAdjustmentAndTransferAreVersionedAndDoNotWriteNoopHistory() {
        long a = driver("INTERNAL", 1), b = driver("INTERNAL", 2);
        long route = route(a);
        routes.publish(route, new RoutePublishRequest(new BigDecimal("50"), null), 1);
        routes.depart(route, 1);
        var first = participants.current(route).orElseThrow();
        routes.adjustParticipantSalary(route, first.id(), new ParticipantSalaryAdjustmentRequest(
                new BigDecimal("50"), first.version(), "核对"), 1);
        assertThat(historyCount()).isZero();
        routes.adjustParticipantSalary(route, first.id(), new ParticipantSalaryAdjustmentRequest(
                BigDecimal.ZERO, first.version(), "修正"), 1);
        assertThat(historyCount()).isEqualTo(1);
        assertThatThrownBy(() -> routes.adjustParticipantSalary(route, first.id(),
                new ParticipantSalaryAdjustmentRequest(BigDecimal.ONE, first.version(), "旧版本"), 1))
                .isInstanceOf(BusinessException.class);
        var stale = routes.find(route);
        var request = transfer(route, stale.version(), stale.currentParticipantId(), b, null, "0", "20");
        routes.transfer(route, request, 1);
        assertThat(participants.effectiveSalary(route)).isEqualByComparingTo("20");
        assertThat(routes.find(route).version()).isGreaterThan(stale.version());
        assertThatThrownBy(() -> routes.transfer(route, request, 1))
                .isInstanceOf(BusinessException.class);
        assertThat(participants.list(route)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT driver_salary FROM route_task WHERE id = ?", BigDecimal.class, route))
                .isEqualByComparingTo("20");
    }

    @Test
    void invalidSalarySetAndBusyVehicleRollbackAllFacts() {
        long a = driver("INTERNAL", 1), b = driver("INTERNAL", 2), c = driver("INTERNAL", 3);
        long route = route(a);
        routes.publish(route, new RoutePublishRequest(new BigDecimal("10"), null), 1);
        routes.depart(route, 1);
        var state = routes.find(route);
        var missing = new RouteTransferRequest(state.currentParticipantId(), state.version(), b, null,
                BigDecimal.ZERO, "漏工资", List.of());
        assertThatThrownBy(() -> routes.transfer(route, missing, 1)).isInstanceOf(BusinessException.class);
        var forged = new RouteTransferRequest(state.currentParticipantId(), state.version(), b, null,
                BigDecimal.ZERO, "伪造", List.of(new RouteTransferRequest.SalaryDecision(999L, BigDecimal.ZERO)));
        assertThatThrownBy(() -> routes.transfer(route, forged, 1)).isInstanceOf(BusinessException.class);
        assertThat(participants.list(route)).hasSize(1);
        long busy = route(c);
        routes.publish(busy, new RoutePublishRequest(BigDecimal.ZERO, null), 1);
        routes.depart(busy, 1);
        assertThatThrownBy(() -> routes.transfer(route,
                transfer(route, state.version(), state.currentParticipantId(), c, null, "0", "0"), 1))
                .isInstanceOf(BusinessException.class);
        assertThat(participants.list(route)).hasSize(1);
        assertThat(historyCount()).isZero();
    }

    @Test
    void ordinaryCancellationExcludesInitialSalaryWithoutDeletingParticipant() {
        long driver = driver("INTERNAL", 1);
        long route = route(driver);
        routes.publish(route, new RoutePublishRequest(new BigDecimal("35.00"), null), 1);
        routes.cancel(route, new RouteReasonRequest("任务取消"), 1);
        assertThat(participants.list(route)).hasSize(1);
        assertThat(participants.list(route).getFirst().status()).isEqualTo("CANCELLED");
        assertThat(participants.effectiveSalary(route)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT driver_salary FROM route_task WHERE id = ?", BigDecimal.class, route))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void completedRouteAllowsIndependentSalaryCorrectionWithoutChangingParticipantFacts() {
        long driver = driver("INTERNAL", 1);
        long route = route(driver);
        routes.publish(route, new RoutePublishRequest(BigDecimal.ZERO, null), 1);
        routes.depart(route, 1);
        routes.unload(route, new UnloadRequest(new BigDecimal("30"), new BigDecimal("10")), 1);
        var before = participants.current(route).orElseThrow();
        routes.adjustParticipantSalary(route, before.id(), new ParticipantSalaryAdjustmentRequest(
                new BigDecimal("25.00"), before.version(), "月末核定"), 1);
        var after = participants.current(route).orElseThrow();
        assertThat(after.status()).isEqualTo(before.status());
        assertThat(after.driverId()).isEqualTo(before.driverId());
        assertThat(after.companyVehicleId()).isEqualTo(before.companyVehicleId());
        assertThat(after.salaryAmount()).isEqualByComparingTo("25.00");
        assertThat(historyCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE operation_type = 'ROUTE_PARTICIPANT_SALARY_ADJUST'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void salaryCorrectionInvalidatesEarlierTransferForm() {
        long firstDriver = driver("INTERNAL", 1), nextDriver = driver("INTERNAL", 2);
        long route = route(firstDriver);
        routes.publish(route, new RoutePublishRequest(new BigDecimal("30.00"), null), 1);
        routes.depart(route, 1);
        var before = routes.find(route);
        var original = participants.current(route).orElseThrow();
        var staleTransfer = transfer(route, before.version(), original.id(), nextDriver, null, "30", "10");
        routes.adjustParticipantSalary(route, original.id(), new ParticipantSalaryAdjustmentRequest(
                new BigDecimal("45.00"), original.version(), "先核定"), 1);
        assertThat(routes.find(route).version()).isGreaterThan(before.version());
        assertThatThrownBy(() -> routes.transfer(route, staleTransfer, 1)).isInstanceOf(BusinessException.class);
        assertThat(participants.list(route)).hasSize(1);
        assertThat(participants.effectiveSalary(route)).isEqualByComparingTo("45.00");
    }

    private long driver(String type, int vehicleId) {
        long driver = masters.createDriver(new DriverRequest(type, "司机" + (++unique),
                "13922220" + String.format("%03d", unique), null, null, null, null, 1)).id();
        if (vehicleId > 0) masters.bindDriverVehicle(driver, vehicleId, 1);
        return driver;
    }

    private long route(long driver) {
        return routes.create(new RouteRequest(LocalDate.of(2026, 9, 28), 1L, 1L,
                "OUTBOUND", "装货" + (++unique), "卸货", driver, new BigDecimal("10.00"), null, null), 1).id();
    }

    private RouteTransferRequest transfer(long route, int version, long currentId, long next,
                                          String plate, String... amounts) {
        List<RouteParticipantRepository.Participant> all = participants.list(route).stream()
                .filter(p -> !"CANCELLED".equals(p.status()) && !"LEGACY_ENDED".equals(p.status())).toList();
        if (amounts.length != all.size() + 1) throw new IllegalArgumentException("all salaries required");
        List<RouteTransferRequest.SalaryDecision> decisions = new ArrayList<>();
        for (int i = 0; i < all.size(); i++)
            decisions.add(new RouteTransferRequest.SalaryDecision(all.get(i).id(), new BigDecimal(amounts[i])));
        return new RouteTransferRequest(currentId, version, next, plate,
                new BigDecimal(amounts[amounts.length - 1]), "转派测试", decisions);
    }

    private int historyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM route_participant_salary_history", Integer.class);
    }
}
