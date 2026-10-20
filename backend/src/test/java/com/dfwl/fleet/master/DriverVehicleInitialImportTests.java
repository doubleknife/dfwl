package com.dfwl.fleet.master;

import static com.dfwl.fleet.security.TestFunctionGrants.grants;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.master.service.DriverVehicleInitialImportService;
import com.dfwl.fleet.master.service.MasterDataService;
import com.dfwl.fleet.master.dto.request.VehicleRequest;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:initialimportdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class DriverVehicleInitialImportTests {
    @Autowired private DriverVehicleInitialImportService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenAuthenticationService sessions;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MockMvc mvc;
    @SpyBean private MasterDataService masterData;
    private String editor;
    private String reader;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM driver_vehicle_history");
        jdbc.update("DELETE FROM driver_vehicle_current");
        jdbc.update("DELETE FROM vehicle");
        jdbc.update("DELETE FROM driver");
        jdbc.update("DELETE FROM auth_session");
        jdbc.update("DELETE FROM sys_user");
        jdbc.update("DELETE FROM sys_role");
        jdbc.update("INSERT INTO sys_role(id,role_code,role_name,status) VALUES(1,'ADMIN','管理员',1),(2,'DRIVER','司机',1)");
        jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(1,'13800000000','test-hash',1,1)");
        editor = sessions.issueToken(new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员",
                grants("PAGE_DRIVERS", "BUTTON_DRIVERS_IMPORT")));
        reader = sessions.issueToken(new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员",
                grants("PAGE_DRIVERS")));
    }

    @Test
    void templateHasTenColumnsAndFormalDropdowns() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template()))) {
            var sheet = book.getSheetAt(0);
            assertThat((int) sheet.getRow(0).getLastCellNum()).isEqualTo(10);
            for (int i = 0; i < 10; i++)
                assertThat(sheet.getRow(0).getCell(i).getStringCellValue())
                        .isEqualTo(DriverVehicleInitialImportService.HEADERS.get(i));
            assertThat(sheet.getDataValidations()).hasSize(2);
            assertThat(book.getSheetAt(1).getRow(0).getCell(0).getStringCellValue()).contains("未录入");
        }
        mvc.perform(get("/api/v1/driver-vehicle-imports/template")
                        .header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
    }

    @Test
    void previewCollectsMultipleErrorsWithoutWritingFacts() throws Exception {
        byte[] bytes = workbook(new String[]{"京A00101", "李甲", "bad", "LIC-1", "DL-1", "bad",
                "不确定", "0", "30.0001", "柴油"});
        var preview = service.preview(file(bytes));
        assertThat(preview.total()).isEqualTo(1);
        assertThat(preview.failed()).isEqualTo(1);
        assertThat(preview.rows().get(0).errors()).hasSizeGreaterThan(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vehicle", Integer.class)).isZero();
        mvc.perform(multipart("/api/v1/driver-vehicle-imports/preview").file(file(bytes))
                        .header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.rows[0].rowNo").value(2));
    }

    @Test
    void confirmationCreatesAccountVehicleAndBindingHistoryAtomically() throws Exception {
        byte[] bytes = workbook(valid("京A00102", "13810000102", "LIC-102", "DL-102", card("11010519900101102")));
        assertThat(service.preview(file(bytes)).passed()).isEqualTo(1);
        mvc.perform(multipart("/api/v1/driver-vehicle-imports/confirm").file(file(bytes))
                        .header("Authorization", "Bearer " + editor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imported").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver_vehicle_current", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver_vehicle_history", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT driver_type FROM driver WHERE phone='13810000102'", String.class))
                .isEqualTo("INTERNAL");
        assertThat(jdbc.queryForObject("SELECT load_standard_type FROM vehicle WHERE plate_no='京A00102'", String.class))
                .isEqualTo("FIXED");
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE phone='13810000102'", String.class);
        assertThat(hash).isNotEqualTo("123456");
        assertThat(encoder.matches("123456", hash)).isTrue();
        mvc.perform(multipart("/api/v1/driver-vehicle-imports/confirm").file(file(bytes))
                        .header("Authorization", "Bearer " + editor))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver_vehicle_history", Integer.class)).isEqualTo(1);
    }

    @Test
    void duplicateAccountAndInternalDuplicateRejectWholeBatch() throws Exception {
        jdbc.update("INSERT INTO sys_user(phone,password_hash,role_id,status) VALUES('13810000103','existing',2,1)");
        byte[] bytes = workbook(valid("京A00103", "13810000103", "LIC-103", "DL-103", card("11010519900101103")));
        assertThat(service.preview(file(bytes)).rows().get(0).errors()).anyMatch(issue -> issue.reason().contains("登录账号"));
        mvc.perform(multipart("/api/v1/driver-vehicle-imports/confirm").file(file(bytes))
                        .header("Authorization", "Bearer " + editor))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver", Integer.class)).isZero();
        byte[] duplicate = workbook(valid("京A00104", "13810000104", "LIC-104", "DL-104", card("11010519900101104")),
                valid("京A00104", "13810000105", "LIC-105", "DL-105", card("11010519900101105")));
        assertThat(service.preview(file(duplicate)).rows().get(1).errors())
                .anyMatch(issue -> issue.field().equals("车牌号") && issue.reason().contains("第 2 行"));
    }

    @Test
    void confirmationRechecksDatabaseAndReadOnlyUserCannotConfirm() throws Exception {
        byte[] bytes = workbook(valid("京A00106", "13810000106", "LIC-106", "DL-106", card("11010519900101106")));
        assertThat(service.preview(file(bytes)).failed()).isZero();
        jdbc.update("INSERT INTO vehicle(plate_no,vehicle_license_no,insurance_complete,energy_type,max_load,load_standard_type,load_standard_min_load,status) VALUES('京A00106','OTHER-LIC',1,'GAS',30,'FIXED',20,1)");
        mvc.perform(multipart("/api/v1/driver-vehicle-imports/confirm").file(file(bytes))
                        .header("Authorization", "Bearer " + editor))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("车牌号")));
        mvc.perform(multipart("/api/v1/driver-vehicle-imports/confirm").file(file(bytes))
                        .header("Authorization", "Bearer " + reader))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver", Integer.class)).isZero();
    }

    @Test
    void secondRowWriteFailureRollsBackFirstRowAndAccount() throws Exception {
        byte[] bytes = workbook(valid("京A00107", "13810000107", "LIC-107", "DL-107", card("11010519900101107")),
                valid("京A00108", "13810000108", "LIC-108", "DL-108", card("11010519900101108")));
        assertThat(service.preview(file(bytes)).passed()).isEqualTo(2);
        AtomicInteger calls = new AtomicInteger();
        Mockito.doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new BusinessException("DATA_002", "模拟确认期间的车辆冲突");
            return invocation.callRealMethod();
        }).when(masterData).createVehicle(Mockito.any(VehicleRequest.class));
        try {
            mvc.perform(multipart("/api/v1/driver-vehicle-imports/confirm").file(file(bytes))
                            .header("Authorization", "Bearer " + editor))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("第 3 行")));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vehicle", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver_vehicle_history", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE role_id=2", Integer.class)).isZero();
        } finally { Mockito.reset(masterData); }
    }

    @Test
    void rejectsChangedHeaderAndOverprecisionFromNumericCells() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template())); var out = new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(0).getCell(0).setCellValue("旧车牌列");
            book.write(out);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.preview(file(out.toByteArray())))
                    .isInstanceOf(BusinessException.class).hasMessage("请使用系统模板");
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template())); var out = new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(0).createCell(10).setCellValue("多余列");
            book.write(out);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.preview(file(out.toByteArray())))
                    .isInstanceOf(BusinessException.class).hasMessage("请使用系统模板");
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template())); var out = new ByteArrayOutputStream()) {
            var row = book.getSheetAt(0).createRow(1);
            String[] values = valid("京A00109", "13810000109", "LIC-109", "DL-109", card("11010519900101109"));
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            row.getCell(7).setCellValue(30.1234);
            book.write(out);
            assertThat(service.preview(file(out.toByteArray())).rows().get(0).errors())
                    .anyMatch(issue -> issue.field().equals("最大载重（吨）") && issue.reason().contains("3 位"));
        }
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "初始导入.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }
    private byte[] workbook(String[]... rows) throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template())); var out = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            for (int index = 0; index < rows.length; index++) {
                var row = sheet.createRow(index + 1);
                for (int column = 0; column < rows[index].length; column++)
                    row.createCell(column).setCellValue(rows[index][column]);
            }
            book.write(out);
            return out.toByteArray();
        }
    }
    private String[] valid(String plate, String phone, String license, String driving, String idCard) {
        return new String[]{plate, "测试司机", phone, license, driving, idCard, "是", "30.000", "20.000", "电"};
    }
    private String card(String first17) {
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (first17.charAt(i) - '0') * weights[i];
        return first17 + "10X98765432".charAt(sum % 11);
    }
}
