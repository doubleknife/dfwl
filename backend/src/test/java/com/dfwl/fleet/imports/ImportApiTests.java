package com.dfwl.fleet.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:importdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql",
        "fleet.storage.local-root=${java.io.tmpdir}/fleet-import-tests"
})
@AutoConfigureMockMvc
class ImportApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TokenAuthenticationService tokenAuthenticationService;
    @Autowired private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() {
        for (String table : java.util.List.of(
                "driver_salary_history", "driver_salary_entry",
                "expense_energy_detail", "expense_penalty_detail", "expense_repair_detail", "expense_toll_detail",
                "expense_entry", "route_status_history", "route_task", "tire_claim", "tire",
                "import_row", "import_task", "file_attachment", "import_field_mapping", "import_template",
                "driver_vehicle_current", "vehicle_trailer_current", "driver", "vehicle", "trailer", "customer", "product")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("INSERT INTO customer (id, customer_name, status) VALUES (1, '客户A', 1)");
        jdbcTemplate.update("INSERT INTO product (id, product_name, status) VALUES (1, '砂石', 1)");
        jdbcTemplate.update("""
                INSERT INTO driver (id, name, phone, id_card_no, driver_type, status)
                VALUES (1, '司机A', '13800000001', '110101199001010011', 'INTERNAL', 1),
                       (2, '司机B', '13800000002', '110101199001010012', 'INTERNAL', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1),
                       (2, '京A10002', 1, 'GAS', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by)
                VALUES (1, 1, CURRENT_TIMESTAMP, 1)
                """);
        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员", Set.of(
                "import:preview", "import:commit", "import:history", "import:failure:export")));
    }

    @Test
    void xlsxRouteImportParsesFileAndCommitsPublishedUnpublishedDuplicateAndMismatchRows() throws Exception {
        long templateId = routeTemplateWithChineseHeaders();
        long fileId = uploadWorkbook("routes.xlsx",
                workbook(true, new String[]{"日期", "客户", "产品", "方向", "装货地", "卸货地", "单价", "司机", "车辆"},
                        new Object[][]{
                                {LocalDate.of(2026, 9, 11), 1, 1, "OUTBOUND", "A", "B", "12.3456", 1, 1},
                                {LocalDate.of(2026, 9, 11), 1, 1, "RETURN", "B", "A", "10.0000", 2, null},
                                {LocalDate.of(2026, 9, 11), 1, 1, "OUTBOUND", "A", "B", "12.3456", 1, 1},
                                {LocalDate.of(2026, 9, 12), 1, 1, "OUTBOUND", "A", "C", "12.3456", 1, 2}
                        }));
        long taskId = preview("ROUTE", templateId, fileId);

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.unpublishedCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(2))
                .andExpect(jsonPath("$.data.rows[0].finalStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.rows[1].finalStatus").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.data.rows[2].finalErrorCode").value("IMPORT_DUPLICATE_IN_BATCH"))
                .andExpect(jsonPath("$.data.rows[3].finalErrorCode").value("ROUTE_003"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task WHERE status = 'PUBLISHED'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task WHERE status = 'UNPUBLISHED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void xlsTireImportParsesLegacyWorkbookAndPreservesTextValues() throws Exception {
        long fileId = uploadWorkbook("tires.xls",
                workbook(false, new String[]{"tireNo", "barcode", "used", "installTime", "vehicleId", "driverId"},
                        new Object[][]{
                                {"001-TIRE", "000123", false, null, null, null},
                                {"T-HIS", "BH", true, LocalDateTime.of(2026, 9, 10, 8, 0), 1, 1}
                        }));
        long taskId = preview("TIRE", null, fileId);

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT barcode FROM tire WHERE tire_no = '001-TIRE'", String.class)).isEqualTo("000123");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tire WHERE tire_no = 'T-HIS'", String.class)).isEqualTo("CLAIMED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tire_claim", Integer.class)).isEqualTo(1);
    }

    @Test
    void csvExpenseImportParsesNestedMappedColumnsAndWritesDetails() throws Exception {
        long templateId = template("EXPENSE",
                new String[][]{
                        {"类型", "expenseType", "1"}, {"日期", "businessDate", "1"}, {"车辆", "vehicleId", "1"},
                        {"归属", "attributionType", "1"}, {"金额", "amount", "1"}, {"站点", "energyDetail.stationId", "0"},
                        {"订单", "energyDetail.orderNo", "1"}, {"开始时间", "energyDetail.startTime", "1"},
                        {"度数", "energyDetail.quantity", "1"}
                });
        long fileId = uploadCsv("expenses.csv", """
                类型,日期,车辆,归属,金额,站点,订单,开始时间,度数
                ELECTRIC,2026-09-11,1,DAILY,88.50,9,EO-1,2026-09-11T10:00:00,12.345
                """);
        long taskId = preview("EXPENSE", templateId, fileId);

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));
        Long expenseId = jdbcTemplate.queryForObject("SELECT id FROM expense_entry WHERE amount = 88.50", Long.class);
        assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM expense_energy_detail WHERE expense_id = ?", java.math.BigDecimal.class, expenseId))
                .isEqualByComparingTo("12.345");
    }

    @Test
    void salaryCsvImportWritesSalaryAndDuplicateCommitIsIdempotent() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status,
                                        assigned_driver_id, created_by)
                VALUES (10, 'R010', 'RK10', '2026-09-11', 1, 1, 'OUTBOUND', 'A', 'B', 1.0000, 'COMPLETED', 1, 1)
                """);
        long fileId = uploadCsv("salary.csv", """
                routeId,driverId,salaryAmount
                10,1,230.50
                """);
        long taskId = preview("SALARY", null, fileId);

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));
        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_entry WHERE route_id = 10", Integer.class)).isEqualTo(1);
    }

    @Test
    void previewFailsForMissingRequiredColumnCorruptFileAndNoDataRows() throws Exception {
        long templateId = template("TIRE", new String[][]{{"胎号", "tireNo", "1"}, {"条码", "barcode", "0"}});
        long missingColumnFileId = uploadCsv("bad.csv", "条码\nB001\n");
        previewExpectBadRequest("TIRE", templateId, missingColumnFileId);

        long corruptFileId = uploadRaw("bad.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "not excel".getBytes(StandardCharsets.UTF_8));
        previewExpectBadRequest("TIRE", null, corruptFileId);

        long noRowsFileId = uploadCsv("empty.csv", "tireNo,barcode\n");
        previewExpectBadRequest("TIRE", null, noRowsFileId);
    }

    @Test
    void commitRevalidatesBindingAfterPreviewFromFile() throws Exception {
        long fileId = uploadCsv("routes.csv", """
                businessDate,customerId,productId,direction,loadingPlace,unloadingPlace,taxUnitPrice,assignedDriverId,vehicleId
                2026-09-12,1,1,OUTBOUND,C,D,9.0000,1,1
                """);
        long taskId = preview("ROUTE", null, fileId);
        jdbcTemplate.update("DELETE FROM driver_vehicle_current WHERE driver_id = 1");
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (1, 2, CURRENT_TIMESTAMP, 1)");

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].finalErrorCode").value("ROUTE_003"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task", Integer.class)).isZero();
    }

    @Test
    void taskCountsMatchRowsAndFailureExportWorks() throws Exception {
        long fileId = uploadCsv("dups.csv", """
                tireNo,barcode
                T-DUP,B1
                T-DUP,B2
                """);
        long taskId = preview("TIRE", null, fileId);
        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(1));

        Integer successRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM import_row WHERE final_status = 'SUCCESS'", Integer.class);
        Integer failureRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM import_row WHERE final_status = 'FAILURE'", Integer.class);
        assertThat(jdbcTemplate.queryForObject("SELECT success_count FROM import_task WHERE id = ?", Integer.class, taskId)).isEqualTo(successRows);
        assertThat(jdbcTemplate.queryForObject("SELECT failure_count FROM import_task WHERE id = ?", Integer.class, taskId)).isEqualTo(failureRows);

        mockMvc.perform(get("/api/v1/imports/%d/failures/export".formatted(taskId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("IMPORT_DUPLICATE_IN_BATCH")));
    }

    @Test
    void databaseWriteExceptionRollsBackBusinessAndRecordsFailure() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO route_task (route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status, created_by)
                VALUES ('R-DUP', 'EXISTING', '2026-09-01', 1, 1, 'OUTBOUND', 'X', 'Y', 1.0000, 'UNPUBLISHED', 1)
                """);
        long fileId = uploadCsv("bad-route.csv", """
                routeNo,businessDate,customerId,productId,direction,loadingPlace,unloadingPlace,taxUnitPrice
                R-DUP,2026-09-13,1,1,OUTBOUND,M,N,1.0000
                """);
        long taskId = preview("ROUTE", null, fileId);

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].finalErrorCode").value("IMPORT_DB_ERROR"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task WHERE route_no = 'R-DUP' AND business_unique_key <> 'EXISTING'", Integer.class)).isZero();
    }

    private long preview(String businessType, Long templateId, long fileId) throws Exception {
        String json = """
                {"businessType":"%s","templateId":%s,"originalFileId":%d}
                """.formatted(businessType, templateId == null ? "null" : templateId.toString(), fileId);
        MvcResult result = mockMvc.perform(post("/api/v1/imports/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("id").asLong();
    }

    private void previewExpectBadRequest(String businessType, Long templateId, long fileId) throws Exception {
        String json = """
                {"businessType":"%s","templateId":%s,"originalFileId":%d}
                """.formatted(businessType, templateId == null ? "null" : templateId.toString(), fileId);
        mockMvc.perform(post("/api/v1/imports/preview")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict());
    }

    private long uploadCsv(String filename, String content) throws Exception {
        return uploadRaw(filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private long uploadWorkbook(String filename, byte[] content) throws Exception {
        String contentType = filename.endsWith(".xls")
                ? "application/vnd.ms-excel"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return uploadRaw(filename, contentType, content);
    }

    private long uploadRaw(String filename, String contentType, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", filename, contentType, content))
                        .param("ownerType", "IMPORT")
                        .param("ownerId", "0")
                        .param("purpose", "IMPORT_FILE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("id").asLong();
    }

    private byte[] workbook(boolean xlsx, String[] headers, Object[][] rows) throws Exception {
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("import");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle timeStyle = workbook.createCellStyle();
            timeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else if (value instanceof Boolean bool) {
                        cell.setCellValue(bool);
                    } else if (value instanceof LocalDate date) {
                        cell.setCellValue(date);
                        cell.setCellStyle(dateStyle);
                    } else if (value instanceof LocalDateTime dateTime) {
                        cell.setCellValue(dateTime);
                        cell.setCellStyle(timeStyle);
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private long routeTemplateWithChineseHeaders() {
        return template("ROUTE", new String[][]{
                {"日期", "businessDate", "1"}, {"客户", "customerId", "1"}, {"产品", "productId", "1"},
                {"方向", "direction", "1"}, {"装货地", "loadingPlace", "1"}, {"卸货地", "unloadingPlace", "1"},
                {"单价", "taxUnitPrice", "1"}, {"司机", "assignedDriverId", "0"}, {"车辆", "vehicleId", "0"}
        });
    }

    private long template(String businessType, String[][] mappings) {
        jdbcTemplate.update("INSERT INTO import_template (template_name, business_type, status) VALUES (?, ?, 1)",
                businessType + "模板", businessType);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM import_template", Long.class);
        for (String[] mapping : mappings) {
            jdbcTemplate.update("""
                    INSERT INTO import_field_mapping (template_id, source_column, target_field, required_flag)
                    VALUES (?, ?, ?, ?)
                    """, id, mapping[0], mapping[1], Integer.parseInt(mapping[2]));
        }
        return id;
    }
}
