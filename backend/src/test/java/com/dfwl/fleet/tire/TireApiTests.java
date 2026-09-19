package com.dfwl.fleet.tire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.tire.policy.TireAccessPolicy;
import org.springframework.security.access.AccessDeniedException;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.dfwl.fleet.tire.ocr.OcrProvider;
import com.dfwl.fleet.tire.ocr.OcrProviderException;
import com.dfwl.fleet.tire.ocr.OcrProviderResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tiredb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql",
        "fleet.storage.local-root=${java.io.tmpdir}/fleet-ocr-tests"
})
@AutoConfigureMockMvc
class TireApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeOcrProvider fakeOcrProvider;

    @Autowired
    private TireAccessPolicy tireAccess;

    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tire_claim");
        jdbcTemplate.update("DELETE FROM tire_request_item");
        jdbcTemplate.update("DELETE FROM tire_request");
        jdbcTemplate.update("DELETE FROM ocr_record");
        jdbcTemplate.update("DELETE FROM file_attachment");
        jdbcTemplate.update("DELETE FROM tire");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");
        fakeOcrProvider.reset();

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
                VALUES (1, 'H621769799', 'BC001', 'IN_STOCK', 'MANUAL')
                """);
        jdbcTemplate.update("""
                INSERT INTO tire (id, tire_no, barcode, status, data_source)
                VALUES (2, 'ABC123456', 'BC002', 'IN_STOCK', 'MANUAL')
                """);
        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "1380", 1L, "admin", "管理员", Set.of(
                "tire:list", "tire:request", "tire:approval:view", "approval:create")));
        otherToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(2L, "1390", 1L, "driver-b", "其他司机", Set.of(
                "tire:request")));
    }

    @Test
    void ocrReadsAttachmentBytesAndPassesBase64ToProvider() throws Exception {
        long attachmentId = uploadTireOcrImage(token, "img".getBytes(StandardCharsets.UTF_8));

        recognize(attachmentId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrProvider").value("FAKE_TENCENT_GENERAL_ACCURATE"))
                .andExpect(jsonPath("$.data.providerRequestId").value("fake-request-1"))
                .andExpect(jsonPath("$.data.candidates[0].candidate").value("H621769799"));

        assertThat(fakeOcrProvider.lastImageBase64)
                .isEqualTo(Base64.getEncoder().encodeToString("img".getBytes(StandardCharsets.UTF_8)));
        assertThat(fakeOcrProvider.lastContentType).isEqualTo("image/jpeg");
    }

    @Test
    void extractsSingleAndHStyleCandidate() throws Exception {
        fakeOcrProvider.detections = List.of(new OcrProviderResult.DetectedText(" H621769799 ", 98D));

        recognize(uploadTireOcrImage(token, new byte[]{1}), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateText").value("H621769799"))
                .andExpect(jsonPath("$.data.candidates[0].inventoryMatched").value(true));
    }

    @Test
    void multipleCandidatesPreferExactInventoryMatchThenConfidence() throws Exception {
        fakeOcrProvider.detections = List.of(
                new OcrProviderResult.DetectedText("ZZ999999", 99D),
                new OcrProviderResult.DetectedText("ABC123456", 70D));

        recognize(uploadTireOcrImage(token, new byte[]{1}), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates[0].candidate").value("ABC123456"))
                .andExpect(jsonPath("$.data.candidates[0].inventoryMatched").value(true))
                .andExpect(jsonPath("$.data.candidates[1].candidate").value("ZZ999999"));
    }

    @Test
    void noResultIsRecordedAndCanBeManuallyConfirmed() throws Exception {
        fakeOcrProvider.detections = List.of();
        long ocrId = readId(recognize(uploadTireOcrImage(token, new byte[]{1}), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("NO_CANDIDATE"))
                .andExpect(jsonPath("$.data.candidates.length()").value(0))
                .andReturn());

        confirm(ocrId, "H621769799")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedText").value("H621769799"));
    }

    @Test
    void providerTimeoutAndApiErrorAreRecordedAndCanBeManuallyConfirmed() throws Exception {
        fakeOcrProvider.error = new OcrProviderException("RequestTimeout", "timeout");
        long timeoutRecord = readId(recognize(uploadTireOcrImage(token, new byte[]{1}), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("RequestTimeout"))
                .andReturn());
        confirm(timeoutRecord, "H621769799").andExpect(status().isOk());

        fakeOcrProvider.error = new OcrProviderException("AuthFailure", "api error");
        recognize(uploadTireOcrImage(token, new byte[]{2}), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCode").value("AuthFailure"));
    }

    @Test
    void unauthorizedOrWrongPurposeAttachmentIsRejected() throws Exception {
        long attachmentId = uploadTireOcrImage(token, new byte[]{1});
        recognize(attachmentId, otherToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_004"));

        long wrongPurpose = uploadWrongPurposeImage(token);
        recognize(wrongPurpose, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_004"));
    }

    @Test
    void responseAndRawResultDoNotExposeSecretsAndRawResultIsPersisted() throws Exception {
        fakeOcrProvider.rawJson = "{\"RequestId\":\"fake-request-1\",\"TextDetections\":[{\"DetectedText\":\"H621769799\",\"Confidence\":99}]}";
        long ocrId = readId(recognize(uploadTireOcrImage(token, new byte[]{1}), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rawResultJson").value(fakeOcrProvider.rawJson))
                .andReturn());

        String raw = jdbcTemplate.queryForObject("SELECT raw_result_json FROM ocr_record WHERE id = ?", String.class, ocrId);
        assertThat(raw).isEqualTo(fakeOcrProvider.rawJson);
        String response = objectMapper.writeValueAsString(
                objectMapper.readTree(raw));
        assertThat(response).doesNotContain("secret").doesNotContain("SECRET");
    }

    @Test
    void confirmedOcrCanContinueExistingTireApprovalFlow() throws Exception {
        long ocrId = readId(recognize(uploadTireOcrImage(token, new byte[]{1}), token).andReturn());
        confirm(ocrId, "H621769799").andExpect(status().isOk());

        long requestId = readId(mockMvc.perform(post("/api/v1/tire-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverId":1,"vehicleId":1,
                                 "items":[{"tireId":1,"confirmedTireNo":"H621769799","ocrRecordId":%d}]}
                                """.formatted(ocrId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].ocrRecordId").value(ocrId)).andReturn());

        mockMvc.perform(get("/api/v1/tire-requests/" + requestId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].confirmedTireNo").value("H621769799"));
    }

    @Test
    void requestedTireCannotBeRequestedAgain() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO ocr_record (id, attachment_id, ocr_provider, raw_result_json, recognized_text,
                                        candidate_text, ocr_status, confirmed_text)
                VALUES (1, 1, 'FAKE', '{}', 'H621769799', 'H621769799', 'SUCCESS', 'H621769799')
                """);
        jdbcTemplate.update("INSERT INTO tire_request (id, driver_id, vehicle_id, status) VALUES (1, 1, 1, 'PENDING')");
        jdbcTemplate.update("INSERT INTO tire_request_item (request_id, tire_id, confirmed_tire_no, ocr_record_id) VALUES (1, 1, 'H621769799', 1)");

        mockMvc.perform(post("/api/v1/tire-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverId":1,"vehicleId":1,
                                 "items":[{"tireId":1,"confirmedTireNo":"H621769799","ocrRecordId":1}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIRE_001"));
    }


    @ParameterizedTest
    @CsvSource({"1,1,true,true", "2,1,true,false", "1,2,true,false", "1,1,false,false"})
    void driverRequestRequiresOwnDriverAndCurrentVehicle(long requestDriver, long vehicle, boolean bound, boolean allowed) throws Exception {
        AuthenticatedUser user = tireDriver();
        if (bound) {
            jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (1, 1, CURRENT_TIMESTAMP, 1)");
        }
        if (!allowed) {
            assertThatThrownBy(() -> tireAccess.ensureDriverCanUseTireRequest(user, requestDriver, vehicle))
                    .isExactlyInstanceOf(AccessDeniedException.class).hasMessage("tire request is outside current driver scope");
        }
        String auth = tokenAuthenticationService.issueToken(user);
        var result = mockMvc.perform(post("/api/v1/tire-requests")
                .header("Authorization", "Bearer " + auth).header("X-Request-Id", "r43-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":%d,\"vehicleId\":%d,\"items\":[{\"tireId\":1,\"confirmedTireNo\":\"H621769799\"}]}".formatted(requestDriver, vehicle)));
        if (allowed) {
            result.andExpect(status().isOk());
        } else {
            result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_003"))
                    .andExpect(jsonPath("$.message").value("无权限"))
                    .andExpect(jsonPath("$.requestId").value("r43-request"));
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tire_request", Integer.class)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OUTSOURCED", " outsource ", "EXTERNAL", " 外协 "})
    void outsourcedTypesAreRejectedBeforeRequestOcrAndConfirmBusiness(String driverType) throws Exception {
        jdbcTemplate.update("UPDATE driver SET driver_type = ? WHERE id = 1", driverType);
        AuthenticatedUser user = tireDriver();
        String auth = tokenAuthenticationService.issueToken(user);
        assertThatThrownBy(() -> tireAccess.ensureDriverCanUseTireRequest(user, 999, 999))
                .isExactlyInstanceOf(AccessDeniedException.class).hasMessage("outsourced driver cannot request tires");
        assertThatThrownBy(() -> tireAccess.ensureDriverCanUseTireOcr(user))
                .isExactlyInstanceOf(AccessDeniedException.class).hasMessage("outsourced driver is not allowed");
        String[] urls = {"/api/v1/tire-requests", "/api/v1/ocr/tire-number", "/api/v1/ocr/999/confirm"};
        String[] bodies = {"{\"driverId\":999,\"vehicleId\":999,\"items\":[{\"tireId\":999,\"confirmedTireNo\":\"missing\"}]}",
                "{\"attachmentId\":999}", "{\"confirmedText\":\"missing\"}"};
        for (int i = 0; i < urls.length; i++) {
            mockMvc.perform(post(urls[i]).header("Authorization", "Bearer " + auth)
                            .header("X-Request-Id", "r43-outsourced").contentType(MediaType.APPLICATION_JSON).content(bodies[i]))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_003"))
                    .andExpect(jsonPath("$.message").value("无权限"))
                    .andExpect(jsonPath("$.requestId").value("r43-outsourced"));
        }
        assertThat(fakeOcrProvider.lastImageBase64).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ocr_record", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tire_request", Integer.class)).isZero();
    }

    @Test
    void internalDriverCanRecognizeAndConfirmWithoutVehicleBinding() throws Exception {
        token = tokenAuthenticationService.issueToken(tireDriver());
        long attachmentId = uploadTireOcrImage(token, new byte[] {1, 2, 3});
        long ocrId = readId(recognize(attachmentId, token).andExpect(status().isOk()).andReturn());
        confirm(ocrId, "H621769799").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedText").value("H621769799"));
        assertThat(fakeOcrProvider.lastImageBase64).isEqualTo("AQID");
    }

    @Test
    void tireAuthorizationPreservesNonDriverBypassAndMissingIdentityMessage() {
        for (AuthenticatedUser user : new AuthenticatedUser[] {null,
                new AuthenticatedUser(99L, "admin", 1L, "ADMIN", "admin", Set.of())}) {
            tireAccess.ensureDriverCanUseTireRequest(user, 999, 999);
            tireAccess.ensureDriverCanUseTireOcr(user);
        }
        jdbcTemplate.update("UPDATE driver SET status = 0 WHERE id = 1");
        assertThatThrownBy(() -> tireAccess.ensureDriverCanUseTireRequest(tireDriver(), 1, 1))
                .isExactlyInstanceOf(AccessDeniedException.class).hasMessage("driver binding required");
        assertThatThrownBy(() -> tireAccess.ensureDriverCanUseTireOcr(tireDriver()))
                .isExactlyInstanceOf(AccessDeniedException.class).hasMessage("driver binding required");
    }

    private AuthenticatedUser tireDriver() {
        return new AuthenticatedUser(101L, "13800000001", 2L, "DRIVER", "driver", Set.of("tire:request"));
    }

    private org.springframework.test.web.servlet.ResultActions recognize(long attachmentId, String authToken) throws Exception {
        return mockMvc.perform(post("/api/v1/ocr/tire-number")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentId\":%d}".formatted(attachmentId)));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(long ocrId, String confirmedText) throws Exception {
        return mockMvc.perform(post("/api/v1/ocr/%d/confirm".formatted(ocrId))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedText\":\"%s\"}".formatted(confirmedText)));
    }

    private long uploadTireOcrImage(String authToken, byte[] bytes) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "tire.jpg", "image/jpeg", bytes))
                        .param("ownerType", "TIRE_OCR")
                        .param("ownerId", "0")
                        .param("purpose", "TIRE_OCR")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private long uploadWrongPurposeImage(String authToken) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1}))
                        .param("ownerType", "APPROVAL_UPLOAD")
                        .param("ownerId", "1")
                        .param("purpose", "APPROVAL_APPLICATION")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return json.path("data").path("id").asLong();
    }

    @TestConfiguration
    static class OcrTestConfig {
        @Bean
        @Primary
        FakeOcrProvider fakeOcrProvider() {
            return new FakeOcrProvider();
        }
    }

    static class FakeOcrProvider implements OcrProvider {
        String lastImageBase64;
        String lastContentType;
        String rawJson = "{\"RequestId\":\"fake-request-1\",\"TextDetections\":[{\"DetectedText\":\"H621769799\",\"Confidence\":99}]}";
        List<OcrProviderResult.DetectedText> detections = List.of(new OcrProviderResult.DetectedText("H621769799", 99D));
        OcrProviderException error;

        @Override
        public OcrProviderResult recognize(String imageBase64, String contentType) {
            this.lastImageBase64 = imageBase64;
            this.lastContentType = contentType;
            if (error != null) {
                OcrProviderException current = error;
                error = null;
                throw current;
            }
            return new OcrProviderResult("FAKE_TENCENT_GENERAL_ACCURATE", "fake-request-1", rawJson, detections);
        }

        void reset() {
            lastImageBase64 = null;
            lastContentType = null;
            rawJson = "{\"RequestId\":\"fake-request-1\",\"TextDetections\":[{\"DetectedText\":\"H621769799\",\"Confidence\":99}]}";
            detections = List.of(new OcrProviderResult.DetectedText("H621769799", 99D));
            error = null;
        }
    }
}
