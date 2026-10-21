package com.dfwl.fleet.attachment;

import static com.dfwl.fleet.security.TestFunctionGrants.grants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3-attachment-api;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql",
        "fleet.storage.local-root=${java.io.tmpdir}/fleet-v3-attachment-tests"
})
@AutoConfigureMockMvc
class AttachmentApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenAuthenticationService tokens;
    @Autowired ObjectMapper json;

    @BeforeEach
    void taskFixture() {
        long adminRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='ADMIN'", Long.class);
        long driverRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='DRIVER'", Long.class);
        jdbc.update("MERGE INTO sys_user(id,phone,password_hash,role_id,status) KEY(id) VALUES(1,'13900000001','test-only',?,1)", adminRole);
        jdbc.update("MERGE INTO sys_user(id,phone,password_hash,role_id,status) KEY(id) VALUES(501,'13911111111','test-only',?,1)", driverRole);
        jdbc.update("MERGE INTO sys_user(id,phone,password_hash,role_id,status) KEY(id) VALUES(502,'13922222222','test-only',?,1)", driverRole);
        jdbc.update("DELETE FROM file_attachment");
        jdbc.update("DELETE FROM transport_participant");
        jdbc.update("DELETE FROM transport_task");
        jdbc.update("DELETE FROM route_template");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM driver");
        jdbc.update("DELETE FROM vehicle");
        jdbc.update("INSERT INTO product (id, name, enabled) VALUES (1, '货物', 1)");
        jdbc.update("""
                INSERT INTO route_template (id, stable_code, name, customer_name, product_id,
                    loading_place, unloading_place, default_unit_price, default_information_fee,
                    default_driver_salary, created_by, updated_by)
                VALUES (1, 'ATT-V3', '附件任务模板', '客户', 1, 'A', 'B', 1, 0, 0, 1, 1)
                """);
        jdbc.update("""
                INSERT INTO transport_task (id, task_no, template_id, template_code_snapshot,
                    template_name_snapshot, customer_name_snapshot, product_name_snapshot,
                    loading_place_snapshot, unloading_place_snapshot, actual_unit_price,
                    actual_information_fee, business_date, status, created_by)
                VALUES (1, 'ATT-V3-TASK', 1, 'ATT-V3', '附件任务模板', '客户', '货物',
                    'A', 'B', 1, 0, '2026-09-30', 'IN_TRANSIT', 1)
                """);
        jdbc.update("INSERT INTO vehicle (id, plate_no, vehicle_license_no, insurance_complete, energy_type, max_load, load_standard_type, load_standard_percent, status) VALUES (1, '云A001', 'TEST-VL-001', 1, 'ELECTRIC', 30, 'PERCENT', 0.9, 1)");
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone, id_card_no, driving_license_no, status) VALUES (1, 501, 'INTERNAL', '司机', '13911111111', '110105199001010010', 'TEST-DL-001', 1)");
        jdbc.update("""
                INSERT INTO transport_participant (id, task_id, sequence_no, driver_id,
                    driver_type_snapshot, company_vehicle_id, vehicle_plate_snapshot,
                    source_type, status, is_current, salary_amount, created_by)
                VALUES (1, 1, 1, 1, 'INTERNAL', 1, '云A001', 'INITIAL', 'IN_TRANSIT', 1, 0, 1)
                """);
        jdbc.update("UPDATE transport_task SET current_participant_id = 1 WHERE id = 1");
    }

    @Test
    void currentDriverUploadsOriginalTicketAndOnlyAuthorizedPeopleCanReadIt() throws Exception {
        String driver = token(501, "13911111111", "DRIVER", "BUTTON_MINI_ROUTES_UNLOAD", "PAGE_MINI_ROUTES");
        var upload = mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "ticket.png", "image/png", new byte[]{1, 2, 3}))
                        .param("ownerType", "TASK").param("ownerId", "1")
                        .param("purpose", "ROUTE_UNLOAD_WEIGHT_TICKET")
                        .header("Authorization", "Bearer " + driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("ROUTE_UNLOAD_WEIGHT_TICKET"))
                .andReturn();
        long id = json.readTree(upload.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
        assertThat(id).isPositive();
        mvc.perform(get("/api/v1/attachments/{id}", id).header("Authorization", "Bearer " + driver))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/attachments/{id}", id)
                        .header("Authorization", "Bearer " + token(502, "13922222222", "DRIVER", "PAGE_ROUTES")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/attachments/{id}", id).header("Authorization", "Bearer " + driver))
                .andExpect(status().isConflict());
    }

    @Test
    void authorizedWeightEvidenceIsIndependentAndV3ApprovalEvidenceIsActive() throws Exception {
        String admin = token(1, "13900000001", "ADMIN", "BUTTON_ROUTES_WEIGHT_ADJUST", "PAGE_ROUTES", "BUTTON_EXPENSE_APPLY_SUBMIT");
        for (int i = 0; i < 2; i++) {
            mvc.perform(multipart("/api/v1/attachments")
                            .file(new MockMultipartFile("file", "adjust.png", "image/png", new byte[]{1, 2, 3}))
                            .param("ownerType", "TASK").param("ownerId", "1")
                            .param("purpose", "WEIGHT_ADJUST")
                            .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT storage_key) FROM file_attachment", Integer.class))
                .isEqualTo(2);
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "approval.png", "image/png", new byte[]{1}))
                        .param("ownerType", "APPROVAL_UPLOAD").param("ownerId", "1")
                        .param("purpose", "APPROVAL_APPLICATION")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "ocr.png", "image/png", new byte[]{1}))
                        .param("ownerType", "TIRE_OCR").param("ownerId", "1")
                        .param("purpose", "TIRE_OCR")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void miniApprovalImagesRequireUploaderAndMatchingApplicationOrActionPermission() throws Exception {
        String applicant = token(501, "13911111111", "DRIVER", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT");
        var application = mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1, 2}))
                        .param("ownerType", "APPROVAL_UPLOAD").param("ownerId", "501")
                        .param("purpose", "APPROVAL_APPLICATION")
                        .header("Authorization", "Bearer " + applicant))
                .andExpect(status().isOk()).andReturn();
        long applicationId = json.readTree(application.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
        mvc.perform(get("/api/v1/attachments/{id}", applicationId)
                        .header("Authorization", "Bearer " + applicant)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/attachments/{id}", applicationId)
                        .header("Authorization", "Bearer " + token(502, "13922222222", "DRIVER", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "wrong-owner.png", "image/png", new byte[]{1}))
                        .param("ownerType", "APPROVAL_UPLOAD").param("ownerId", "502")
                        .param("purpose", "APPROVAL_APPLICATION")
                        .header("Authorization", "Bearer " + applicant))
                .andExpect(status().isConflict());
        String approver = token(1, "13900000001", "ADMIN", "BUTTON_MINI_APPROVALS_APPROVE");
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "action.png", "image/png", new byte[]{3}))
                        .param("ownerType", "APPROVAL_UPLOAD").param("ownerId", "1")
                        .param("purpose", "APPROVAL_ACTION")
                        .header("Authorization", "Bearer " + approver))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "action-denied.png", "image/png", new byte[]{4}))
                        .param("ownerType", "APPROVAL_UPLOAD").param("ownerId", "501")
                        .param("purpose", "APPROVAL_ACTION")
                        .header("Authorization", "Bearer " + applicant))
                .andExpect(status().isForbidden());
    }

    @Test
    void tireRequestPermissionDoesNotLetOutsourcedDriverUploadAnOcrPhoto() throws Exception {
        jdbc.update("INSERT INTO driver_vehicle_current(driver_id,vehicle_id,bound_at,bound_by) "
                + "VALUES(1,1,CURRENT_TIMESTAMP,1)");
        String driver = token(501, "13911111111", "DRIVER", "BUTTON_MINI_TIRE_REQUEST_SUBMIT");
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "tire.png", "image/png", new byte[]{1, 2, 3}))
                        .param("ownerType", "TIRE_OCR").param("ownerId", "0")
                        .param("purpose", "TIRE_OCR")
                        .header("Authorization", "Bearer " + driver))
                .andExpect(status().isOk());
        jdbc.update("UPDATE driver SET driver_type='OUTSOURCED' WHERE id=1");
        mvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "tire-again.png", "image/png", new byte[]{4, 5, 6}))
                        .param("ownerType", "TIRE_OCR").param("ownerId", "0")
                        .param("purpose", "TIRE_OCR")
                        .header("Authorization", "Bearer " + driver))
                .andExpect(status().isForbidden());
    }

    private String token(long id, String phone, String role, String... permissions) {
        long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, role);
        return tokens.issueToken(new AuthenticatedUser(id, phone, roleId,
                role, role, grants(permissions)));
    }
}
