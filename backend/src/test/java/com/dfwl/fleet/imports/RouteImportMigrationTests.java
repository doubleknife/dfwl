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

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:routemigration;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class RouteImportMigrationTests {
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.SpyBean ImportRepository repository;
    @Autowired ImportRowCommitService rows;
    @Autowired ImportService imports;
    @Autowired ObjectMapper mapper;
    static final String KEY="1|2026-09-10|OUTBOUND|A|B";

    @BeforeEach void setup() {
        for(String t:List.of("route_status_history","route_task","import_row","import_task","driver_vehicle_current","vehicle_trailer_current","driver","vehicle","trailer","customer","product"))jdbc.update("DELETE FROM "+t);
        jdbc.update("INSERT INTO customer (id,customer_name,status) VALUES (1,'C',1)");
        jdbc.update("INSERT INTO product (id,product_name,status) VALUES (1,'P',1)");
        jdbc.update("INSERT INTO driver (id,name,phone,driver_type,status) VALUES (1,'D','13800000001','INTERNAL',1)");
        jdbc.update("INSERT INTO vehicle (id,plate_no,insurance_complete,energy_type,max_load,load_standard_type,load_standard_percent,status) VALUES (1,'V1',1,'ELECTRIC',30,'PERCENT',0.9,1),(2,'V2',1,'GAS',30,'PERCENT',0.9,1)");
        jdbc.update("INSERT INTO trailer (id,trailer_no,insurance_complete,status) VALUES (1,'T1',1,1)");
        jdbc.update("INSERT INTO driver_vehicle_current (driver_id,vehicle_id,bound_at,bound_by) VALUES (1,1,CURRENT_TIMESTAMP,7)");
        jdbc.update("INSERT INTO vehicle_trailer_current (vehicle_id,trailer_id,bound_at,bound_by) VALUES (1,1,CURRENT_TIMESTAMP,7)");
    }
    Map<String,Object> raw() {
        var r=new HashMap<String,Object>();
        r.put("routeNo","R-IMPORT");r.put("businessDate","2026-09-10");r.put("customerId",1);r.put("productId",1);r.put("direction","OUTBOUND");r.put("loadingPlace"," A ");r.put("unloadingPlace","B");r.put("taxUnitPrice","12.3456");r.put("assignedDriverId",1);
        r.put("externalRouteNo","EXT");r.put("tripSequence","02");r.put("carryingFee","10.20");r.put("mileageKm","123.45");r.put("infoFee","3.40");r.put("driverSalary","50.60");r.put("salarySource","IMPORT");
        return r;
    }
    @ParameterizedTest
    @ValueSource(strings={"published","noDriver","noDriverVehicle","driverNoVehicle","driverInactive","vehicleInactive","vehicleInsurance","vehicleDeleted","noTrailer","trailerInactive","trailerInsurance","trailerDeleted","mismatch","driverNoVehicleExplicit","missingCustomer","inactiveCustomer","missingProduct","inactiveProduct","missingDriver","deletedDriver","missingVehicle","inactiveExplicitVehicle","explicit","explicitBadDirection","badDirection","missingPrice","badPrice","badDate","missingCustomerField","aliases","defaults","batch","historySuccess","historyUnpublished","existingRoute","cancelledRoute","deletedRoute","duplicateBeforeBadPrice"})
    void routeBehaviorCharacterization(String scenario) throws Exception {
        var raw=raw();String explicit=null,key=KEY,commitKey=KEY,status="PUBLISHED",code=null,message=null;
        Long assigned=1L,vehicle=1L;
        boolean existing=false;
        switch(scenario){
            case "noDriver" -> {raw.remove("assignedDriverId");assigned=null;vehicle=null;status="UNPUBLISHED";}
            case "noDriverVehicle" -> {raw.remove("assignedDriverId");raw.put("vehicleId",2);assigned=null;vehicle=2L;status="UNPUBLISHED";}
            case "driverNoVehicle" -> {jdbc.update("DELETE FROM driver_vehicle_current");vehicle=null;status="UNPUBLISHED";}
            case "driverInactive" -> {jdbc.update("UPDATE driver SET status=0");vehicle=null;status="UNPUBLISHED";}
            case "vehicleInactive" -> {jdbc.update("UPDATE vehicle SET status=0 WHERE id=1");vehicle=null;status="UNPUBLISHED";}
            case "vehicleInsurance" -> {jdbc.update("UPDATE vehicle SET insurance_complete=0 WHERE id=1");vehicle=null;status="UNPUBLISHED";}
            case "vehicleDeleted" -> {jdbc.update("UPDATE vehicle SET deleted_at=CURRENT_TIMESTAMP WHERE id=1");vehicle=null;status="UNPUBLISHED";}
            case "noTrailer" -> jdbc.update("DELETE FROM vehicle_trailer_current");
            case "trailerInactive" -> {jdbc.update("UPDATE trailer SET status=0");vehicle=null;status="UNPUBLISHED";}
            case "trailerInsurance" -> {jdbc.update("UPDATE trailer SET insurance_complete=0");vehicle=null;status="UNPUBLISHED";}
            case "trailerDeleted" -> jdbc.update("UPDATE trailer SET deleted_at=CURRENT_TIMESTAMP");
            case "mismatch", "driverNoVehicleExplicit" -> {raw.put("vehicleId",2);if(scenario.equals("driverNoVehicleExplicit"))jdbc.update("DELETE FROM driver_vehicle_current");code="ROUTE_003";message="导入车辆与司机当前绑定车辆不匹配";}
            case "missingCustomer", "inactiveCustomer" -> {if(scenario.equals("missingCustomer"))jdbc.update("DELETE FROM customer");else jdbc.update("UPDATE customer SET status=0");code="IMPORT_REFERENCE_NOT_FOUND";message="客户或产品不存在";}
            case "missingProduct", "inactiveProduct" -> {if(scenario.equals("missingProduct"))jdbc.update("DELETE FROM product");else jdbc.update("UPDATE product SET status=0");code="IMPORT_REFERENCE_NOT_FOUND";message="客户或产品不存在";}
            case "missingDriver", "deletedDriver" -> {if(scenario.equals("missingDriver"))jdbc.update("DELETE FROM driver");else jdbc.update("UPDATE driver SET deleted_at=CURRENT_TIMESTAMP");code="ROUTE_003";message="司机车辆校验失败";}
            case "missingVehicle", "inactiveExplicitVehicle" -> {raw.remove("assignedDriverId");raw.put("vehicleId",99);if(scenario.equals("inactiveExplicitVehicle")){raw.put("vehicleId",1);jdbc.update("UPDATE vehicle SET status=0 WHERE id=1");}code="IMPORT_REFERENCE_NOT_FOUND";message="车辆不存在";}
            case "explicit" -> {explicit=" CUSTOM ";key="CUSTOM";commitKey=key;}
            case "explicitBadDirection", "badDirection" -> {raw.put("direction","bad");key="1|2026-09-10|bad|A|B";commitKey=key;if(scenario.equals("explicitBadDirection")){explicit=" CUSTOM ";commitKey="CUSTOM";}code="IMPORT_FORMAT_ERROR";message="线路方向不合法";}
            case "missingPrice", "badPrice" -> {if(scenario.equals("missingPrice"))raw.remove("taxUnitPrice");else raw.put("taxUnitPrice","bad");code="IMPORT_FORMAT_ERROR";message=scenario.equals("missingPrice")?"taxUnitPrice不能为空":"taxUnitPrice格式错误";key=null;commitKey=null;}
            case "badDate" -> {raw.put("businessDate","bad");code="IMPORT_FORMAT_ERROR";message="导入行格式错误";key=null;commitKey=null;}
            case "missingCustomerField" -> {raw.remove("customerId");code="IMPORT_FORMAT_ERROR";message="customerId不能为空";key=null;commitKey=null;}
            case "aliases" -> {raw.remove("assignedDriverId");raw.put("driverId",1);raw.put("importVehicleId",1);}
            case "defaults" -> {for(String f:List.of("carryingFee","infoFee","mileageKm","driverSalary","salarySource","externalRouteNo","tripSequence"))raw.remove(f);}
            case "batch" -> {code="IMPORT_DUPLICATE_IN_BATCH";message="导入批次内重复";}
            case "historySuccess", "historyUnpublished" -> {long t=task();repository.insertRow(t,1,"{}",null,"SUCCESS",null,null,"ROUTE",KEY);repository.markRowFinal(repository.findRows(t).get(0).id(),scenario.equals("historySuccess")?"SUCCESS":"UNPUBLISHED",null,null,"ROUTE",99L,KEY);code="IMPORT_DUPLICATE_EXISTING";message="业务数据已存在";}
            case "existingRoute", "cancelledRoute", "deletedRoute", "duplicateBeforeBadPrice" -> {existing=true;jdbc.update("INSERT INTO route_task (route_no,business_unique_key,business_date,customer_id,product_id,direction,loading_place,unloading_place,tax_unit_price,status,created_by) VALUES ('OLD',?,'2026-09-10',1,1,'OUTBOUND','A','B',10,?,7)",KEY,scenario.equals("cancelledRoute")?"CANCELLED":"PUBLISHED");if(scenario.equals("deletedRoute"))jdbc.update("UPDATE route_task SET deleted_at=CURRENT_TIMESTAMP");else if(!scenario.equals("cancelledRoute")){code="IMPORT_DUPLICATE_EXISTING";message="业务数据已存在";}if(scenario.equals("duplicateBeforeBadPrice"))raw.put("taxUnitPrice","bad");}
        }
        var batch=new HashSet<String>();if(scenario.equals("batch"))batch.add(KEY);
        var preview=rows.preparePreview("ROUTE",new ImportPreviewRequest.ImportRowRequest(1,raw,explicit),batch);
        assertThat(preview.validation().success()).isEqualTo(code==null);assertThat(preview.businessUniqueKey()).isEqualTo(key);
        assertThat(preview.validation().errorCode()).isEqualTo(code);assertThat(preview.validation().errorMessage()).isEqualTo(message);
        long task=task();repository.insertRow(task,1,mapper.writeValueAsString(raw),null,"SUCCESS",null,null,"ROUTE",explicit);
        var commitBatch=new HashSet<String>();if(scenario.equals("batch"))commitBatch.add(KEY);
        rows.commitRow("ROUTE",repository.findRows(task).get(0),commitBatch,7);
        var result=repository.findRows(task).get(0);
        assertThat(result.finalStatus()).isEqualTo(code!=null?"FAILURE":status.equals("PUBLISHED")?"SUCCESS":"UNPUBLISHED");
        assertThat(result.finalErrorCode()).isEqualTo(code);assertThat(result.finalErrorMessage()).isEqualTo(message);assertThat(result.businessUniqueKey()).isEqualTo(commitKey);
        if(code==null){
            long id=jdbc.queryForObject("SELECT id FROM route_task WHERE route_no='R-IMPORT'",Long.class);assertThat(result.businessId()).isEqualTo(id);
            var saved=jdbc.queryForMap("SELECT * FROM route_task WHERE id=?",id);
            assertThat(saved).containsEntry("business_unique_key",commitKey).containsEntry("status",status).containsEntry("assigned_driver_id",assigned).containsEntry("import_vehicle_id",vehicle).containsEntry("customer_id",1L).containsEntry("product_id",1L).containsEntry("business_date",java.sql.Date.valueOf("2026-09-10")).containsEntry("direction","OUTBOUND").containsEntry("loading_place","A").containsEntry("unloading_place","B").containsEntry("created_by",7L);
            assertThat(saved).containsEntry("departure_driver_id",null).containsEntry("departure_vehicle_id",null).containsEntry("departure_trailer_id",null);
            assertThat(saved.get("created_at")).isNotNull();
            assertThat((java.math.BigDecimal)saved.get("tax_unit_price")).isEqualByComparingTo("12.3456");
            boolean defaults=scenario.equals("defaults");
            assertThat((java.math.BigDecimal)saved.get("carrying_fee")).isEqualByComparingTo(defaults?"0":"10.20");
            assertThat((java.math.BigDecimal)saved.get("info_fee")).isEqualByComparingTo(defaults?"0":"3.40");
            if(defaults)assertThat(saved).containsEntry("mileage_km",null).containsEntry("driver_salary",null).containsEntry("salary_source",null).containsEntry("external_route_no",null).containsEntry("trip_sequence",null);
            else {assertThat((java.math.BigDecimal)saved.get("mileage_km")).isEqualByComparingTo("123.45");assertThat((java.math.BigDecimal)saved.get("driver_salary")).isEqualByComparingTo("50.60");assertThat(saved).containsEntry("salary_source","IMPORT").containsEntry("external_route_no","EXT").containsEntry("trip_sequence","02");}
            var history=jdbc.queryForMap("SELECT * FROM route_status_history WHERE route_id=?",id);
            assertThat(history).containsEntry("from_status",null).containsEntry("to_status",status).containsEntry("operation_type","IMPORT").containsEntry("operator_id",7L).containsEntry("reason",null);
            assertThat(history.get("operation_time")).isNotNull();
        }else {assertThat(result.businessId()).isNull();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM route_task",Integer.class)).isEqualTo(existing?1:0);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM route_status_history",Integer.class)).isZero();}
    }

    @Autowired java.util.List<com.dfwl.fleet.imports.spi.ImportBusinessHandler> handlers;
    @org.springframework.boot.test.mock.mockito.SpyBean com.dfwl.fleet.route.imports.RouteImportHandler routeHandler;
    @org.springframework.boot.test.mock.mockito.SpyBean com.dfwl.fleet.route.repository.RouteImportRepository routeImports;

    @org.junit.jupiter.api.Test
    void discoveryAndDispatch() throws Exception {
        assertThat(handlers.stream().map(h->h.businessType().name())).containsExactlyInAnyOrder("SALARY","TIRE","EXPENSE","ROUTE");
        long task=prepare(raw());
        org.mockito.Mockito.verify(routeHandler).resolveBusinessKey(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(routeHandler).validateDuplicate(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(routeHandler).validate(org.mockito.ArgumentMatchers.any());
        assertThat(imports.commit(task,7).successCount()).isEqualTo(1);
        org.mockito.Mockito.verify(routeHandler).commit(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.clearInvocations(routeHandler);
        for(String type:List.of("SALARY","TIRE","EXPENSE"))rows.preparePreview(type,new ImportPreviewRequest.ImportRowRequest(1,Map.of(),null),new HashSet<>());
        org.mockito.Mockito.verifyNoInteractions(routeHandler);
    }

    @ParameterizedTest
    @ValueSource(strings={"UNBOUND","REBOUND","MISMATCH","DRIVER","VEHICLE","TRAILER","CUSTOMER","PRODUCT","NEW_DUPLICATE"})
    void commitReadsLatestBindingsAndReferences(String change) throws Exception {
        var raw=raw();if(change.equals("MISMATCH"))raw.put("vehicleId",1);
        long task=prepare(raw);
        switch(change){
            case "UNBOUND" -> jdbc.update("DELETE FROM driver_vehicle_current");
            case "REBOUND", "MISMATCH" -> jdbc.update("UPDATE driver_vehicle_current SET vehicle_id=2");
            case "DRIVER" -> jdbc.update("UPDATE driver SET status=0");
            case "VEHICLE" -> jdbc.update("UPDATE vehicle SET insurance_complete=0 WHERE id=1");
            case "TRAILER" -> jdbc.update("UPDATE trailer SET status=0");
            case "CUSTOMER" -> jdbc.update("UPDATE customer SET status=0");
            case "PRODUCT" -> jdbc.update("UPDATE product SET status=0");
            case "NEW_DUPLICATE" -> jdbc.update("INSERT INTO route_task (route_no,business_unique_key,business_date,customer_id,product_id,direction,loading_place,unloading_place,tax_unit_price,status,created_by) VALUES ('NEW',?,'2026-09-10',1,1,'OUTBOUND','A','B',10,'PUBLISHED',7)",KEY);
        }
        var result=imports.commit(task,7);var row=result.rows().get(0);
        if(Set.of("MISMATCH","CUSTOMER","PRODUCT","NEW_DUPLICATE").contains(change)) {
            assertThat(row.finalStatus()).isEqualTo("FAILURE");
            assertThat(row.finalErrorCode()).isEqualTo(change.equals("MISMATCH")?"ROUTE_003":change.equals("NEW_DUPLICATE")?"IMPORT_DUPLICATE_EXISTING":"IMPORT_REFERENCE_NOT_FOUND");
            assertThat(row.businessId()).isNull();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM route_status_history",Integer.class)).isZero();
        } else {
            boolean published=change.equals("REBOUND");
            assertThat(row.finalStatus()).isEqualTo(published?"SUCCESS":"UNPUBLISHED");
            assertThat(result.unpublishedCount()).isEqualTo(published?0:1);
            assertThat(jdbc.queryForMap("SELECT * FROM route_task WHERE id=?",row.businessId())).containsEntry("status",published?"PUBLISHED":"UNPUBLISHED").containsEntry("import_vehicle_id",published?2L:null);
            assertThat(jdbc.queryForObject("SELECT to_status FROM route_status_history",String.class)).isEqualTo(published?"PUBLISHED":"UNPUBLISHED");
        }
        var saved=repository.findRows(task);imports.commit(task,7);assertThat(repository.findRows(task)).isEqualTo(saved);
    }

    @ParameterizedTest
    @ValueSource(strings={"HISTORY_PUBLISHED","HISTORY_UNPUBLISHED","FINAL_PUBLISHED","FINAL_UNPUBLISHED"})
    void rowWritesAreAtomicAndOtherRowsContinue(String stage) throws Exception {
        var raw=raw();boolean unpublished=stage.endsWith("UNPUBLISHED");if(unpublished)raw.remove("assignedDriverId");
        long task=prepare(raw);long first=repository.findRows(task).get(0).id();
        var second=raw();second.put("routeNo","SECOND");second.put("unloadingPlace","C");
        repository.insertRow(task,2,mapper.writeValueAsString(second),null,"SUCCESS",null,null,"ROUTE","1|2026-09-10|OUTBOUND|A|C");
        org.mockito.stubbing.Answer<Object> fail=invocation->{invocation.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("injected "+stage);};
        if(stage.startsWith("HISTORY")){
            java.util.concurrent.atomic.AtomicBoolean firstCall=new java.util.concurrent.atomic.AtomicBoolean(true);
            org.mockito.Mockito.doAnswer(invocation->{Object result=invocation.callRealMethod();if(firstCall.getAndSet(false))throw new org.springframework.dao.DataAccessResourceFailureException("injected history");return result;})
                .when(routeImports).insertRouteStatusHistory(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong());
        } else org.mockito.Mockito.doAnswer(fail).when(repository).markRowFinal(org.mockito.ArgumentMatchers.eq(first),org.mockito.ArgumentMatchers.eq(unpublished?"UNPUBLISHED":"SUCCESS"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("ROUTE"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        var result=imports.commit(task,7);assertThat(result.successCount()).isEqualTo(1);assertThat(result.failureCount()).isEqualTo(1);assertThat(result.unpublishedCount()).isZero();
        assertThat(result.rows().get(0).finalErrorCode()).isEqualTo("IMPORT_DB_ERROR");assertThat(result.rows().get(0).businessId()).isNull();
        assertThat(jdbc.queryForList("SELECT route_no FROM route_task",String.class)).containsExactly("SECOND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM route_status_history",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT route_id FROM route_status_history",Long.class)).isEqualTo(result.rows().get(1).businessId());
        var saved=repository.findRows(task);imports.commit(task,7);assertThat(repository.findRows(task)).isEqualTo(saved);
    }

    long prepare(Map<String,Object> raw) throws Exception {
        var p=rows.preparePreview("ROUTE",new ImportPreviewRequest.ImportRowRequest(1,raw,null),new HashSet<>());
        assertThat(p.validation().success()).isTrue();long t=task();repository.insertRow(t,1,mapper.writeValueAsString(raw),null,"SUCCESS",null,null,"ROUTE",p.businessUniqueKey());return t;
    }
    long task(){return repository.createTask(UUID.randomUUID().toString(),"ROUTE",null,1L,1,0,0,7);}
}
