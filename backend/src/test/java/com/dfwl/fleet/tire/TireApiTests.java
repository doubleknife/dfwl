package com.dfwl.fleet.tire;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tiredb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class TireApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tire_claim");
        jdbcTemplate.update("DELETE FROM tire_request_item");
        jdbcTemplate.update("DELETE FROM tire_request");
        jdbcTemplate.update("DELETE FROM ocr_record");
        jdbcTemplate.update("DELETE FROM file_attachment");
        jdbcTemplate.update("DELETE FROM tire");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");

        jdbcTemplate.update("""
                INSERT INTO driver (id, name, phone, id_card_no, driver_type, status)
                VALUES (1, 'Driver A', '13800000001', '110101199001010011', 'INTERNAL', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO tire (id, tire_no, barcode, status, data_source)
                VALUES (1, 'TIRE-001', 'BC001', 'IN_STOCK', 'MANUAL')
                """);
        jdbcTemplate.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key, original_filename,
                                             content_type, file_size, uploaded_by)
                VALUES (1, 'TIRE_OCR', 0, 'ORIGINAL_IMAGE', 'tire/1.jpg', '1.jpg', 'image/jpeg', 100, 1)
                """);
        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "1380", 1L, "admin", "管理员", Set.of(
                "tire:list", "tire:request", "tire:approval:view")));
    }

    @Test
    void ocrConfirmAndTireRequestPreserveConfirmedTireNo() throws Exception {
        mockMvc.perform(post("/api/v1/ocr/tire-number")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attachmentId":1,"ocrProvider":"LOCAL","rawResultJson":"{\\"text\\":\\"TIRE-001\\"}",
                                 "recognizedText":"TIRE-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.rawResultJson").value("{\"text\":\"TIRE-001\"}"));

        mockMvc.perform(post("/api/v1/ocr/1/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmedText":"TIRE-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedText").value("TIRE-001"))
                .andExpect(jsonPath("$.data.confirmedBy").value(1));

        mockMvc.perform(post("/api/v1/tire-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverId":1,"vehicleId":1,
                                 "items":[{"tireId":1,"confirmedTireNo":"TIRE-001","ocrRecordId":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].confirmedTireNo").value("TIRE-001"));

        mockMvc.perform(get("/api/v1/tire-requests/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].ocrRecordId").value(1));
    }

    @Test
    void requestedTireCannotBeRequestedAgain() throws Exception {
        jdbcTemplate.update("INSERT INTO ocr_record (id, attachment_id, recognized_text, confirmed_text) VALUES (1, 1, 'TIRE-001', 'TIRE-001')");
        jdbcTemplate.update("INSERT INTO tire_request (id, driver_id, vehicle_id, status) VALUES (1, 1, 1, 'PENDING')");
        jdbcTemplate.update("INSERT INTO tire_request_item (request_id, tire_id, confirmed_tire_no, ocr_record_id) VALUES (1, 1, 'TIRE-001', 1)");

        mockMvc.perform(post("/api/v1/tire-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverId":1,"vehicleId":1,
                                 "items":[{"tireId":1,"confirmedTireNo":"TIRE-001","ocrRecordId":1}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIRE_001"));
    }
}
