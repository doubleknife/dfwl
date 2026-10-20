package com.dfwl.fleet.route;

import static com.dfwl.fleet.security.TestFunctionGrants.grants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.master.repository.MasterDataRepository;
import com.dfwl.fleet.master.service.MasterDataService;
import com.dfwl.fleet.master.dto.request.DriverRequest;
import com.dfwl.fleet.route.dto.request.DispatchBatchRequest;
import com.dfwl.fleet.route.dto.request.DispatchConfirmRequest;
import com.dfwl.fleet.route.dto.request.RouteTemplateRequest;
import com.dfwl.fleet.route.dto.request.TaskCancelRequest;
import com.dfwl.fleet.route.dto.request.TaskDepartRequest;
import com.dfwl.fleet.route.dto.request.TaskSalariesConfirmRequest;
import com.dfwl.fleet.route.dto.request.TaskTransferRequest;
import com.dfwl.fleet.route.dto.request.TaskUnloadRequest;
import com.dfwl.fleet.route.dto.request.TaskWeightAdjustmentRequest;
import com.dfwl.fleet.route.dto.request.TaskAmountAdjustmentRequest;
import com.dfwl.fleet.route.service.DispatchBatchService;
import com.dfwl.fleet.route.service.RouteTemplateService;
import com.dfwl.fleet.route.service.TaskAmountService;
import com.dfwl.fleet.route.service.TransportTaskService;
import com.dfwl.fleet.salary.service.DriverSalaryService;
import com.dfwl.fleet.salary.service.MonthlyOperationsService;
import com.dfwl.fleet.salary.service.SalaryLedgerService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3tasksdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class V3TaskFoundationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired RouteTemplateService templates;
    @Autowired DispatchBatchService dispatch;
    @Autowired TransportTaskService tasks;
    @Autowired TaskAmountService amounts;
    @Autowired DriverSalaryService salaries;
    @Autowired SalaryLedgerService salaryLedger;
    @Autowired MonthlyOperationsService monthlyOperations;
    @Autowired MasterDataRepository masterData;
    @Autowired MasterDataService masterService;
    @Autowired MockMvc mvc;
    @Autowired TokenAuthenticationService tokens;

    @BeforeEach
    void setup() {
        long driverRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='DRIVER'", Long.class);
        jdbc.update("MERGE INTO sys_user(id,phone,password_hash,role_id,status) KEY(id) VALUES(501,'13911111111','test-only',?,1)", driverRole);
        jdbc.update("MERGE INTO sys_user(id,phone,password_hash,role_id,status) KEY(id) VALUES(502,'13922222222','test-only',?,1)", driverRole);
        for (String table : List.of("task_unload_evidence", "task_weight_version", "departure_event",
                "task_command_request", "participant_salary_history", "transport_participant",
                "task_status_history", "task_amount_history", "dispatch_batch_row", "transport_task",
                "dispatch_batch", "route_template", "audit_log", "file_attachment",
                "driver_vehicle_current", "vehicle", "driver", "product")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO product (id, name, enabled) VALUES (1, '砂石', 1)");
        jdbc.update("""
                INSERT INTO vehicle (id, plate_no, vehicle_license_no, insurance_complete, energy_type, max_load,
                    load_standard_type, load_standard_percent, status)
                VALUES (10, '云A001', 'TEST-VL-010', 1, 'ELECTRIC', 30, 'PERCENT', 0.9000, 1)
                """);
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone, id_card_no, driving_license_no, status) VALUES (11, 501, 'INTERNAL', '自有司机', '13911111111', '110105199001010117', 'TEST-DL-011', 1)");
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone, id_card_no, driving_license_no, status) VALUES (12, 502, 'OUTSOURCED', '外协司机', '13922222222', '110105199001010125', 'TEST-DL-012', 1)");
        jdbc.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (11, 10, CURRENT_TIMESTAMP, 1)");
        templates.create(new RouteTemplateRequest("TEMP-001", "昆明至曲靖", "昆明客户", 1L,
                "昆明", "曲靖", money("100.00"), money("20.00"), money("30.00"), null, null), 1);
    }

    @Test
    void publishUsesSnapshotAndOneParticipantSalary() {
        DispatchBatchRequest request = batch(false);
        var preview = dispatch.preview(request, 1);
        assertThat(preview.valid()).isTrue();
        assertThat(preview.rows()).hasSize(1);
        var published = dispatch.confirm(new DispatchConfirmRequest(preview.previewId(), "batch-1", request), 1);
        assertThat(published.rows()).hasSize(1);
        long id = published.rows().getFirst().taskId();
        var task = tasks.find(id);
        assertThat(task.templateName()).isEqualTo("昆明至曲靖");
        assertThat(task.customerName()).isEqualTo("昆明客户");
        assertThat(task.actualUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(task.actualInformationFee()).isEqualByComparingTo("20.00");
        assertThat(tasks.participants(id, true).getFirst().salary()).isEqualByComparingTo("30.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_participant WHERE task_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(dispatch.confirm(new DispatchConfirmRequest(preview.previewId(), "batch-1", request), 1).batchId())
                .isEqualTo(published.batchId());

        var template = templates.list().getFirst();
        templates.update(template.id(), new RouteTemplateRequest("TEMP-001", "新名称", "新客户", 1L,
                "新装货地", "新卸货地", money("150.00"), money("60.00"), money("70.00"), null,
                template.version()), 1);
        assertThat(tasks.find(id).templateName()).isEqualTo("昆明至曲靖");
        assertThat(tasks.find(id).actualUnitPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void currentPublishedDriverCannotBeDisabledButCompletedAndCancelledDoNotBlock() {
        DispatchBatchRequest request = batch(false);
        long taskId = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "disable-block", request), 1).rows().getFirst().taskId();
        DriverRequest disabled = new DriverRequest("INTERNAL", "自有司机", "13911111111",
                "TEST-DL-011", "110105199001010117", 0);
        assertThatThrownBy(() -> masterService.updateDriver(11, disabled, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1 条未完成运输任务");
        assertThat(jdbc.queryForObject("SELECT status FROM driver WHERE id=11", Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE transport_task SET status='COMPLETED' WHERE id=?", taskId);
        assertThat(masterService.updateDriver(11, disabled, 1).status()).isZero();
        assertThat(dispatch.preview(request, 1).valid()).isFalse();
        masterService.updateDriver(11, new DriverRequest("INTERNAL", "自有司机", "13911111111",
                "TEST-DL-011", "110105199001010117", 1), 1);
        assertThat(dispatch.preview(request, 1).valid()).isFalse();
        masterService.bindDriverVehicle(11, 10, 1);
        assertThat(dispatch.preview(request, 1).valid()).isTrue();
    }

    @Test
    void currentOutsourcedDriverIsBlockedButHistoricalTransferredDriverIsNot() {
        DispatchBatchRequest request = batch(true);
        long taskId = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "disable-outsource", request), 1).rows().getFirst().taskId();
        DriverRequest disabled = new DriverRequest("OUTSOURCED", "外协司机", "13922222222",
                "TEST-DL-012", "110105199001010125", 0);
        assertThatThrownBy(() -> masterService.updateDriver(12, disabled, 1))
                .isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE transport_participant SET is_current=0,status='TRANSFERRED' WHERE task_id=?", taskId);
        jdbc.update("""
                INSERT INTO transport_participant(task_id,sequence_no,driver_id,driver_type_snapshot,
                    company_vehicle_id,vehicle_plate_snapshot,source_type,status,is_current,assigned_at,salary_amount,created_by)
                VALUES(?,2,11,'INTERNAL',10,'云A001','TRANSFER','ASSIGNED',1,CURRENT_TIMESTAMP,0,1)
                """, taskId);
        Long current = jdbc.queryForObject("SELECT id FROM transport_participant WHERE task_id=? AND sequence_no=2",
                Long.class, taskId);
        jdbc.update("UPDATE transport_task SET current_participant_id=?,status='TRANSFER_PENDING' WHERE id=?", current, taskId);
        assertThat(masterService.updateDriver(12, disabled, 1).status()).isZero();
    }

    @Test
    void disablingAndPublishingTheSameDriverCannotBothSucceed() throws Exception {
        DispatchBatchRequest request = batch(false);
        var preview = dispatch.preview(request, 1);
        var confirm = new DispatchConfirmRequest(preview.previewId(), "disable-publish-race", request);
        var disabled = new DriverRequest("INTERNAL", "自有司机", "13911111111",
                "TEST-DL-011", "110105199001010117", 0);
        var gate = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var publish = workers.submit(() -> {
                gate.await();
                try { dispatch.confirm(confirm, 1); return true; }
                catch (RuntimeException ex) { return false; }
            });
            var disable = workers.submit(() -> {
                gate.await();
                try { masterService.updateDriver(11, disabled, 1); return true; }
                catch (RuntimeException ex) { return false; }
            });
            gate.countDown();
            boolean published = publish.get(10, TimeUnit.SECONDS);
            boolean turnedOff = disable.get(10, TimeUnit.SECONDS);
            assertThat(published && turnedOff).isFalse();
            assertThat(published || turnedOff).isTrue();
            if (published) {
                assertThat(jdbc.queryForObject("SELECT status FROM driver WHERE id=11", Integer.class)).isEqualTo(1);
            } else {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isZero();
            }
        }
    }

    @Test
    void disablingAndDepartingAClosedAllocationKeepsDriverActive() throws Exception {
        DispatchBatchRequest request = batch(false);
        long taskId = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "disable-depart-race", request), 1).rows().getFirst().taskId();
        var disabled = new DriverRequest("INTERNAL", "自有司机", "13911111111",
                "TEST-DL-011", "110105199001010117", 0);
        var driver = new AuthenticatedUser(501L, "13911111111", 2L, "DRIVER", "司机",
                grants("PAGE_ROUTES", "BUTTON_MINI_ROUTES_DEPART"));
        int version = tasks.find(taskId).version();
        var gate = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var departure = workers.submit(() -> {
                gate.await();
                return tasks.depart(taskId, new TaskDepartRequest("race-depart", version), driver);
            });
            var disable = workers.submit(() -> {
                gate.await();
                try { masterService.updateDriver(11, disabled, 1); return true; }
                catch (BusinessException ex) { return false; }
            });
            gate.countDown();
            assertThat(departure.get(10, TimeUnit.SECONDS).status()).isEqualTo("IN_TRANSIT");
            assertThat(disable.get(10, TimeUnit.SECONDS)).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM driver WHERE id=11", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void invalidRowAbortsWholeBatchAndOutsourcedRequiresRealPlate() {
        DispatchBatchRequest valid = batch(false);
        DispatchBatchRequest all = new DispatchBatchRequest(valid.businessDate(), List.of(valid.rows().getFirst(),
                new DispatchBatchRequest.Row("TEMP-001", 12L, null, null, null, money("0.00"), null)));
        var preview = dispatch.preview(all, 1);
        assertThat(preview.valid()).isFalse();
        assertThatThrownBy(() -> dispatch.confirm(new DispatchConfirmRequest(preview.previewId(), "bad-batch", all), 1))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isZero();
        var missingRow = dispatch.preview(new DispatchBatchRequest(valid.businessDate(),
                java.util.Collections.singletonList((DispatchBatchRequest.Row) null)), 1);
        assertThat(missingRow.valid()).isFalse();
        assertThat(missingRow.rows().getFirst().errors()).isNotEmpty();

        var external = batch(true);
        var externalPreview = dispatch.preview(external, 1);
        assertThat(externalPreview.valid()).isTrue();
        long id = dispatch.confirm(new DispatchConfirmRequest(externalPreview.previewId(), "external-batch", external), 1)
                .rows().getFirst().taskId();
        assertThat(tasks.participants(id, true).getFirst().outsourcedPlate()).isEqualTo("外协A001");
        assertThat(tasks.participants(id, true).getFirst().companyVehiclePlate()).isNull();
        assertThat(tasks.participants(id, true).getFirst().salary()).isEqualByComparingTo("0.00");
    }

    @Test
    void departedTaskCannotCancelAndUnloadKeepsOriginalTicket() {
        DispatchBatchRequest request = batch(false);
        long id = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(), "trip-1", request), 1)
                .rows().getFirst().taskId();
        AuthenticatedUser driver = new AuthenticatedUser(501L, "13911111111", 2L, "DRIVER", "司机",
                grants("PAGE_ROUTES", "BUTTON_MINI_ROUTES_DEPART", "BUTTON_MINI_ROUTES_UNLOAD"));
        var departed = tasks.depart(id, new TaskDepartRequest("depart-1", tasks.find(id).version()), driver);
        assertThat(departed.status()).isEqualTo("IN_TRANSIT");
        assertThat(masterData.hasInTransitDriverOrVehicle(11, 10)).isTrue();
        assertThat(masterData.hasInTransitVehicleOrTrailer(10, -1)).isTrue();
        assertThat(masterData.canDriverViewVehicle(10, 11)).isTrue();
        assertThat(masterData.listVehiclesForDriver(11, 1, 20).total()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM departure_event WHERE task_id = ?", Integer.class, id)).isEqualTo(1);
        assertThatThrownBy(() -> tasks.cancel(id, new TaskCancelRequest("不允许", departed.version(), "cancel-1"), 1))
                .isInstanceOf(BusinessException.class);
        jdbc.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key,
                    original_filename, content_type, file_size, uploaded_by)
                VALUES (100, 'TASK', ?, 'ROUTE_UNLOAD_WEIGHT_TICKET', 'test/ticket.png',
                    'ticket.png', 'image/png', 12, 501)
                """, id);
        assertThatThrownBy(() -> tasks.unload(id,
                new TaskUnloadRequest(money("30"), money("5"), List.of(), "unload-empty"), driver))
                .isInstanceOf(BusinessException.class);
        var completed = tasks.unload(id,
                new TaskUnloadRequest(money("30"), money("5"), List.of(100L), "unload-1"), driver);
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.netWeight()).isEqualByComparingTo("25");
        assertThat(masterData.hasInTransitDriverOrVehicle(11, 10)).isFalse();
        assertThat(masterData.hasInTransitVehicleOrTrailer(10, -1)).isFalse();
        assertThat(tasks.weightVersions(id).getFirst().originalTicketAttachmentIds()).containsExactly(100L);
        assertThat(tasks.findForDriver(driver, id).toString()).doesNotContain("actualUnitPrice", "actualInformationFee", "salary");
    }

    @Test
    void transferReDepartureAndAdjustmentsHaveOneSourceOfTruth() {
        DispatchBatchRequest request = batch(false);
        long id = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(), "trip-transfer", request), 1)
                .rows().getFirst().taskId();
        AuthenticatedUser firstDriver = new AuthenticatedUser(501L, "13911111111", 2L, "DRIVER", "司机", Set.of());
        tasks.depart(id, new TaskDepartRequest("first-depart", tasks.find(id).version()), firstDriver);
        long oldId = tasks.participants(id, true).getFirst().id();
        var transferred = tasks.transfer(id, new TaskTransferRequest(12L, "外协A001",
                List.of(new TaskTransferRequest.SalaryDecision(oldId, money("12.00"))), money("0.00"),
                "司机交接", tasks.find(id).version(), oldId), 1);
        assertThat(transferred.status()).isEqualTo("TRANSFER_PENDING");
        assertThat(tasks.participants(id, true)).extracting(item -> item.salary())
                .containsExactly(money("12.00"), money("0.00"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participant_salary_history", Integer.class)).isEqualTo(1);
        AuthenticatedUser secondDriver = new AuthenticatedUser(502L, "13922222222", 2L, "DRIVER", "司机", Set.of());
        assertThatThrownBy(() -> tasks.depart(id, new TaskDepartRequest("wrong-depart", transferred.version()), firstDriver))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        tasks.depart(id, new TaskDepartRequest("second-depart", transferred.version()), secondDriver);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM departure_event WHERE task_id = ?", Integer.class, id)).isEqualTo(2);
        jdbc.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key,
                    original_filename, content_type, file_size, uploaded_by)
                VALUES (101, 'TASK', ?, 'ROUTE_UNLOAD_WEIGHT_TICKET', 'test/second.png',
                    'second.png', 'image/png', 12, 502)
                """, id);
        tasks.unload(id, new TaskUnloadRequest(money("31"), money("5"), List.of(101L), "unload-second"), secondDriver);
        assertThat(tasks.find(id).status()).isEqualTo("COMPLETED");
        assertThat(tasks.weightVersions(id).getFirst().standardResult()).isEqualTo("NOT_APPLICABLE");
        tasks.adjustWeight(id, new TaskWeightAdjustmentRequest(money("32"), money("5"),
                "计量纠错", List.of(), tasks.find(id).version()), 1);
        assertThat(tasks.weightVersions(id)).hasSize(2);
        assertThat(tasks.weightVersions(id).getFirst().originalTicketAttachmentIds()).containsExactly(101L);
        assertThat(tasks.find(id).netWeight()).isEqualByComparingTo("27");

        amounts.adjust(id, new TaskAmountAdjustmentRequest(tasks.find(id).version(), "UNIT_PRICE",
                money("120.00"), "价格纠错"), 1);
        amounts.adjust(id, new TaskAmountAdjustmentRequest(tasks.find(id).version(), "INFORMATION_FEE",
                money("5.00"), "信息费纠错"), 1);
        assertThat(amounts.history(id)).extracting(item -> item.amountKind())
                .containsExactly("UNIT_PRICE", "INFORMATION_FEE");
        assertThat(tasks.find(id).actualUnitPrice()).isEqualByComparingTo("120.00");
        assertThat(tasks.find(id).actualInformationFee()).isEqualByComparingTo("5.00");
        assertThatThrownBy(() -> amounts.adjust(id, new TaskAmountAdjustmentRequest(
                tasks.find(id).version(), "UNIT_PRICE", money("1.001"), "不允许隐式舍入"), 1))
                .isInstanceOf(BusinessException.class);
        assertThat(amounts.history(id)).hasSize(2);

        var participants = tasks.participants(id, true);
        var salaryResult = tasks.confirmParticipantSalaries(id, new TaskSalariesConfirmRequest(
                List.of(new TaskSalariesConfirmRequest.SalaryDecision(participants.get(0).id(), money("0.00"),
                                participants.get(0).version()),
                        new TaskSalariesConfirmRequest.SalaryDecision(participants.get(1).id(), money("0.00"),
                                participants.get(1).version())),
                "最终工资核定", "salary-1"), 1);
        assertThat(salaryResult).extracting(item -> item.salary()).containsExactly(money("0.00"), money("0.00"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participant_salary_history", Integer.class)).isEqualTo(2);
        var salaryPage = salaryLedger.list(new SalaryLedgerService.Filter("2026-09", null,
                null, null, null, null), 1, 20);
        assertThat(salaryPage.total()).isEqualTo(2);
        assertThat(salaryPage.records()).extracting(item -> item.salaryAmount()).containsExactly(money("0.00"), money("0.00"));
        assertThat(salaries.history(participants.get(0).id())).hasSize(2);
        var ownSlip = salaries.slip(firstDriver, "2026-09", 1, 20);
        assertThat(ownSlip.summary().totalAmount()).isEqualByComparingTo("0.00");
        assertThat(ownSlip.details().records()).hasSize(1);
        assertThatThrownBy(() -> salaries.slip(secondDriver, "2026-09", 1, 20))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void driverApiIsScopedAndLegacyRouteWritePathIsAbsent() throws Exception {
        DispatchBatchRequest request = batch(false);
        long id = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "driver-api", request), 1).rows().getFirst().taskId();
        String driverToken = tokens.issueToken(new AuthenticatedUser(501L, "13911111111", driverRoleId(),
                "DRIVER", "司机", grants("PAGE_MINI_ROUTES", "PAGE_MINI_SALARY")));
        String otherToken = tokens.issueToken(new AuthenticatedUser(502L, "13922222222", driverRoleId(),
                "DRIVER", "司机", grants("PAGE_MINI_ROUTES", "PAGE_MINI_SALARY")));
        mvc.perform(get("/api/v1/driver/transport-tasks/" + id).header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskNo").exists())
                .andExpect(jsonPath("$.data.actualUnitPrice").doesNotExist())
                .andExpect(jsonPath("$.data.actualInformationFee").doesNotExist());
        mvc.perform(get("/api/v1/driver/transport-tasks/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transport-tasks/" + id).header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/routes/" + id).header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/imports/preview").header("Authorization", "Bearer " + driverToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void salaryLedgerUsesEveryParticipantAndTransferChainWithMatchingExport() throws Exception {
        var request = batch(false);
        long id = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "salary-ledger", request), 1).rows().getFirst().taskId();
        var first = tasks.participants(id, true).getFirst();
        tasks.depart(id, new TaskDepartRequest("ledger-depart", tasks.find(id).version()),
                new AuthenticatedUser(501L, "13911111111", 2L, "DRIVER", "司机", Set.of()));
        tasks.transfer(id, new TaskTransferRequest(12L, "外协A002",
                List.of(new TaskTransferRequest.SalaryDecision(first.id(), money("10.00"))), money("0.00"),
                "临时转派", tasks.find(id).version(), first.id()), 1);
        var filter = new SalaryLedgerService.Filter("2026-09", null, null, null, null, true);
        var page = salaryLedger.list(filter, 1, 20);
        assertThat(page.records()).hasSize(2);
        assertThat(page.records()).allMatch(SalaryLedgerService.Row::transferTask);
        assertThat(page.records()).extracting(SalaryLedgerService.Row::salaryAmount)
                .containsExactly(money("10.00"), money("0.00"));
        assertThat(salaryLedger.summary(filter).salaryTotal()).isEqualByComparingTo("10.00");
        assertThat(salaryLedger.summary(filter).transferTaskCount()).isEqualTo(1);
        assertThat(salaryLedger.drivers(filter, 1, 20).records()).hasSize(2);
        assertThat(salaryLedger.list(new SalaryLedgerService.Filter("2026-09", null,
                "OUTSOURCED", null, null, true), 1, 20).total()).isEqualTo(1);
        assertThat(salaryLedger.options("DRIVER", "外协", "2026-09", 1, 20).records())
                .extracting(SalaryLedgerService.FilterOption::label).containsExactly("外协司机");
        assertThat(salaryLedger.options("TEMPLATE", "昆明", "2026-09", 1, 20).total()).isEqualTo(1);
        assertThat(salaryLedger.options("TASK", "", "2026-09", 1, 20).total()).isEqualTo(1);
        assertThatThrownBy(() -> salaryLedger.options("OTHER", "", "2026-09", 1, 20))
                .isInstanceOf(BusinessException.class);
        assertThat(monthlyOperations.attendanceDrivers("自有", 1, 20).total()).isEqualTo(1);
        assertThat(monthlyOperations.attendanceDrivers("外协", 1, 20).total()).isZero();
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(salaryLedger.export(filter)))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(2);
            assertThat(book.getSheetAt(0).getRow(2).getCell(6).getStringCellValue()).isEqualTo("外协司机");
        }
        String driverToken = tokens.issueToken(new AuthenticatedUser(501L, "13911111111", driverRoleId(),
                "DRIVER", "司机", grants("PAGE_SALARIES", "BUTTON_SALARIES_EXPORT", "PAGE_ATTENDANCE",
                        "BUTTON_ATTENDANCE_EXPORT", "PAGE_DISPATCH_INTERNAL", "BUTTON_DISPATCH_INTERNAL_EXPORT")));
        for (String path : List.of("/api/v1/driver-salaries?month=2026-09", "/api/v1/attendance?month=2026-09",
                "/api/v1/dispatch-ledger/internal?month=2026-09",
                "/api/v1/driver-salaries/export?month=2026-09",
                "/api/v1/attendance/export?month=2026-09",
                "/api/v1/dispatch-ledger/export?month=2026-09&scope=INTERNAL")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + driverToken)).andExpect(status().isForbidden());
        }
    }

    @Test
    void attendanceCountsActualDepartureDayOnceAndLedgersCountEachHistoricalVehicle() throws Exception {
        var request = batch(false);
        long firstTask = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "month-1", request), 1).rows().getFirst().taskId();
        long secondTask = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "month-2", request), 1).rows().getFirst().taskId();
        var driver = new AuthenticatedUser(501L, "13911111111", 2L, "DRIVER", "司机", Set.of());
        tasks.depart(firstTask, new TaskDepartRequest("month-depart-1", tasks.find(firstTask).version()), driver);
        jdbc.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key,
                    original_filename, content_type, file_size, uploaded_by)
                VALUES (120, 'TASK', ?, 'ROUTE_UNLOAD_WEIGHT_TICKET', 'test/month.png',
                    'month.png', 'image/png', 12, 501)
                """, firstTask);
        tasks.unload(firstTask, new TaskUnloadRequest(money("30"), money("5"), List.of(120L), "month-unload"), driver);
        tasks.depart(secondTask, new TaskDepartRequest("month-depart-2", tasks.find(secondTask).version()), driver);
        jdbc.update("UPDATE transport_participant SET departed_at='2026-09-18 10:00:00', ended_at='2026-09-19 08:00:00' WHERE task_id=?", firstTask);
        jdbc.update("UPDATE transport_participant SET departed_at='2026-09-18 14:00:00' WHERE task_id=?", secondTask);
        var attendance = monthlyOperations.attendance("2026-09", null, 1, 20);
        assertThat(attendance.records()).hasSize(1);
        assertThat(attendance.records().getFirst().monthlyTotal()).isEqualTo(1);
        assertThat(attendance.records().getFirst().days().get(17).tasks()).hasSize(2);
        assertThat(attendance.records().getFirst().days().get(18).count()).isZero();
        var vehicle = monthlyOperations.ledger("2026-09", false, 1, 20);
        assertThat(vehicle.records()).hasSize(1);
        assertThat(vehicle.records().getFirst().plate()).isEqualTo("云A001");
        assertThat(vehicle.records().getFirst().days().get(17).count()).isEqualTo(2);
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(monthlyOperations.exportAttendance("2026-09", null)))) {
            assertThat(book.getSheetAt(0).getRow(1).getCell(2 + 17).getNumericCellValue()).isEqualTo(1);
        }
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(monthlyOperations.exportLedger("2026-09", false)))) {
            assertThat(book.getSheetAt(0).getRow(1).getCell(3 + 17).getNumericCellValue()).isEqualTo(2);
        }
    }

    @Test
    void outsourcedDispatchUsesActualPlateAndNeverCreatesCompanyVehicle() throws Exception {
        var first = batch(true);
        long taskId = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(first, 1).previewId(),
                "external-month", first), 1).rows().getFirst().taskId();
        var externalDriver = new AuthenticatedUser(502L, "13922222222", 2L, "DRIVER", "司机", Set.of());
        tasks.depart(taskId, new TaskDepartRequest("external-depart", tasks.find(taskId).version()), externalDriver);
        jdbc.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key,
                    original_filename, content_type, file_size, uploaded_by)
                VALUES (122, 'TASK', ?, 'ROUTE_UNLOAD_WEIGHT_TICKET', 'test/external.png',
                    'external.png', 'image/png', 12, 502)
                """, taskId);
        tasks.unload(taskId, new TaskUnloadRequest(money("30"), money("5"), List.of(122L), "external-unload"), externalDriver);
        var second = new DispatchBatchRequest(LocalDate.of(2026, 9, 30), List.of(
                new DispatchBatchRequest.Row("TEMP-001", 12L, "外协B002", null, null, money("0.00"), null)));
        long secondId = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(second, 1).previewId(),
                "external-month-second", second), 1).rows().getFirst().taskId();
        tasks.depart(secondId, new TaskDepartRequest("external-depart-two", tasks.find(secondId).version()), externalDriver);
        var month = java.time.YearMonth.now().toString();
        var external = monthlyOperations.ledger(month, true, 1, 20);
        assertThat(external.records()).hasSize(2);
        assertThat(external.records()).extracting(MonthlyOperationsService.MonthlyRow::plate)
                .containsExactlyInAnyOrder("外协A001", "外协B002");
        assertThat(external.monthTotal()).isEqualTo(2);
        assertThat(monthlyOperations.attendance(month, null, 1, 20).monthTotal()).isZero();
        assertThat(monthlyOperations.ledger(month, false, 1, 20).monthTotal()).isZero();
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(monthlyOperations.exportLedger(month, true)))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    void salaryConfirmationSkipsNoOpAndRejectsStaleOrNegativeAmounts() {
        var request = batch(false);
        long id = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "salary-confirm-c", request), 1).rows().getFirst().taskId();
        var participant = tasks.participants(id, true).getFirst();
        var unchanged = new TaskSalariesConfirmRequest(List.of(new TaskSalariesConfirmRequest.SalaryDecision(
                participant.id(), participant.salary(), participant.version())), "月末核对", "same-salary");
        tasks.confirmParticipantSalaries(id, unchanged, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participant_salary_history", Integer.class)).isZero();
        assertThat(tasks.participants(id, true).getFirst().version()).isEqualTo(participant.version());
        var changed = new TaskSalariesConfirmRequest(List.of(new TaskSalariesConfirmRequest.SalaryDecision(
                participant.id(), money("0.00"), participant.version())), "按实际参与核定", "zero-salary");
        tasks.confirmParticipantSalaries(id, changed, 1);
        tasks.confirmParticipantSalaries(id, changed, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participant_salary_history", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> tasks.confirmParticipantSalaries(id, new TaskSalariesConfirmRequest(
                List.of(new TaskSalariesConfirmRequest.SalaryDecision(participant.id(), money("1.00"),
                        participant.version())), "旧版本不可覆盖", "stale-salary"), 1))
                .isInstanceOf(BusinessException.class);
        var current = tasks.participants(id, true).getFirst();
        assertThatThrownBy(() -> tasks.confirmParticipantSalaries(id, new TaskSalariesConfirmRequest(
                List.of(new TaskSalariesConfirmRequest.SalaryDecision(participant.id(), money("-1.00"),
                        current.version())), "工资不能为负", "negative-salary"), 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void oneCompanyPlateWithDifferentDriversKeepsBothActualHistoricalRows() {
        var request = batch(false);
        long firstTask = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(request, 1).previewId(),
                "same-car-first", request), 1).rows().getFirst().taskId();
        var firstDriver = new AuthenticatedUser(501L, "13911111111", 2L, "DRIVER", "司机", Set.of());
        tasks.depart(firstTask, new TaskDepartRequest("same-car-depart-1", tasks.find(firstTask).version()), firstDriver);
        jdbc.update("UPDATE transport_participant SET trailer_plate_snapshot='挂A' WHERE task_id=?", firstTask);
        jdbc.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key,
                    original_filename, content_type, file_size, uploaded_by)
                VALUES (121, 'TASK', ?, 'ROUTE_UNLOAD_WEIGHT_TICKET', 'test/car.png',
                    'car.png', 'image/png', 12, 501)
                """, firstTask);
        tasks.unload(firstTask, new TaskUnloadRequest(money("30"), money("5"), List.of(121L), "same-car-unload"), firstDriver);
        jdbc.update("DELETE FROM driver_vehicle_current WHERE driver_id=11");
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone, id_card_no, driving_license_no, status) VALUES (13, 503, 'INTERNAL', '第二司机', '13933333333', '110105199001010133', 'TEST-DL-013', 1)");
        jdbc.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (13, 10, CURRENT_TIMESTAMP, 1)");
        var second = new DispatchBatchRequest(LocalDate.of(2026, 9, 30), List.of(
                new DispatchBatchRequest.Row("TEMP-001", 13L, null, null, null, null, null)));
        long secondTask = dispatch.confirm(new DispatchConfirmRequest(dispatch.preview(second, 1).previewId(),
                "same-car-second", second), 1).rows().getFirst().taskId();
        tasks.depart(secondTask, new TaskDepartRequest("same-car-depart-2", tasks.find(secondTask).version()),
                new AuthenticatedUser(503L, "13933333333", 2L, "DRIVER", "司机", Set.of()));
        var month = java.time.YearMonth.now().toString();
        var ledger = monthlyOperations.ledger(month, false, 1, 20);
        assertThat(ledger.records()).hasSize(2);
        assertThat(ledger.records()).extracting(MonthlyOperationsService.MonthlyRow::plate)
                .containsOnly("云A001");
        assertThat(ledger.records()).extracting(MonthlyOperationsService.MonthlyRow::driverName)
                .containsExactlyInAnyOrder("自有司机", "第二司机");
        assertThat(ledger.records()).extracting(MonthlyOperationsService.MonthlyRow::trailerPlate)
                .containsExactlyInAnyOrder("挂A", null);
        assertThat(monthlyOperations.attendance(month, null, 1, 20).monthTotal()).isEqualTo(2);
    }

    private DispatchBatchRequest batch(boolean outsourced) {
        return new DispatchBatchRequest(LocalDate.of(2026, 9, 30), List.of(
                new DispatchBatchRequest.Row("TEMP-001", outsourced ? 12L : 11L,
                        outsourced ? "外协A001" : null, null, null,
                        outsourced ? money("0.00") : null, null)));
    }

    private long driverRoleId() {
        return jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='DRIVER'", Long.class);
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
}
