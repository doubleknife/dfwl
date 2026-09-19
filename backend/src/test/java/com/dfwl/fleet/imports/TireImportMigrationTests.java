package com.dfwl.fleet.imports;

import com.dfwl.fleet.imports.spi.ImportBusinessHandler;
import com.dfwl.fleet.imports.spi.ImportBusinessType;
import org.springframework.boot.test.mock.mockito.SpyBean;
import com.dfwl.fleet.tire.imports.TireImportHandler;
import com.dfwl.fleet.tire.repository.TireRepository;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;
import com.dfwl.fleet.imports.dto.request.ImportPreviewRequest;
import com.dfwl.fleet.imports.repository.ImportRepository;
import com.dfwl.fleet.imports.service.ImportRowCommitService;
import com.dfwl.fleet.imports.service.ImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tiremigration;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class TireImportMigrationTests {
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private ImportRepository repository;
    @Autowired private ImportRowCommitService rows;
    @Autowired private ImportService imports;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        for (String table : List.of("tire_claim", "tire", "import_row", "import_task", "driver", "vehicle")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO driver (id, name, phone, driver_type, status) VALUES (1, 'A', '13800000001', 'INTERNAL', 1)");
        jdbc.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type, load_standard_percent, status)
                VALUES (1, 'TEST-1', 1, 'ELECTRIC', 30, 'PERCENT', 0.9, 1)
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stock", "claimed", "explicitKey", "explicitStock", "reserved", "missingNo", "blankNo", "badStatus",
            "missingDriver", "missingVehicle", "missingTime", "badTime", "badArrival", "absentDriver", "absentVehicle",
            "inactiveDriver", "inactiveVehicle", "badDriver", "duplicateStock", "duplicateClaimed", "duplicateReserved",
            "batchDuplicate", "historyDuplicate", "duplicateBeforeInvalidStatus"})
    void tireBehaviorCharacterization(String scenario) throws Exception {
        Map<String,Object> raw = new HashMap<>(Map.of("tireNo", " T-001 ", "barcode", "00123", "description", "history",
                "arrivalTime", "2026-09-01T08:00:00", "used", true, "driverId", 1, "vehicleId", 1, "installTime", "2026-09-10T09:30:00"));
        String key = "T-001", explicit = null, expectedStatus = "CLAIMED", code = null, message = null;
        boolean existing = false;
        switch (scenario) {
            case "stock" -> { raw.put("used", false); raw.remove("driverId"); raw.remove("vehicleId"); raw.remove("installTime"); expectedStatus = "IN_STOCK"; }
            case "explicitKey" -> { explicit = " CUSTOM "; key = "CUSTOM"; }
            case "explicitStock" -> { raw.put("status", "in_stock"); expectedStatus = "IN_STOCK"; }
            case "reserved" -> { raw.put("status", "APPROVAL_RESERVED"); expectedStatus = "APPROVAL_RESERVED"; }
            case "missingNo", "blankNo" -> { if (scenario.equals("missingNo")) raw.remove("tireNo"); else raw.put("tireNo", " "); key = null; code = "IMPORT_FORMAT_ERROR"; message = "tireNo不能为空"; }
            case "badStatus" -> { raw.put("status", "INSTALLED"); code = "IMPORT_FORMAT_ERROR"; message = "轮胎状态不合法"; }
            case "missingDriver", "missingVehicle", "missingTime" -> { raw.remove(scenario.equals("missingDriver") ? "driverId" : scenario.equals("missingVehicle") ? "vehicleId" : "installTime"); code = "IMPORT_FORMAT_ERROR"; message = "历史已使用轮胎必须提供司机、车辆和安装时间"; }
            case "badTime", "badArrival" -> { raw.put(scenario.equals("badTime") ? "installTime" : "arrivalTime", "bad"); code = "IMPORT_FORMAT_ERROR"; message = "导入行格式错误"; key = null; }
            case "absentDriver", "inactiveDriver" -> { if (scenario.equals("absentDriver")) raw.put("driverId", 99); else jdbc.update("UPDATE driver SET status = 0 WHERE id = 1"); code = "IMPORT_REFERENCE_NOT_FOUND"; message = "司机不存在"; }
            case "absentVehicle", "inactiveVehicle" -> { if (scenario.equals("absentVehicle")) raw.put("vehicleId", 99); else jdbc.update("UPDATE vehicle SET status = 0 WHERE id = 1"); code = "IMPORT_REFERENCE_NOT_FOUND"; message = "车辆不存在"; }
            case "badDriver" -> { raw.put("driverId", "bad"); key = null; code = "IMPORT_FORMAT_ERROR"; message = "driverId格式错误"; }
            case "duplicateStock", "duplicateClaimed", "duplicateReserved", "duplicateBeforeInvalidStatus" -> {
                existing = true; code = "IMPORT_DUPLICATE_EXISTING"; message = "业务数据已存在";
                String state = scenario.equals("duplicateClaimed") ? "CLAIMED" : scenario.equals("duplicateReserved") ? "APPROVAL_RESERVED" : "IN_STOCK";
                jdbc.update("INSERT INTO tire (tire_no, status, data_source) VALUES ('T-001', ?, 'IMPORT')", state);
                if (scenario.equals("duplicateBeforeInvalidStatus")) raw.put("status", "BAD");
            }
            case "batchDuplicate" -> { code = "IMPORT_DUPLICATE_IN_BATCH"; message = "导入批次内重复"; }
            case "historyDuplicate" -> { code = "IMPORT_DUPLICATE_EXISTING"; message = "业务数据已存在"; long old = task(); repository.insertRow(old, 1, "{}", "{}", "SUCCESS", null, null, "TIRE", "T-001"); repository.markRowFinal(repository.findRows(old).get(0).id(), "SUCCESS", null, null, "TIRE", 90L, "T-001"); }
            default -> { }
        }
        var batch = new HashSet<String>();
        if (scenario.equals("batchDuplicate")) batch.add("T-001");
        var preview = rows.preparePreview("TIRE", new ImportPreviewRequest.ImportRowRequest(1, raw, explicit), batch);
        assertThat(preview.validation().success()).isEqualTo(code == null);
        assertThat(preview.validation().errorCode()).isEqualTo(code);
        assertThat(preview.validation().errorMessage()).isEqualTo(message);
        assertThat(preview.businessUniqueKey()).isEqualTo(key);
        long task = task();
        repository.insertRow(task, 1, mapper.writeValueAsString(raw), null, "SUCCESS", null, null, "TIRE", explicit);
        var row = repository.findRows(task).get(0);
        var commitBatch = new HashSet<String>();
        if (scenario.equals("batchDuplicate")) commitBatch.add("T-001");
        rows.commitRow("TIRE", row, commitBatch, 7);
        var result = repository.findRows(task).get(0);
        assertThat(result.finalStatus()).isEqualTo(code == null ? "SUCCESS" : "FAILURE");
        assertThat(result.finalErrorCode()).isEqualTo(code);
        assertThat(result.finalErrorMessage()).isEqualTo(message);
        assertThat(result.businessUniqueKey()).isEqualTo(key);
        if (code == null) {
            long id = jdbc.queryForObject("SELECT id FROM tire WHERE tire_no = 'T-001'", Long.class);
            assertThat(result.businessId()).isEqualTo(id);
            var tire = jdbc.queryForMap("SELECT * FROM tire WHERE id = ?", id);
            assertThat(tire).containsEntry("status", expectedStatus).containsEntry("tire_no", "T-001")
                    .containsEntry("barcode", "00123").containsEntry("data_source", "IMPORT").containsEntry("description", "history");
            assertThat(tire.get("created_at")).isNotNull();
            assertThat(tire.get("arrival_time")).isEqualTo(java.sql.Timestamp.valueOf("2026-09-01 08:00:00"));
            if (scenario.equals("stock")) assertThat(tire).containsEntry("install_time", null).containsEntry("driver_id", null).containsEntry("vehicle_id", null);
            else assertThat(tire).containsEntry("install_time", java.sql.Timestamp.valueOf("2026-09-10 09:30:00")).containsEntry("driver_id", 1L).containsEntry("vehicle_id", 1L);
            if (expectedStatus.equals("CLAIMED")) {
                var claim = jdbc.queryForMap("SELECT * FROM tire_claim WHERE tire_id = ?", id);
                assertThat(claim).containsEntry("driver_id", 1L).containsEntry("vehicle_id", 1L).containsEntry("request_id", null)
                        .containsEntry("source_type", "IMPORT").containsEntry("install_time", java.sql.Timestamp.valueOf("2026-09-10 09:30:00"));
            } else assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_claim", Integer.class)).isZero();
        } else {
            assertThat(result.businessId()).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire", Integer.class)).isEqualTo(existing ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_claim", Integer.class)).isZero();
        }
    }

    @Autowired private List<ImportBusinessHandler> handlers;
    @SpyBean
    private TireImportHandler tireHandler;
    @SpyBean
    private TireRepository tireRepository;

    @Test
    void discoversTireHandlerAndRoutesOnlyMigratedTypes() throws Exception {
        assertThat(handlers.stream().map(ImportBusinessHandler::businessType))
                .containsExactlyInAnyOrder(ImportBusinessType.SALARY,
                        ImportBusinessType.TIRE, ImportBusinessType.EXPENSE, ImportBusinessType.ROUTE);
        long task = preparedTask(false);
        verify(tireHandler).resolveBusinessKey(any());
        verify(tireHandler).validateDuplicate(any());
        verify(tireHandler).validate(any());
        assertThat(imports.commit(task, 7).successCount()).isEqualTo(1);
        verify(tireHandler).commit(any());
        clearInvocations(tireHandler);
        for (String type : List.of("ROUTE", "EXPENSE", "SALARY")) {
            rows.preparePreview(type, new ImportPreviewRequest.ImportRowRequest(1, Map.of(), null), new HashSet<>());
        }
        verifyNoInteractions(tireHandler);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLAIMED", "DRIVER", "VEHICLE"})
    void commitReadsLatestTireAndReferenceState(String change) throws Exception {
        long task = preparedTask(false);
        switch (change) {
            case "CLAIMED" -> jdbc.update("INSERT INTO tire (tire_no, status, data_source) VALUES ('T-FIRST', 'CLAIMED', 'IMPORT')");
            case "DRIVER" -> jdbc.update("UPDATE driver SET status = 0 WHERE id = 1");
            case "VEHICLE" -> jdbc.update("UPDATE vehicle SET status = 0 WHERE id = 1");
            default -> throw new IllegalArgumentException(change);
        }
        var result = imports.commit(task, 7);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.rows().get(0).finalErrorCode()).isEqualTo(change.equals("CLAIMED") ? "IMPORT_DUPLICATE_EXISTING" : "IMPORT_REFERENCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_claim", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire", Integer.class)).isEqualTo(change.equals("CLAIMED") ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLAIM", "FINAL_ROW"})
    void failedClaimOrFinalRowRollsBackBusinessAndContinuesNextRow(String stage) throws Exception {
        long task = preparedTask(true);
        long firstRow = repository.findRows(task).get(0).id();
        Answer<Object> failAfterWrite = invocation -> {
            invocation.callRealMethod();
            throw new DataAccessResourceFailureException("injected " + stage);
        };
        if (stage.equals("CLAIM")) {
            doAnswer(failAfterWrite).when(tireRepository).insertImportedClaim(
                    anyLong(), anyLong(),
                    anyLong(), any());
        } else {
            doAnswer(failAfterWrite).when(repository).markRowFinal(
                    eq(firstRow), eq("SUCCESS"),
                    any(), any(), eq("TIRE"),
                    any(), any());
        }
        var result = imports.commit(task, 7);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.rows().get(0).finalStatus()).isEqualTo("FAILURE");
        assertThat(result.rows().get(0).finalErrorCode()).isEqualTo("IMPORT_DB_ERROR");
        assertThat(result.rows().get(0).finalErrorMessage()).isEqualTo("数据库写入失败");
        assertThat(result.rows().get(0).businessId()).isNull();
        assertThat(result.rows().get(1).finalStatus()).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForList("SELECT tire_no FROM tire", String.class)).containsExactly("T-SECOND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_claim", Integer.class)).isZero();
        var before = repository.findRows(task);
        imports.commit(task, 7);
        assertThat(repository.findRows(task)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire", Integer.class)).isEqualTo(1);
    }

    private long preparedTask(boolean secondRow) throws Exception {
        long task = task();
        Map<String,Object> first = Map.of("tireNo", "T-FIRST", "used", true, "driverId", 1,
                "vehicleId", 1, "installTime", "2026-09-10T09:30:00");
        var keys = new HashSet<String>();
        var rawRows = secondRow ? List.of(first, Map.<String,Object>of("tireNo", "T-SECOND", "used", false)) : List.of(first);
        int number = 0;
        for (var raw : rawRows) {
            var preview = rows.preparePreview("TIRE", new ImportPreviewRequest.ImportRowRequest(++number, raw, null), keys);
            assertThat(preview.validation().success()).isTrue();
            repository.insertRow(task, number, mapper.writeValueAsString(raw), mapper.writeValueAsString(raw), "SUCCESS",
                    null, null, "TIRE", preview.businessUniqueKey());
        }
        return task;
    }

    private long task() { return repository.createTask(UUID.randomUUID().toString(), "TIRE", null, 1L, 1, 0, 0, 7); }
}
