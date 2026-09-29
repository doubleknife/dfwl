package com.dfwl.fleet.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.master.repository.MasterDataRepository;
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
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
    @Autowired MasterDataRepository masterData;
    @Autowired MockMvc mvc;
    @Autowired TokenAuthenticationService tokens;

    @BeforeEach
    void setup() {
        for (String table : List.of("task_unload_evidence", "task_weight_version", "departure_event",
                "task_command_request", "participant_salary_history", "transport_participant",
                "task_status_history", "task_amount_history", "dispatch_batch_row", "transport_task",
                "dispatch_batch", "route_template", "audit_log", "file_attachment",
                "driver_vehicle_current", "vehicle", "driver", "product")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO product (id, name, enabled) VALUES (1, '砂石', 1)");
        jdbc.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load,
                    load_standard_type, load_standard_percent, status)
                VALUES (10, '云A001', 1, 'ELECTRIC', 30, 'PERCENT', 0.9000, 1)
                """);
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone, status) VALUES (11, 501, 'INTERNAL', '自有司机', '13911111111', 1)");
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone, status) VALUES (12, 502, 'OUTSOURCED', '外协司机', '13922222222', 1)");
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
                Set.of("task:view", "task:depart", "task:unload"));
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
        var salaryPage = salaries.list(null, "2026-09", 1, 20);
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
        String driverToken = tokens.issueToken(new AuthenticatedUser(501L, "13911111111", 2L,
                "DRIVER", "司机", Set.of("task:view", "salary:mine")));
        String otherToken = tokens.issueToken(new AuthenticatedUser(502L, "13922222222", 2L,
                "DRIVER", "司机", Set.of("task:view", "salary:mine")));
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

    private DispatchBatchRequest batch(boolean outsourced) {
        return new DispatchBatchRequest(LocalDate.of(2026, 9, 30), List.of(
                new DispatchBatchRequest.Row("TEMP-001", outsourced ? 12L : 11L,
                        outsourced ? "外协A001" : null, null, null,
                        outsourced ? money("0.00") : null, null)));
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
}
