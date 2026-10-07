package com.dfwl.fleet.tire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.tire.v3.TireV3Service;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3-tire;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql",
        "fleet.storage.local-root=${java.io.tmpdir}/fleet-v3-tire-tests"
})
class V3TireWorkflowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TireV3Service tires;
    private final AuthenticatedUser admin = new AuthenticatedUser(190L, "13900000190", 2L, "ADMIN",
            "管理员", Set.of("tire:view", "tire:manage", "tire:import"));

    @BeforeEach
    void reset() {
        for (String table : List.of("import_row", "import_task", "file_attachment", "tire_request",
                "tire_history", "tire", "driver_vehicle_current", "driver", "vehicle",
                "audit_log", "sys_user")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(190,'13900000190','HASH',2,1)");
    }

    private MockMultipartFile sheet(String[] headers, String[][] rows) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var page = book.createSheet("轮胎入库");
            var heading = page.createRow(0);
            for (int i = 0; i < headers.length; i++) heading.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < rows.length; r++) {
                var line = page.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) line.createCell(c).setCellValue(rows[r][c]);
            }
            book.write(out);
            return new MockMultipartFile("file", "轮胎入库.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private String[] headers() { return new String[]{"到货时间", "物料描述", "条码", "胎号"}; }
    private long id(Map<String, Object> value) { return ((Number) value.get("id")).longValue(); }

    @Test
    void fixedFourFieldImportCreatesAvailableTiresAndRowResults() throws Exception {
        try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(tires.template()))) {
            assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short) 4);
            for (int i = 0; i < 4; i++) assertThat(book.getSheetAt(0).getRow(0).getCell(i).getStringCellValue())
                    .isEqualTo(headers()[i]);
        }
        var preview = tires.preview(sheet(headers(), new String[][]{
                {"2026-10-07 08:30", "载重轮胎", "B-01", "T-001"},
                {"2026-10-07 09:30", "备用轮胎", "B-02", "T-002"}}), admin);
        assertThat((List<?>) preview.get("rows")).hasSize(2);
        var result = tires.confirm(id(preview), admin);
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire WHERE status='AVAILABLE'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE module='TIRE'", Long.class)).isEqualTo(2);
        tires.confirm(id(preview), admin);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire", Long.class)).isEqualTo(2);
    }

    @Test
    void invalidHeaderAndDuplicateTireNumberAreBusinessErrors() throws Exception {
        assertThatThrownBy(() -> tires.preview(sheet(new String[]{"到货时间", "物料描述", "车牌", "胎号"},
                new String[][]{{"2026-10-07 08:30", "轮胎", "云A", "T-001"}}), admin))
                .isInstanceOf(BusinessException.class);
        var preview = tires.preview(sheet(headers(), new String[][]{
                {"2026-10-07 08:30", "轮胎", "B-01", "T-001"},
                {"2026-10-07 08:30", "轮胎", "B-02", "T-001"}}), admin);
        var rows = (List<?>) preview.get("rows");
        assertThat(((Map<?, ?>) rows.get(1)).get("message")).asString().contains("重复");
        var result = tires.confirm(id(preview), admin);
        assertThat(result.get("status")).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire", Long.class)).isEqualTo(1);
    }

    @Test
    void authorizedEditKeepsHistoryAuditAndOptimisticVersion() {
        jdbc.update("""
                INSERT INTO tire(id,tire_no,barcode,material_description,arrived_at,status,created_by)
                VALUES(700,'T-OLD','B-OLD','旧轮胎','2026-10-07 08:30:00','AVAILABLE',190)
                """);
        var input = new TireV3Service.TireEdit("T-NEW", "B-NEW", "新轮胎",
                LocalDateTime.of(2026, 10, 7, 8, 30), 0, "核对入库单修正");
        var updated = tires.edit(700, input, admin);
        assertThat(updated.get("tireNo")).isEqualTo("T-NEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_history WHERE tire_id=700", Long.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE module='TIRE'", Long.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> tires.edit(700, input, admin)).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE tire SET status='CLAIMED' WHERE id=700");
        assertThatThrownBy(() -> tires.edit(700, new TireV3Service.TireEdit("T-OTHER", "B-NEW", "新轮胎",
                LocalDateTime.of(2026, 10, 7, 8, 30), 1, "再次修正"), admin))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void pendingAndApprovedRequestsCannotMatchTheSameInventoryAgain() {
        jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) "
                + "VALUES(191,'13900000191','HASH',1,1)");
        jdbc.update("INSERT INTO driver(id,user_id,driver_type,name,phone,status) "
                + "VALUES(191,191,'INTERNAL','司机','13900000191',1)");
        jdbc.update("INSERT INTO vehicle(id,plate_no,insurance_complete,energy_type,max_load,"
                + "load_standard_type,load_standard_percent,status) "
                + "VALUES(191,'测F191',1,'ELECTRIC',30,'PERCENT',0.9,1)");
        jdbc.update("INSERT INTO driver_vehicle_current(driver_id,vehicle_id,bound_at,bound_by) "
                + "VALUES(191,191,CURRENT_TIMESTAMP,190)");
        jdbc.update("INSERT INTO tire(id,tire_no,material_description,arrived_at,status,created_by) "
                + "VALUES(701,'T-701','轮胎',CURRENT_TIMESTAMP,'AVAILABLE',190)");
        var driver = new AuthenticatedUser(191L, "13900000191", 1L, "DRIVER", "司机",
                Set.of("tire:request:create"));
        assertThat(tires.requestTarget("T-701", driver).get("status")).isEqualTo("AVAILABLE");
        jdbc.update("INSERT INTO tire_request(id,applicant_driver_id,vehicle_id,confirmed_tire_no,"
                + "ocr_record_id,photo_attachment_id,tire_id,status) "
                + "VALUES(701,191,191,'T-701',1,1,701,'PENDING_APPROVAL')");
        assertThatThrownBy(() -> tires.requestTarget("T-701", driver))
                .isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE tire_request SET status='RETURNED_TO_APPLICANT' WHERE id=701");
        assertThat(tires.requestTarget("T-701", driver).get("status")).isEqualTo("AVAILABLE");
        jdbc.update("UPDATE tire_request SET status='APPROVED' WHERE id=701");
        assertThatThrownBy(() -> tires.requestTarget("T-701", driver))
                .isInstanceOf(BusinessException.class);
    }
}
