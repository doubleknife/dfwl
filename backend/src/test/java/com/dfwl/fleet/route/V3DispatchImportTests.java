package com.dfwl.fleet.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.route.dto.request.DispatchBatchRequest;
import com.dfwl.fleet.route.dto.request.DispatchConfirmRequest;
import com.dfwl.fleet.route.dto.request.RouteTemplateRequest;
import com.dfwl.fleet.route.service.DispatchBatchService;
import com.dfwl.fleet.route.service.RouteTemplateService;
import com.dfwl.fleet.route.service.TaskImportService;
import com.dfwl.fleet.security.AuthenticatedUser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3dispatchimport;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class V3DispatchImportTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskImportService imports;
    @Autowired DispatchBatchService dispatch;
    @Autowired RouteTemplateService templates;

    private final AuthenticatedUser operator = new AuthenticatedUser(1L, "13900000001", 1L, "ADMIN", "管理员",
            Set.of("task:import:submit", "task:import:view", "dispatch:batch", "dispatch:view"));

    @BeforeEach
    void setup() {
        for (String table : List.of("import_row", "import_task", "task_status_history", "transport_participant",
                "dispatch_batch_row", "transport_task", "dispatch_batch", "route_template", "audit_log",
                "file_attachment", "driver_vehicle_current", "vehicle", "driver", "product"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO product (id, name, enabled) VALUES (1, '砂石', 1)");
        jdbc.update("INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, "
                + "load_standard_type, load_standard_percent, status) VALUES (10, '云A001', 1, 'ELECTRIC', 30, 'PERCENT', 0.9, 1)");
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone, status) "
                + "VALUES (11, 501, 'INTERNAL', '自有司机', '13911111111', 1)");
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone, status) "
                + "VALUES (12, 502, 'OUTSOURCED', '外协司机', '13922222222', 1)");
        jdbc.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) "
                + "VALUES (11, 10, CURRENT_TIMESTAMP, 1)");
        templates.create(new RouteTemplateRequest("TPL-01", "砂石运输", "中文客户", 1L, "昆明", "曲靖",
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("20.00"), null, null), 1);
    }

    @Test
    void fixedWorkbookPublishesRowsUsingSharedDefaultsAndKeepsHistory() throws Exception {
        byte[] workbook = workbook(List.of(
                List.of("2026-09-30", "TPL-01", "13911111111", "", "", "", "", "首趟"),
                List.of("2026-09-30", "TPL-01", "13922222222", "外协甲001", "120.00", "0.00", "0.00", "外协")));
        var preview = imports.preview(file(workbook), "import-1", operator);
        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.rows()).allMatch(row -> "VALID".equals(row.status()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isZero();
        var confirmed = imports.confirm(preview.id(), operator);
        assertThat(confirmed.status()).isEqualTo("COMPLETED");
        assertThat(confirmed.rows()).allMatch(row -> "PUBLISHED".equals(row.status()) && row.taskNo() != null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT salary_amount FROM transport_participant WHERE driver_id = 12",
                BigDecimal.class)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT actual_unit_price FROM transport_task WHERE customer_name_snapshot = '中文客户' "
                + "ORDER BY id LIMIT 1", BigDecimal.class)).isEqualByComparingTo("100.00");
        assertThat(imports.confirm(preview.id(), operator).rows()).hasSize(2);
        assertThat(imports.preview(file(workbook), "import-1", operator).id()).isEqualTo(preview.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isEqualTo(2);
        assertThat(imports.history(1, 20).total()).isEqualTo(1);
    }

    @Test
    void badAndDuplicateRowsHaveIndividualResultsWithoutCreatingDrivers() throws Exception {
        byte[] workbook = workbook(List.of(
                List.of("2026-09-30", "TPL-01", "13911111111", "", "", "", "", ""),
                List.of("2026-09-30", "TPL-01", "13911111111", "", "", "", "", ""),
                List.of("2026-09-30", "TPL-01", "13999999999", "", "", "", "", "")));
        var preview = imports.preview(file(workbook), "import-bad", operator);
        assertThat(preview.rows()).extracting(TaskImportService.ImportRowView::status)
                .containsExactly("VALID", "INVALID", "INVALID");
        var confirmed = imports.confirm(preview.id(), operator);
        assertThat(confirmed.status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(confirmed.rows()).extracting(TaskImportService.ImportRowView::status)
                .containsExactly("PUBLISHED", "FAILED", "FAILED");
        assertThat(confirmed.rows().get(1).message()).contains("重复");
        assertThat(confirmed.rows().get(2).message()).contains("司机");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver", Integer.class)).isEqualTo(2);
        var duplicate = imports.preview(file(workbook(List.of(
                List.of("2026-09-30", "TPL-01", "13911111111", "", "", "", "", "")))), "import-again", operator);
        assertThat(duplicate.rows().getFirst().message()).contains("已经导入");
    }

    @Test
    void rejectsChangedHeaderAndRevalidatesDispatchAfterPreview() throws Exception {
        byte[] workbook = workbook(List.of(List.of("2026-09-30", "TPL-01", "13911111111", "", "", "", "", "")));
        try (XSSFWorkbook xlsx = new XSSFWorkbook(new ByteArrayInputStream(workbook));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            xlsx.getSheetAt(0).getRow(0).getCell(1).setCellValue("数据库模板ID");
            xlsx.write(out);
            assertThatThrownBy(() -> imports.preview(file(out.toByteArray()), "wrong-header", operator))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("表头");
        }
        DispatchBatchRequest batch = new DispatchBatchRequest(LocalDate.of(2026, 9, 30),
                List.of(new DispatchBatchRequest.Row("TPL-01", 11L, null, null, null, null, null)));
        var preview = dispatch.preview(batch, 1);
        jdbc.update("UPDATE vehicle SET status = 0 WHERE id = 10");
        assertThatThrownBy(() -> dispatch.confirm(new DispatchConfirmRequest(preview.previewId(), "changed", batch), 1))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isZero();
    }

    @Test
    void dispatchPublishesMultipleRowsForSameDriverAndCopiesOverrides() {
        DispatchBatchRequest batch = new DispatchBatchRequest(LocalDate.of(2026, 9, 30), List.of(
                new DispatchBatchRequest.Row("TPL-01", 11L, null, null, null, null, "第一趟"),
                new DispatchBatchRequest.Row("TPL-01", 11L, null, new BigDecimal("125.00"),
                        new BigDecimal("0.00"), new BigDecimal("8.00"), "第二趟")));
        var preview = dispatch.preview(batch, 1);
        assertThat(preview.valid()).isTrue();
        var result = dispatch.confirm(new DispatchConfirmRequest(preview.previewId(), "multi-same-driver", batch), 1);
        assertThat(result.rows()).hasSize(2);
        assertThat(jdbc.queryForList("SELECT actual_unit_price FROM transport_task ORDER BY id", BigDecimal.class))
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("125.00"));
        assertThat(jdbc.queryForList("SELECT salary_amount FROM transport_participant ORDER BY id", BigDecimal.class))
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("8.00"));
        assertThat(dispatch.confirm(new DispatchConfirmRequest(preview.previewId(), "multi-same-driver", batch), 1)
                .batchId()).isEqualTo(result.batchId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isEqualTo(2);
    }

    @Test
    void templateOrBindingChangesAfterPreviewPublishNothing() {
        DispatchBatchRequest batch = new DispatchBatchRequest(LocalDate.of(2026, 9, 30), List.of(
                new DispatchBatchRequest.Row("TPL-01", 11L, null, null, null, null, null),
                new DispatchBatchRequest.Row("TPL-01", 12L, "外协乙001", null, null, null, null)));
        var firstPreview = dispatch.preview(batch, 1);
        jdbc.update("UPDATE route_template SET enabled = 0");
        assertThatThrownBy(() -> dispatch.confirm(new DispatchConfirmRequest(firstPreview.previewId(),
                "disabled-template", batch), 1)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isZero();
        jdbc.update("UPDATE route_template SET enabled = 1");
        var secondPreview = dispatch.preview(batch, 1);
        jdbc.update("DELETE FROM driver_vehicle_current WHERE driver_id = 11");
        assertThatThrownBy(() -> dispatch.confirm(new DispatchConfirmRequest(secondPreview.previewId(),
                "changed-binding", batch), 1)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isZero();
    }

    @Test
    void concurrentConfirmationWithSameKeyCreatesOneBatch() throws Exception {
        DispatchBatchRequest batch = new DispatchBatchRequest(LocalDate.of(2026, 9, 30),
                List.of(new DispatchBatchRequest.Row("TPL-01", 11L, null, null, null, null, null)));
        var preview = dispatch.preview(batch, 1);
        DispatchConfirmRequest confirm = new DispatchConfirmRequest(preview.previewId(), "concurrent-key", batch);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> dispatch.confirm(confirm, 1).batchId());
            var second = executor.submit(() -> dispatch.confirm(confirm, 1).batchId());
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_task", Integer.class)).isEqualTo(1);
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "运输任务固定模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private byte[] workbook(List<List<String>> values) throws Exception {
        try (XSSFWorkbook xlsx = new XSSFWorkbook(new ByteArrayInputStream(imports.template()));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = xlsx.getSheetAt(0);
            int rowNo = 1;
            for (List<String> row : values) {
                var excelRow = sheet.createRow(rowNo++);
                for (int i = 0; i < row.size(); i++) excelRow.createCell(i).setCellValue(row.get(i));
            }
            xlsx.write(out);
            return out.toByteArray();
        }
    }
}
