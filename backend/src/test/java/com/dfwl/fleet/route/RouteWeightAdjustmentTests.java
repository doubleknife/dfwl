package com.dfwl.fleet.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.route.dto.request.WeightAdjustmentRequest;
import com.dfwl.fleet.route.service.RouteService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:routeweightdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class RouteWeightAdjustmentTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    @Autowired
    private RouteService routeService;

    private String financeToken;
    private String driverToken;

    @Test
    void unloadEvidenceIsRequiredScopedAndImmutableAfterCorrection() throws Exception {
        mockMvc.perform(post("/api/v1/routes/2/unload")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":30,\"tareWeight\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_004"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_weight_version WHERE route_id = 2", Integer.class)).isZero();

        long otherRoute = RouteTicketFixture.ticket(jdbcTemplate, 3, 101);
        long otherUser = RouteTicketFixture.ticket(jdbcTemplate, 2, 999);
        for (long invalid : List.of(otherRoute, otherUser, 1L)) {
            mockMvc.perform(post("/api/v1/routes/2/unload")
                            .header("Authorization", "Bearer " + driverToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"grossWeight\":30,\"tareWeight\":10,\"evidenceAttachmentIds\":[" + invalid + "]}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ATTACHMENT_004"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_unload_evidence", Integer.class)).isZero();
        long ticket = RouteTicketFixture.ticket(jdbcTemplate, 2, 101);
        mockMvc.perform(post("/api/v1/routes/2/unload")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":30,\"tareWeight\":10,\"evidenceAttachmentIds\":[" + ticket + "]}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_unload_evidence WHERE route_id = 2 AND attachment_id = ?", Integer.class, ticket)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/routes/2/weight-adjustments")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":32,\"tareWeight\":10,\"reason\":\"复核\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT attachment_ids FROM route_weight_version WHERE route_id = 2 AND version_no = 1", String.class))
                .contains(String.valueOf(ticket));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_unload_evidence WHERE attachment_id = ?", Integer.class, ticket)).isEqualTo(1);
    }

    @Test
    void onlyCurrentDriverCanUploadAndUnrelatedDriverCannotDownloadTicket() throws Exception {
        mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "ticket.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer " + financeToken)
                        .param("ownerType", "ROUTE").param("ownerId", "2")
                        .param("purpose", "ROUTE_UNLOAD_WEIGHT_TICKET"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "ticket.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer " + driverToken)
                        .param("ownerType", "ROUTE").param("ownerId", "1")
                        .param("purpose", "ROUTE_UNLOAD_WEIGHT_TICKET"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "ticket.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer " + driverToken)
                        .param("ownerType", "ROUTE").param("ownerId", "2")
                        .param("purpose", "ROUTE_UNLOAD_WEIGHT_TICKET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("ROUTE_UNLOAD_WEIGHT_TICKET"));
        long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_attachment", Long.class);
        String outsider = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                999L, "other", 2L, "DRIVER", "其他司机", Set.of("route:list", "route:unload")));
        mockMvc.perform(get("/api/v1/attachments/" + id).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/attachments/1").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/attachments?ownerType=ROUTE&ownerId=1&purpose=WEIGHT_ADJUST")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/attachments?ownerType=ROUTE&ownerId=1&purpose=WEIGHT_ADJUST")
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @BeforeEach
    void setUp() {
        List.of(
                "settlement_diff", "settlement_detail", "settlement_version", "settlement_month",
                "route_unload_evidence", "file_attachment", "expense_entry", "route_weight_version", "route_status_history", "route_task",
                "vehicle_trailer_current", "driver_vehicle_current", "vehicle", "driver", "product", "customer"
        ).forEach(table -> jdbcTemplate.update("DELETE FROM " + table));

        jdbcTemplate.update("INSERT INTO customer (id, customer_name, status) VALUES (1, '客户A', 1)");
        jdbcTemplate.update("INSERT INTO product (id, product_name, status) VALUES (1, '煤炭', 1)");
        jdbcTemplate.update("INSERT INTO driver (id, user_id, driver_type, name, phone, status) VALUES (1, 101, 'INTERNAL', 'Driver A', '13800000001', 1)");
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, load_standard_min_load, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, NULL, 1),
                       (2, '京A10002', 1, 'GAS', 30.000, 'FIXED', NULL, 15.000, 1)
                """);
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (1, 1, CURRENT_TIMESTAMP, 1)");
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price,
                                        assigned_driver_id, status, departure_driver_id, departure_vehicle_id,
                                        departure_time, unload_time, effective_weight_version_id, created_by)
                VALUES (1, 'R1', 'r1', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 10.0000, 1, 'COMPLETED', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1),
                       (2, 'R2', 'r2', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'C', 10.0000, 1, 'IN_TRANSIT', 1, 1, CURRENT_TIMESTAMP, NULL, NULL, 1),
                       (3, 'R3', 'r3', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'D', 10.0000, 1, 'IN_TRANSIT', 1, 2, CURRENT_TIMESTAMP, NULL, NULL, 1),
                       (4, 'R4', 'r4', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'E', 10.0000, 1, 'IN_TRANSIT', 1, 1, CURRENT_TIMESTAMP, NULL, NULL, 1)
                """);
        RouteParticipantFixture.backfill(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO route_weight_version (id, route_id, version_no, gross_weight, tare_weight, net_weight,
                                                  load_standard_threshold, load_standard_met, source_type, operator_id, reason)
                VALUES (1, 1, 1, 30.000, 10.000, 20.000, 27.000, 0, 'UNLOAD', 101, NULL)
                """);
        jdbcTemplate.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key, original_filename,
                                             content_type, file_size, uploaded_by)
                VALUES (1, 'ROUTE', 1, 'WEIGHT_ADJUST', 'route/1/adjust.jpg', 'adjust.jpg', 'image/jpeg', 100, 1)
                """);

        financeToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                1L, "finance", 1L, "FINANCE", "财务", Set.of(
                "route:list", "route:weight:adjust", "route:unload", "report:profit", "report:dashboard",
                "settlement:generate", "settlement:view")));
        driverToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                101L, "13800000001", 2L, "DRIVER", "司机", Set.of("route:list", "route:unload", "route:weight:adjust")));
    }

    @Test
    void grossWeightLessThanOrEqualToTareWeightIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/routes/2/unload")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":10.000,\"tareWeight\":10.000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_004"));

        mockMvc.perform(post("/api/v1/routes/2/unload")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":-1.000,\"tareWeight\":10.000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_004"));
    }

    @Test
    void unqualifiedWeightStillCompletesRoute() throws Exception {
        long ticket = RouteTicketFixture.ticket(jdbcTemplate, 2, 101);
        mockMvc.perform(post("/api/v1/routes/2/unload")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":30.000,\"tareWeight\":10.000,\"evidenceAttachmentIds\":[" + ticket + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.netWeight").value(20.000))
                .andExpect(jsonPath("$.data.loadStandardThreshold").doesNotExist())
                .andExpect(jsonPath("$.data.loadStandardMet").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT load_standard_met FROM route_weight_version WHERE route_id = 2", Integer.class)).isZero();
    }

    @Test
    void percentLoadStandardIsCalculated() throws Exception {
        long ticket = RouteTicketFixture.ticket(jdbcTemplate, 4, 101);
        mockMvc.perform(post("/api/v1/routes/4/unload")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":40.000,\"tareWeight\":10.000,\"evidenceAttachmentIds\":[" + ticket + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loadStandardThreshold").doesNotExist())
                .andExpect(jsonPath("$.data.loadStandardMet").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT load_standard_met FROM route_weight_version WHERE route_id = 4", Integer.class)).isEqualTo(1);
    }

    @Test
    void fixedLoadStandardIsCalculated() throws Exception {
        long ticket = RouteTicketFixture.ticket(jdbcTemplate, 3, 101);
        mockMvc.perform(post("/api/v1/routes/3/unload")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":25.000,\"tareWeight\":10.000,\"evidenceAttachmentIds\":[" + ticket + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loadStandardThreshold").doesNotExist())
                .andExpect(jsonPath("$.data.loadStandardMet").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT load_standard_met FROM route_weight_version WHERE route_id = 3", Integer.class)).isEqualTo(1);
    }

    @Test
    void driverCannotAdjustWeightAfterUnloadEvenIfPermissionIsMisconfigured() throws Exception {
        mockMvc.perform(post("/api/v1/routes/1/weight-adjustments")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":35.000,\"tareWeight\":10.000,\"reason\":\"fix\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void financeAdjustmentCreatesNewVersionAndKeepsOriginalDriverWeight() throws Exception {
        mockMvc.perform(post("/api/v1/routes/1/weight-adjustments")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":35.000,\"tareWeight\":10.000,\"reason\":\"磅单纠错\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netWeight").value(25.000));

        List<BigDecimal> weights = jdbcTemplate.queryForList("""
                SELECT net_weight FROM route_weight_version WHERE route_id = 1 ORDER BY version_no
                """, BigDecimal.class);
        assertThat(weights).containsExactly(new BigDecimal("20.000"), new BigDecimal("25.000"));
        assertThat(jdbcTemplate.queryForObject("SELECT version_no FROM route_weight_version WHERE id = (SELECT effective_weight_version_id FROM route_task WHERE id = 1)", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void adjustmentRecalculatesIncomeAndRealtimeProfit() throws Exception {
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].incomeAmount").value(200.0))
                .andExpect(jsonPath("$.data[0].profitAmount").value(200.0));

        mockMvc.perform(post("/api/v1/routes/1/weight-adjustments")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":35.000,\"tareWeight\":10.000,\"reason\":\"磅单纠错\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].incomeAmount").value(250.0))
                .andExpect(jsonPath("$.data[0].profitAmount").value(250.0));
        mockMvc.perform(get("/api/v1/reports/dashboard").header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProfit").value(250.0));
    }

    @Test
    void generatedMonthlySettlementSnapshotDoesNotChangeAfterAdjustment() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/2026-09/versions").header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalIncome").value(200.0));
        Long versionId = jdbcTemplate.queryForObject("SELECT id FROM settlement_version", Long.class);

        mockMvc.perform(post("/api/v1/routes/1/weight-adjustments")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":35.000,\"tareWeight\":10.000,\"reason\":\"磅单纠错\"}"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT total_income FROM settlement_version WHERE id = ?", BigDecimal.class, versionId))
                .isEqualByComparingTo("200.00");
        mockMvc.perform(get("/api/v1/settlements/versions/%d/details".formatted(versionId))
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].incomeAmount").value(200.0));
    }

    @Test
    void adjustmentAttachmentIsRecordedInWeightHistory() throws Exception {
        mockMvc.perform(post("/api/v1/routes/1/weight-adjustments")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":35.000,\"tareWeight\":10.000,\"reason\":\"磅单纠错\",\"attachmentIds\":[1]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/routes/1/weight-versions").header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].versionNo").value(2))
                .andExpect(jsonPath("$.data[1].reason").value("磅单纠错"))
                .andExpect(jsonPath("$.data[1].attachmentIds[0]").value(1));
    }

    @Test
    void weightHistoryShowsBeforeAndAfterAdjustments() throws Exception {
        mockMvc.perform(post("/api/v1/routes/1/weight-adjustments")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":35.000,\"tareWeight\":10.000,\"reason\":\"磅单纠错\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/routes/1/weight-versions").header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].versionNo").value(1))
                .andExpect(jsonPath("$.data[0].grossWeight").value(30.000))
                .andExpect(jsonPath("$.data[0].netWeight").value(20.000))
                .andExpect(jsonPath("$.data[0].operatorId").value(101))
                .andExpect(jsonPath("$.data[1].versionNo").value(2))
                .andExpect(jsonPath("$.data[1].grossWeight").value(35.000))
                .andExpect(jsonPath("$.data[1].netWeight").value(25.000))
                .andExpect(jsonPath("$.data[1].operatorId").value(1))
                .andExpect(jsonPath("$.data[1].reason").value("磅单纠错"));
    }

    @Test
    void concurrentAdjustmentsDoNotCreateDuplicateVersionNo() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> task = () -> {
            ready.countDown();
            start.await();
            routeService.adjustWeight(1, new WeightAdjustmentRequest(
                    new BigDecimal("36.000"), new BigDecimal("10.000"), "并发纠错", List.of()), 1);
            return null;
        };
        List<Future<Void>> futures = new ArrayList<>();
        futures.add(executor.submit(task));
        futures.add(executor.submit(task));
        ready.await();
        start.countDown();
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdownNow();

        List<Integer> versions = jdbcTemplate.queryForList("""
                SELECT version_no FROM route_weight_version WHERE route_id = 1 ORDER BY version_no
                """, Integer.class);
        assertThat(versions).containsExactly(1, 2, 3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT version_no) FROM route_weight_version WHERE route_id = 1
                """, Integer.class)).isEqualTo(3);
    }
}
