package com.dfwl.fleet.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:importdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class ImportApiTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;
    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM expense_energy_detail");
        jdbcTemplate.update("DELETE FROM expense_penalty_detail");
        jdbcTemplate.update("DELETE FROM expense_repair_detail");
        jdbcTemplate.update("DELETE FROM expense_toll_detail");
        jdbcTemplate.update("DELETE FROM expense_entry");
        jdbcTemplate.update("DELETE FROM route_status_history");
        jdbcTemplate.update("DELETE FROM route_task");
        jdbcTemplate.update("DELETE FROM tire");
        jdbcTemplate.update("DELETE FROM import_row");
        jdbcTemplate.update("DELETE FROM import_task");
        jdbcTemplate.update("DELETE FROM file_attachment");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM vehicle_trailer_current");
        jdbcTemplate.update("DELETE FROM driver");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM trailer");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM product");

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
        for (long id = 1; id <= 6; id++) {
            seedImportFile(id);
        }
        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员", Set.of(
                "import:preview", "import:commit", "import:history", "import:failure:export")));
    }

    @Test
    void routeImportWritesPublishedUnpublishedDuplicateAndMismatchRows() throws Exception {
        long taskId = preview("""
                {"businessType":"ROUTE","originalFileId":1,
                 "rows":[
                   {"rowNo":1,"rawData":{"businessDate":"2026-09-11","customerId":1,"productId":1,
                    "direction":"OUTBOUND","loadingPlace":"A","unloadingPlace":"B","taxUnitPrice":"12.3456",
                    "assignedDriverId":1,"vehicleId":1}},
                   {"rowNo":2,"rawData":{"businessDate":"2026-09-11","customerId":1,"productId":1,
                    "direction":"RETURN","loadingPlace":"B","unloadingPlace":"A","taxUnitPrice":"10.0000",
                    "assignedDriverId":2}},
                   {"rowNo":3,"rawData":{"businessDate":"2026-09-11","customerId":1,"productId":1,
                    "direction":"OUTBOUND","loadingPlace":"A","unloadingPlace":"B","taxUnitPrice":"12.3456",
                    "assignedDriverId":1,"vehicleId":1}},
                   {"rowNo":4,"rawData":{"businessDate":"2026-09-12","customerId":1,"productId":1,
                    "direction":"OUTBOUND","loadingPlace":"A","unloadingPlace":"C","taxUnitPrice":"12.3456",
                    "assignedDriverId":1,"vehicleId":2}}
                 ]}
                """);

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
    void commitRevalidatesBindingAfterPreview() throws Exception {
        long taskId = preview("""
                {"businessType":"ROUTE","originalFileId":2,
                 "rows":[{"rowNo":1,"rawData":{"businessDate":"2026-09-12","customerId":1,"productId":1,
                 "direction":"OUTBOUND","loadingPlace":"C","unloadingPlace":"D","taxUnitPrice":"9.0000",
                 "assignedDriverId":1,"vehicleId":1}}]}
                """);
        jdbcTemplate.update("DELETE FROM driver_vehicle_current WHERE driver_id = 1");
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (1, 2, CURRENT_TIMESTAMP, 1)");

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].finalErrorCode").value("ROUTE_003"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task", Integer.class)).isZero();
    }

    @Test
    void repeatedCommitIsIdempotent() throws Exception {
        long taskId = preview("""
                {"businessType":"TIRE","originalFileId":3,
                 "rows":[{"rowNo":1,"rawData":{"tireNo":"T-001","barcode":"B001","used":false}}]}
                """);

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));
        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tire WHERE tire_no = 'T-001'", Integer.class)).isEqualTo(1);
    }

    @Test
    void tireAndExpenseImportWriteBusinessTablesAndAttachmentIsLinked() throws Exception {
        long tireTaskId = preview("""
                {"businessType":"TIRE","originalFileId":4,
                 "rows":[
                   {"rowNo":1,"rawData":{"tireNo":"T-NEW","barcode":"BN","used":false}},
                   {"rowNo":2,"rawData":{"tireNo":"T-HIS","barcode":"BH","used":true,
                    "installTime":"2026-09-10T08:00:00","vehicleId":1,"driverId":1}}
                 ]}
                """);
        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(tireTaskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tire WHERE tire_no = 'T-NEW'", String.class)).isEqualTo("IN_STOCK");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tire WHERE tire_no = 'T-HIS'", String.class)).isEqualTo("CLAIMED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tire_claim c
                JOIN tire t ON t.id = c.tire_id
                WHERE t.tire_no = 'T-HIS' AND c.source_type = 'IMPORT'
                """, Integer.class)).isEqualTo(1);

        long expenseTaskId = preview("""
                {"businessType":"EXPENSE","originalFileId":5,
                 "rows":[{"rowNo":1,"rawData":{"expenseType":"ELECTRIC",
                  "businessDate":"2026-09-11","vehicleId":1,"attributionType":"DAILY","amount":"88.50",
                  "energyDetail":{"energyType":"ELECTRIC","stationId":9,"orderNo":"EO-1","startTime":"2026-09-11T10:00:00","quantity":"12.345"}}}]}
                """);
        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(expenseTaskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));
        Long expenseId = jdbcTemplate.queryForObject("SELECT id FROM expense_entry WHERE amount = 88.50", Long.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_energy_detail WHERE expense_id = ?", Integer.class, expenseId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT business_unique_key FROM import_row WHERE business_id = ?", String.class, expenseId))
                .isEqualTo("ENERGY|ELECTRIC|9|EO-1");
        assertThat(jdbcTemplate.queryForObject("SELECT owner_id FROM file_attachment WHERE id = 5", Long.class)).isEqualTo(expenseTaskId);
    }

    @Test
    void taskCountsMatchRowsAndFailureExportWorks() throws Exception {
        long taskId = preview("""
                {"businessType":"TIRE","originalFileId":1,
                 "rows":[
                   {"rowNo":1,"rawData":{"tireNo":"T-DUP","barcode":"B1"}},
                   {"rowNo":2,"rawData":{"tireNo":"T-DUP","barcode":"B2"}}
                 ]}
                """);
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
        long taskId = preview("""
                {"businessType":"ROUTE","originalFileId":6,
                 "rows":[{"rowNo":1,"businessUniqueKey":"UNIQUE-BUT-BAD-ROUTE-NO",
                 "rawData":{"routeNo":"R-DUP","businessDate":"2026-09-13","customerId":1,"productId":1,
                 "direction":"OUTBOUND","loadingPlace":"M","unloadingPlace":"N","taxUnitPrice":"1.0000"}}]}
                """);

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].finalErrorCode").value("IMPORT_DB_ERROR"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task WHERE business_unique_key = 'UNIQUE-BUT-BAD-ROUTE-NO'", Integer.class)).isZero();
    }

    private long preview(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/imports/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("id").asLong();
    }

    private void seedImportFile(long id) {
        jdbcTemplate.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key, original_filename,
                                             content_type, file_size, file_hash, uploaded_by)
                VALUES (?, 'IMPORT', 0, 'IMPORT_FILE', ?, ?, 'text/csv', 10, ?, 1)
                """, id, "imports/" + id + ".csv", id + ".csv", "hash-" + id);
    }
}
