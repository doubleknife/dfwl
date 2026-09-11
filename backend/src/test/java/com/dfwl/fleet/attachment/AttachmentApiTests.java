package com.dfwl.fleet.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attachmentdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql",
        "fleet.storage.local-root=${java.io.tmpdir}/fleet-attachment-tests"
})
@AutoConfigureMockMvc
class AttachmentApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    @Autowired
    private ObjectMapper objectMapper;

    private String importToken;
    private String noPermissionToken;
    private String approvalToken;
    private String tireToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM file_attachment");
        importToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                1L, "13800000001", 1L, "importer", "导入员", Set.of("import:preview", "import:history")));
        noPermissionToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                2L, "13800000002", 2L, "viewer", "查看员", Set.of()));
        approvalToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                3L, "13800000003", 3L, "applicant", "申请人", Set.of("approval:create", "approval:history:view")));
        tireToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                4L, "13800000004", 4L, "driver", "司机", Set.of("tire:request")));
    }

    @Test
    void uploadListAndReadOriginalFile() throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "routes.csv", "text/csv", "a,b\n1,2".getBytes(StandardCharsets.UTF_8)))
                        .param("ownerType", "IMPORT")
                        .param("ownerId", "0")
                        .param("purpose", "IMPORT_FILE")
                        .header("Authorization", "Bearer " + importToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerType").value("IMPORT"))
                .andExpect(jsonPath("$.data.purpose").value("IMPORT_FILE"))
                .andExpect(jsonPath("$.data.originalFilename").value("routes.csv"))
                .andExpect(jsonPath("$.data.contentType").value("text/csv"))
                .andExpect(jsonPath("$.data.fileSize").value(7))
                .andExpect(jsonPath("$.data.fileHash").isNotEmpty())
                .andExpect(jsonPath("$.data.storageKey").isNotEmpty())
                .andExpect(jsonPath("$.data.uploadedBy").value(1))
                .andReturn();

        long id = readId(uploaded);
        mockMvc.perform(get("/api/v1/attachments")
                        .param("ownerType", "IMPORT")
                        .param("ownerId", "0")
                        .param("purpose", "IMPORT_FILE")
                        .header("Authorization", "Bearer " + importToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id));

        MvcResult downloaded = mockMvc.perform(get("/api/v1/attachments/{id}", id)
                        .header("Authorization", "Bearer " + importToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().exists("X-File-Sha256"))
                .andReturn();
        assertThat(downloaded.getResponse().getContentAsString()).isEqualTo("a,b\n1,2");
    }

    @Test
    void illegalPurposeAndUnsupportedContentTypeAreRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "bad.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8)))
                        .param("ownerType", "IMPORT")
                        .param("ownerId", "0")
                        .param("purpose", "UNKNOWN")
                        .header("Authorization", "Bearer " + importToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_002"));

        mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv", "x".getBytes(StandardCharsets.UTF_8)))
                        .param("ownerType", "TIRE")
                        .param("ownerId", "0")
                        .param("purpose", "TIRE_OCR")
                        .header("Authorization", "Bearer " + tireToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_003"));

        mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "weight.jpg", "image/jpeg", new byte[]{1}))
                        .param("ownerType", "ROUTE")
                        .param("ownerId", "0")
                        .param("purpose", "WEIGHT_ADJUST")
                        .header("Authorization", "Bearer " + tokenAuthenticationService.issueToken(new AuthenticatedUser(
                                5L, "13800000005", 5L, "adjuster", "调磅员", Set.of("route:weight:adjust")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_004"));
    }

    @Test
    void readRequiresBusinessPermission() throws Exception {
        long id = uploadImportFile();

        mockMvc.perform(get("/api/v1/attachments/{id}", id)
                        .header("Authorization", "Bearer " + noPermissionToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void duplicateUploadReturnsExistingMetadata() throws Exception {
        long firstId = uploadImportFile();
        long secondId = uploadImportFile();

        assertThat(secondId).isEqualTo(firstId);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_attachment", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void approvalAttachmentCannotBeDeletedThroughOrdinaryEndpoint() throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "approval.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .param("ownerType", "APPROVAL")
                        .param("ownerId", "0")
                        .param("purpose", "APPROVAL_APPLICATION")
                        .header("Authorization", "Bearer " + approvalToken))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(delete("/api/v1/attachments/{id}", readId(uploaded))
                        .header("Authorization", "Bearer " + approvalToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_005"));
    }

    private long uploadImportFile() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "routes.csv", "text/csv", "a,b\n1,2".getBytes(StandardCharsets.UTF_8)))
                        .param("ownerType", "IMPORT")
                        .param("ownerId", "0")
                        .param("purpose", "IMPORT_FILE")
                        .header("Authorization", "Bearer " + importToken))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return json.path("data").path("id").asLong();
    }
}
