package com.dfwl.fleet.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.dfwl.fleet.approval.api.ApprovalActionRequest;
import com.dfwl.fleet.approval.api.ApprovalCreateRequest;
import com.dfwl.fleet.approval.service.ApprovalService;
import com.dfwl.fleet.expense.api.ExpenseReversalRequest;
import com.dfwl.fleet.expense.service.ExpenseService;
import com.dfwl.fleet.imports.api.ImportPreviewRequest;
import com.dfwl.fleet.imports.service.ImportService;
import com.dfwl.fleet.master.service.MasterDataService;
import com.dfwl.fleet.route.api.UnloadRequest;
import com.dfwl.fleet.route.service.RouteService;
import com.dfwl.fleet.settlement.service.SettlementService;
import com.dfwl.fleet.tire.api.TireRequestCreateRequest;
import com.dfwl.fleet.tire.service.TireService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrencydb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class CriticalConcurrencyTests {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RouteService routeService;
    @Autowired private MasterDataService masterDataService;
    @Autowired private ApprovalService approvalService;
    @Autowired private ExpenseService expenseService;
    @Autowired private TireService tireService;
    @Autowired private ImportService importService;
    @Autowired private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        for (String table : List.of(
                "settlement_diff", "settlement_detail", "settlement_version", "settlement_month",
                "import_row", "import_task", "expense_reversal_link", "expense_energy_detail",
                "expense_penalty_detail", "expense_repair_detail", "expense_toll_detail", "expense_entry",
                "tire_claim", "tire_request_item", "tire_request", "approval_action", "approval_task",
                "approval_submission_version", "approval_instance", "approval_flow_node", "approval_flow",
                "route_weight_version", "route_status_history", "route_task", "file_attachment",
                "driver_vehicle_history", "driver_vehicle_current", "vehicle_trailer_history",
                "vehicle_trailer_current", "tire", "trailer", "vehicle", "driver", "customer", "product")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("INSERT INTO customer (id, customer_name, status) VALUES (1, '客户A', 1)");
        jdbcTemplate.update("INSERT INTO product (id, product_name, status) VALUES (1, '砂石', 1)");
        jdbcTemplate.update("""
                INSERT INTO driver (id, name, phone, id_card_no, driver_type, status)
                VALUES (1, 'Driver A', '13800000001', '110101199001010011', 'INTERNAL', 1),
                       (2, 'Driver B', '13800000002', '110101199001010012', 'INTERNAL', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1),
                       (2, '京A10002', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (1, 1, CURRENT_TIMESTAMP, 1)");
        jdbcTemplate.update("INSERT INTO driver_vehicle_history (driver_id, vehicle_id, bind_time, bind_by) VALUES (1, 1, CURRENT_TIMESTAMP, 1)");
        jdbcTemplate.update("INSERT INTO tire (id, tire_no, barcode, status, data_source) VALUES (1, 'TIRE-001', 'BC001', 'IN_STOCK', 'MANUAL')");
        jdbcTemplate.update("INSERT INTO approval_flow (id, approval_type, flow_name, version_no, status, created_by) VALUES (1, 'GENERAL', '通用审批', 1, 'ACTIVE', 1)");
        jdbcTemplate.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (1, 1, '审批人', 2)");
    }

    @Test
    void sameRouteConcurrentDepartOnlyOneSucceeds() throws Exception {
        insertPublishedRoute(1, 1);

        Results results = runConcurrently(2, () -> routeService.depart(1, 1));

        assertThat(results.success()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_status_history WHERE route_id = 1 AND operation_type = 'DEPART'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM route_task WHERE id = 1", String.class)).isEqualTo("IN_TRANSIT");
    }

    @Test
    void sameVehicleConcurrentDepartToTwoRoutesOnlyOneSucceeds() throws Exception {
        insertPublishedRoute(1, 1);
        insertPublishedRoute(2, 1);

        Results results = runConcurrently(2, List.of(
                () -> routeService.depart(1, 1),
                () -> routeService.depart(2, 1)));

        assertThat(results.success()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task WHERE status = 'IN_TRANSIT' AND departure_vehicle_id = 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentUnloadWritesOneWeightVersionAndCompletesOnce() throws Exception {
        insertPublishedRoute(1, 1);
        routeService.depart(1, 1);

        Results results = runConcurrently(2, () -> routeService.unload(1, new UnloadRequest(new BigDecimal("30.000"), new BigDecimal("10.000")), 1));

        assertThat(results.success()).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_weight_version WHERE route_id = 1 AND version_no = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_status_history WHERE route_id = 1 AND operation_type = 'UNLOAD'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentBindSameVehicleOnlyOneSucceeds() throws Exception {
        jdbcTemplate.update("DELETE FROM driver_vehicle_history");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");

        Results results = runConcurrently(2, List.of(
                () -> { masterDataService.bindDriverVehicle(1, 1, 1); return null; },
                () -> { masterDataService.bindDriverVehicle(2, 1, 1); return null; }));

        assertThat(results.success()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_vehicle_current WHERE vehicle_id = 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentApprovalSameTaskOnlyOneTakesEffect() throws Exception {
        long approvalId = approvalService.create(new ApprovalCreateRequest("GENERAL", "GENERAL", null, Map.of("x", 1), null), 1).id();

        Results results = runConcurrently(2, () -> approvalService.approve(approvalId, new ApprovalActionRequest("ok", null), 2));

        assertThat(results.success()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM approval_action WHERE action_type = 'APPROVE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentReverseSameExpenseCreatesOneReversal() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id,
                                           attribution_type, amount, source_type, status, created_by)
                VALUES (1, 'E001', 'REPAIR', CURRENT_DATE, 1, 'DAILY', 100.00, 'MANUAL', 'ACTIVE', 1)
                """);

        Results results = runConcurrently(2, () -> expenseService.reverse(1, new ExpenseReversalRequest("修正"), 1));

        assertThat(results.success()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_entry WHERE reversal_of_id = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_reversal_link WHERE original_expense_id = 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentReserveSameTireOnlyOneSucceeds() throws Exception {
        Results results = runConcurrently(2, () -> tireService.createRequest(new TireRequestCreateRequest(
                1L, 1L, List.of(new TireRequestCreateRequest.Item(1L, "TIRE-001", null)))));

        assertThat(results.success()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tire WHERE id = 1", String.class)).isEqualTo("APPROVAL_RESERVED");
    }

    @Test
    void concurrentCommitSameImportTaskDoesNotDuplicateBusinessRows() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key, original_filename, content_type, file_size, uploaded_by)
                VALUES (1, 'IMPORT', 0, 'IMPORT_FILE', 'import/a.csv', 'a.csv', 'text/csv', 10, 1)
                """);
        ImportPreviewRequest request = new ImportPreviewRequest("TIRE", null, 1L, List.of(
                new ImportPreviewRequest.ImportRowRequest(1, Map.of("tireNo", "TIRE-NEW", "barcode", "BNEW"), null)));
        long taskId = importService.preview(request, 1).id();

        Results results = runConcurrently(2, () -> importService.commit(taskId, 1));

        assertThat(results.success()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tire WHERE tire_no = 'TIRE-NEW'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM import_task WHERE id = ?", String.class, taskId)).isEqualTo("COMMITTED");
    }

    @Test
    void concurrentSettlementGenerationUsesUniqueSequentialVersions() throws Exception {
        Results results = runConcurrently(2, () -> settlementService.generate("2026-09", 1));

        assertThat(results.success()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT version_no FROM settlement_version ORDER BY version_no", Integer.class))
                .containsExactly(1, 2);
    }

    private void insertPublishedRoute(long id, long driverId) {
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status,
                                        assigned_driver_id, created_by)
                VALUES (?, ?, ?, DATE '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 1.0000, 'PUBLISHED', ?, 1)
                """, id, "R00" + id, "K00" + id, driverId);
    }

    private Results runConcurrently(int count, Callable<?> task) throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(task);
        }
        return runConcurrently(count, tasks);
    }

    private Results runConcurrently(int count, List<Callable<?>> tasks) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            int success = 0;
            int failure = 0;
            for (var future : futures) {
                try {
                    future.get();
                    success++;
                } catch (Exception ex) {
                    failure++;
                }
            }
            return new Results(success, failure);
        } finally {
            executor.shutdownNow();
        }
    }

    private record Results(int success, int failure) {
    }
}
