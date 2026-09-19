package com.dfwl.fleet.imports;

import static org.assertj.core.api.Assertions.*;
import com.dfwl.fleet.imports.dto.request.ImportPreviewRequest;
import com.dfwl.fleet.imports.repository.ImportRepository;
import com.dfwl.fleet.imports.service.ImportRowCommitService;
import com.dfwl.fleet.imports.service.ImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:expensemigration;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class ExpenseImportMigrationTests {
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.SpyBean ImportRepository repository;
    @Autowired ImportRowCommitService rows;
    @Autowired ImportService imports;
    @Autowired ObjectMapper mapper;

    @BeforeEach void setup() {
        for (String table : List.of("expense_attribution_history", "expense_energy_detail", "expense_penalty_detail", "expense_repair_detail", "expense_toll_detail", "expense_entry", "import_row", "import_task", "route_status_history", "route_task", "vehicle", "customer", "product")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type, load_standard_percent, status) VALUES (1, 'E-1', 1, 'ELECTRIC', 30, 'PERCENT', 0.9, 1)");
        jdbc.update("""
            INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                direction, loading_place, unloading_place, tax_unit_price, status, departure_vehicle_id,
                departure_time, unload_time, created_by)
            VALUES (1, 'R1', 'R1', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 10, 'IN_TRANSIT', 1,
                TIMESTAMP '2026-09-10 08:00:00', TIMESTAMP '2026-09-10 12:00:00', 7)
            """);
    }

    Map<String,Object> raw(String type) {
        var raw = new HashMap<String,Object>(Map.of("expenseNo", " E-001 ", "expenseType", type, "businessDate", "2026-09-10", "vehicleId", 1, "attributionType", "DAILY", "amount", "88.50", "remark", "imported"));
        if (Set.of("ELECTRIC", "GAS", "TEMP_ELECTRIC").contains(type)) raw.put("energyDetail", new HashMap<>(Map.of("stationId", 3, "orderNo", "O-1", "startTime", "2026-09-10T09:00:00", "quantity", "20.25")));
        return raw;
    }

    @ParameterizedTest
    @ValueSource(strings={"PENALTY", "CARRYING", "REPAIR", "ELECTRIC", "GAS", "TEMP_ELECTRIC", "WATER", "TOLL", "CAR_WASH", "INFORMATION", "DRIVER_SALARY", "OTHER"})
    void allExistingTypesAndDetails(String type) throws Exception {
        var raw = raw(type);
        raw.put("penaltyDetail", Map.of("detail", "fine", "deductPoints", "2", "penaltyNo", "P1", "driverId", 4));
        raw.put("repairDetail", Map.of("detail", "repair", "trailerId", 5, "receiptNo", "RR1", "invoiceNo", "I1", "repairShop", "shop"));
        raw.put("tollDetail", Map.of("detail", "toll", "trailerId", 6, "receiptNo", "T1"));
        long task=prepare(raw, null);
        var result=imports.commit(task, 7).rows().get(0);
        assertThat(result.finalStatus()).isEqualTo("SUCCESS");
        assertThat(result.finalErrorCode()).isNull(); assertThat(result.finalErrorMessage()).isNull();
        assertThat(result.businessUniqueKey()).isEqualTo("EXPENSE_NO|E-001");
        long id=result.businessId();
        boolean energy=raw.containsKey("energyDetail");
        assertThat(jdbc.queryForMap("SELECT * FROM expense_entry WHERE id=?", id))
            .containsEntry("expense_no", "E-001").containsEntry("expense_type", type).containsEntry("vehicle_id", 1L)
            .containsEntry("driver_id", null).containsEntry("route_id", energy ? 1L : null)
            .containsEntry("attribution_type", energy ? "ROUTE" : "DAILY").containsEntry("status", "ACTIVE")
            .containsEntry("source_type", "IMPORT").containsEntry("created_by", 7L).containsEntry("import_row_id", result.id())
            .containsEntry("business_date", java.sql.Date.valueOf("2026-09-10")).containsEntry("remark", "imported");
        assertThat(jdbc.queryForObject("SELECT amount FROM expense_entry WHERE id=?", java.math.BigDecimal.class,id)).isEqualByComparingTo("88.50");
        assertThat(jdbc.queryForMap("SELECT * FROM expense_penalty_detail WHERE expense_id=?",id)).containsEntry("detail","fine").containsEntry("penalty_no","P1").containsEntry("driver_id",4L);
        assertThat(jdbc.queryForObject("SELECT deduct_points FROM expense_penalty_detail WHERE expense_id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForMap("SELECT * FROM expense_repair_detail WHERE expense_id=?",id)).containsEntry("detail","repair").containsEntry("trailer_id",5L).containsEntry("receipt_no","RR1").containsEntry("invoice_no","I1").containsEntry("repair_shop","shop");
        assertThat(jdbc.queryForMap("SELECT * FROM expense_toll_detail WHERE expense_id=?",id)).containsEntry("detail","toll").containsEntry("trailer_id",6L).containsEntry("receipt_no","T1");
        if (energy) {
            assertThat(jdbc.queryForMap("SELECT * FROM expense_energy_detail WHERE expense_id=?",id)).containsEntry("energy_type",type).containsEntry("station_id",3L).containsEntry("order_no","O-1").containsEntry("match_status","AUTO_MATCHED").containsEntry("auto_matched_route_id",1L).containsEntry("start_time",java.sql.Timestamp.valueOf("2026-09-10 09:00:00"));
            assertThat(jdbc.queryForObject("SELECT quantity FROM expense_energy_detail WHERE expense_id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("20.25");
        }
        var saved=repository.findRows(task); imports.commit(task,7); assertThat(repository.findRows(task)).isEqualTo(saved);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry",Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings={"explicit", "energy", "penalty", "invoice", "repairReceipt", "toll", "missingKey", "vehicleMissing", "vehicleAbsent", "vehicleInactive", "routeMissing", "routeAbsent", "dailyRoute", "badAttribution", "badType", "badAmount", "badDate", "missingEnergy", "batch", "history", "historyBeforeBadAmount"})
    void keysErrorsAndPriority(String scenario) throws Exception {
        var raw=raw("OTHER"); String explicit=null, key="EXPENSE_NO|E-001", code=null, message=null;
        switch(scenario) {
            case "explicit" -> { explicit=" CUSTOM "; key="CUSTOM"; }
            case "energy" -> {raw=raw("GAS"); raw.remove("expenseNo"); key="ENERGY|GAS|3|O-1";}
            case "penalty" -> {raw.remove("expenseNo"); raw.put("penaltyDetail",Map.of("penaltyNo","P1")); key="PENALTY|P1";}
            case "invoice", "repairReceipt" -> {raw.remove("expenseNo"); raw.put("repairDetail",scenario.equals("invoice") ? Map.of("invoiceNo","I1","receiptNo","R1") : Map.of("receiptNo","R1")); key=scenario.equals("invoice") ? "REPAIR_INVOICE|I1" : "REPAIR_RECEIPT|R1";}
            case "toll" -> {raw.remove("expenseNo"); raw.put("tollDetail",Map.of("receiptNo","T1")); key="TOLL_RECEIPT|T1";}
            case "missingKey" -> {raw.remove("expenseNo"); code="IMPORT_KEY_MISSING";message="无天然唯一编号的费用必须提供businessUniqueKey";key=null;}
            case "vehicleMissing" -> {raw.remove("vehicleId");code="IMPORT_FORMAT_ERROR";message="vehicleId不能为空";key=null;}
            case "vehicleAbsent", "vehicleInactive" -> {if(scenario.equals("vehicleAbsent")) raw.put("vehicleId",99);else jdbc.update("UPDATE vehicle SET status=0 WHERE id=1");code="IMPORT_REFERENCE_NOT_FOUND";message="车辆不存在";key=null;}
            case "routeMissing" -> {raw.put("attributionType","ROUTE");code="EXPENSE_003";message="线路费用必须绑定线路";key=null;}
            case "routeAbsent" -> {raw.put("attributionType","ROUTE");raw.put("routeId",99);code="ROUTE_001";message="线路不存在";key=null;}
            case "dailyRoute" -> {raw.put("routeId",1);code="EXPENSE_003";message="日常费用不能绑定线路";key=null;}
            case "badAttribution" -> {raw.put("attributionType","BAD");code="EXPENSE_003";message="费用归属不合法";key=null;}
            case "badType" -> {raw.put("expenseType","BAD");code="IMPORT_FORMAT_ERROR";message="费用类型不合法";key=null;}
            case "badAmount" -> {raw.put("amount","bad");code="IMPORT_FORMAT_ERROR";message="amount格式错误";key=null;}
            case "badDate" -> {raw.put("businessDate","bad");code="IMPORT_FORMAT_ERROR";message="导入行格式错误";key=null;}
            case "missingEnergy" -> {raw.put("expenseType","GAS");code="IMPORT_FORMAT_ERROR";message="能源费用明细不能为空";key=null;}
            case "batch" -> {code="IMPORT_DUPLICATE_IN_BATCH";message="导入批次内重复";}
            case "history", "historyBeforeBadAmount" -> {long old=task(); repository.insertRow(old,1,"{}",null,"SUCCESS",null,null,"EXPENSE",key);repository.markRowFinal(repository.findRows(old).get(0).id(),"SUCCESS",null,null,"EXPENSE",99L,key);code="IMPORT_DUPLICATE_EXISTING";message="业务数据已存在";if(scenario.equals("historyBeforeBadAmount"))raw.put("amount","bad");}
        }
        var batch=new HashSet<String>(); if(scenario.equals("batch"))batch.add(key);
        var preview=rows.preparePreview("EXPENSE",new ImportPreviewRequest.ImportRowRequest(1,raw,explicit),batch);
        assertThat(preview.businessUniqueKey()).isEqualTo(key);
        assertThat(preview.validation().errorCode()).isEqualTo(code);assertThat(preview.validation().errorMessage()).isEqualTo(message);
        long task=task();repository.insertRow(task,1,mapper.writeValueAsString(raw),null,"SUCCESS",null,null,"EXPENSE",explicit);
        var commitBatch=new HashSet<String>();if(scenario.equals("batch"))commitBatch.add(key);
        rows.commitRow("EXPENSE",repository.findRows(task).get(0),commitBatch,7);
        var result=repository.findRows(task).get(0);
        assertThat(result.finalStatus()).isEqualTo(code==null?"SUCCESS":"FAILURE");assertThat(result.businessUniqueKey()).isEqualTo(key);
        assertThat(result.finalErrorCode()).isEqualTo(code);assertThat(result.finalErrorMessage()).isEqualTo(message);
        if(code==null)assertThat(result.businessId()).isEqualTo(jdbc.queryForObject("SELECT id FROM expense_entry",Long.class));else assertThat(result.businessId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings={"manual", "unmatched", "voided", "nullUnload", "boundaryStart", "boundaryEnd", "completed", "ordinaryRoute"})
    void matchingBehavior(String scenario) throws Exception {
        var raw=raw(scenario.equals("ordinaryRoute")?"OTHER":"ELECTRIC");raw.put("attributionType","ROUTE");
        boolean unmatched=Set.of("unmatched","voided","nullUnload").contains(scenario);
        if(scenario.equals("manual")||scenario.equals("ordinaryRoute"))raw.put("routeId",1);
        if(scenario.equals("unmatched"))jdbc.update("UPDATE route_task SET departure_vehicle_id=2");
        if(scenario.equals("voided"))jdbc.update("UPDATE route_task SET status='VOIDED'");
        if(scenario.equals("nullUnload"))jdbc.update("UPDATE route_task SET unload_time=NULL");
        if(scenario.equals("completed"))jdbc.update("UPDATE route_task SET status='COMPLETED'");
        if(scenario.startsWith("boundary")) { @SuppressWarnings("unchecked") var detail=(Map<String,Object>)raw.get("energyDetail"); detail.put("startTime",scenario.equals("boundaryStart")?"2026-09-10T08:00:00":"2026-09-10T12:00:00"); }
        long task=prepare(raw,null);var result=imports.commit(task,7);assertThat(result.successCount()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM expense_entry")).containsEntry("route_id",unmatched?null:1L).containsEntry("status","ACTIVE").containsEntry("attribution_type","ROUTE");
        if(!scenario.equals("ordinaryRoute"))assertThat(jdbc.queryForMap("SELECT * FROM expense_energy_detail")).containsEntry("match_status",unmatched?"UNMATCHED":scenario.equals("manual")?"MANUAL":"AUTO_MATCHED").containsEntry("auto_matched_route_id",unmatched?null:1L);
    }

    @Autowired java.util.List<com.dfwl.fleet.imports.spi.ImportBusinessHandler> handlers;
    @org.springframework.boot.test.mock.mockito.SpyBean com.dfwl.fleet.expense.imports.ExpenseImportHandler expenseHandler;
    @org.springframework.boot.test.mock.mockito.SpyBean com.dfwl.fleet.expense.repository.ExpenseRepository expenses;
    @Autowired com.dfwl.fleet.route.service.RouteService routes;
    @Autowired com.dfwl.fleet.expense.service.ExpenseService expenseService;

    @org.junit.jupiter.api.Test
    void discoveryAndDispatch() throws Exception {
        assertThat(handlers.stream().map(h -> h.businessType().name())).containsExactlyInAnyOrder("SALARY","TIRE","EXPENSE","ROUTE");
        long task=prepare(raw("OTHER"),null);
        org.mockito.Mockito.verify(expenseHandler).resolveBusinessKey(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(expenseHandler).validateDuplicate(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(expenseHandler).validate(org.mockito.ArgumentMatchers.any());
        imports.commit(task,7);
        org.mockito.Mockito.verify(expenseHandler).commit(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.clearInvocations(expenseHandler);
        for(String type:List.of("ROUTE","SALARY","TIRE")) rows.preparePreview(type,new ImportPreviewRequest.ImportRowRequest(1,Map.of(),null),new HashSet<>());
        org.mockito.Mockito.verifyNoInteractions(expenseHandler);
    }

    @ParameterizedTest
    @ValueSource(strings={"VEHICLE","ROUTE","MATCH","HISTORY","BUSINESS"})
    void commitRechecksLiveState(String change) throws Exception {
        var raw=raw(change.equals("MATCH")?"GAS":"OTHER");
        if(change.equals("ROUTE")){raw.put("attributionType","ROUTE");raw.put("routeId",1);}
        long task=prepare(raw,null);
        switch(change){
            case "VEHICLE" -> jdbc.update("UPDATE vehicle SET status=0 WHERE id=1");
            case "ROUTE" -> jdbc.update("UPDATE route_task SET deleted_at=CURRENT_TIMESTAMP WHERE id=1");
            case "MATCH" -> jdbc.update("UPDATE route_task SET status='VOIDED' WHERE id=1");
            case "HISTORY" -> {long old=task();repository.insertRow(old,1,"{}",null,"SUCCESS",null,null,"EXPENSE","EXPENSE_NO|E-001");repository.markRowFinal(repository.findRows(old).get(0).id(),"SUCCESS",null,null,"EXPENSE",99L,"EXPENSE_NO|E-001");}
            case "BUSINESS" -> jdbc.update("INSERT INTO expense_entry (expense_no,expense_type,business_date,vehicle_id,attribution_type,amount,source_type,status,created_by) VALUES ('E-001','OTHER','2026-09-10',1,'DAILY',1,'MANUAL','ACTIVE',7)");
        }
        var result=imports.commit(task,7).rows().get(0);
        if(change.equals("MATCH")) {
            assertThat(result.finalStatus()).isEqualTo("SUCCESS");
            assertThat(jdbc.queryForMap("SELECT * FROM expense_energy_detail")).containsEntry("match_status","UNMATCHED").containsEntry("auto_matched_route_id",null);
        } else {
            assertThat(result.finalStatus()).isEqualTo("FAILURE");
            assertThat(result.finalErrorCode()).isEqualTo(switch(change){case "VEHICLE" -> "IMPORT_REFERENCE_NOT_FOUND";case "ROUTE" -> "ROUTE_001";case "HISTORY" -> "IMPORT_DUPLICATE_EXISTING";default -> "IMPORT_DB_ERROR";});
            assertThat(result.businessId()).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry",Integer.class)).isEqualTo(change.equals("BUSINESS")?1:0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"ENERGY","PENALTY","REPAIR","TOLL","FINAL_ROW"})
    void detailOrFinalRowFailureRollsBackAndContinues(String stage) throws Exception {
        var raw=raw("GAS");raw.put("penaltyDetail",Map.of("detail","p"));raw.put("repairDetail",Map.of("detail","r"));raw.put("tollDetail",Map.of("detail","t"));
        long task=prepare(raw,null);long first=repository.findRows(task).get(0).id();
        var second=raw("OTHER");second.put("expenseNo","E-SECOND");
        repository.insertRow(task,2,mapper.writeValueAsString(second),null,"SUCCESS",null,null,"EXPENSE","EXPENSE_NO|E-SECOND");
        org.mockito.stubbing.Answer<Object> fail = invocation -> {invocation.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("injected "+stage);};
        switch(stage){
            case "ENERGY" -> org.mockito.Mockito.doAnswer(fail).when(expenses).insertEnergyDetail(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            case "PENALTY" -> org.mockito.Mockito.doAnswer(fail).when(expenses).insertPenaltyDetail(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            case "REPAIR" -> org.mockito.Mockito.doAnswer(fail).when(expenses).insertRepairDetail(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            case "TOLL" -> org.mockito.Mockito.doAnswer(fail).when(expenses).insertTollDetail(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            default -> org.mockito.Mockito.doAnswer(fail).when(repository).markRowFinal(org.mockito.ArgumentMatchers.eq(first),org.mockito.ArgumentMatchers.eq("SUCCESS"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("EXPENSE"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        }
        var result=imports.commit(task,7);assertThat(result.successCount()).isEqualTo(1);assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.rows().get(0).finalErrorCode()).isEqualTo("IMPORT_DB_ERROR");assertThat(result.rows().get(0).businessId()).isNull();
        assertThat(jdbc.queryForList("SELECT expense_no FROM expense_entry",String.class)).containsExactly("E-SECOND");
        for(String table:List.of("expense_energy_detail","expense_penalty_detail","expense_repair_detail","expense_toll_detail"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
        var saved=repository.findRows(task);imports.commit(task,7);assertThat(repository.findRows(task)).isEqualTo(saved);
    }

    @org.junit.jupiter.api.Test
    void importedEnergyCanBeVoidedAndReattributed() throws Exception {
        jdbc.update("INSERT INTO customer (id,customer_name,status) VALUES (1,'C',1)");
        jdbc.update("INSERT INTO product (id,product_name,status) VALUES (1,'P',1)");
        long task=prepare(raw("ELECTRIC"),null);long id=imports.commit(task,7).rows().get(0).businessId();
        routes.voidRoute(1,new com.dfwl.fleet.route.dto.request.RouteReasonRequest("void"),7);
        assertThat(jdbc.queryForMap("SELECT * FROM expense_entry WHERE id=?",id)).containsEntry("status","PENDING_ATTRIBUTION").containsEntry("route_id",1L);
        expenseService.adjustAttribution(id,new com.dfwl.fleet.expense.dto.request.ExpenseAttributionRequest("DAILY",null,"confirmed"),7);
        assertThat(jdbc.queryForMap("SELECT * FROM expense_entry WHERE id=?",id)).containsEntry("status","ACTIVE").containsEntry("route_id",null).containsEntry("attribution_type","DAILY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_attribution_history WHERE expense_id=?",Integer.class,id)).isEqualTo(2);
    }

    @org.junit.jupiter.api.Test
    void historyAppearingAfterInitialDuplicateCheckIsStillRejected() throws Exception {
        long task=prepare(raw("OTHER"),null);
        org.mockito.Mockito.doAnswer(invocation -> {
            Object initial = invocation.callRealMethod();
            long other=task();
            repository.insertRow(other,1,"{}",null,"SUCCESS",null,null,"EXPENSE","EXPENSE_NO|E-001");
            repository.markRowFinal(repository.findRows(other).get(0).id(),"SUCCESS",null,null,"EXPENSE",99L,"EXPENSE_NO|E-001");
            return initial;
        }).when(repository).committedKeyExists("EXPENSE","EXPENSE_NO|E-001");
        var result=imports.commit(task,7).rows().get(0);
        assertThat(result.finalErrorCode()).isEqualTo("IMPORT_DUPLICATE_EXISTING");
        assertThat(result.businessId()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry",Integer.class)).isZero();
    }

    long prepare(Map<String,Object> raw,String key) throws Exception {
        var preview=rows.preparePreview("EXPENSE",new ImportPreviewRequest.ImportRowRequest(1,raw,key),new HashSet<>());
        assertThat(preview.validation().success()).isTrue();long task=task();
        repository.insertRow(task,1,mapper.writeValueAsString(raw),null,"SUCCESS",null,null,"EXPENSE",preview.businessUniqueKey());return task;
    }
    long task(){return repository.createTask(UUID.randomUUID().toString(),"EXPENSE",null,1L,1,0,0,7);}
}
