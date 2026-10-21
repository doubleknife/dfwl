package com.dfwl.fleet.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.finance.v3.ChargingImportService;
import com.dfwl.fleet.finance.v3.FinanceFactsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3charging;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql",
        "fleet.ocr.provider=DISABLED"
})
class V3ChargingImportTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ChargingImportService charging;
    @Autowired FinanceFactsService finance;
    @Autowired ObjectMapper json;

    @BeforeEach void setup() {
        for (String table : List.of("charging_source_order", "charging_import_batch", "charging_company",
                "expense_attribution_history", "expense_entry", "transport_participant", "transport_task",
                "route_template", "vehicle", "driver", "product")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO product(id,name) VALUES(1,'砂石')");
        jdbc.update("INSERT INTO driver(id,driver_type,name,phone,id_card_no,driving_license_no,status) VALUES(1,'INTERNAL','司机','13900000111','110105199001010010','TEST-DL-001',1)");
        jdbc.update("""
                INSERT INTO vehicle(id,plate_no,vehicle_license_no,insurance_complete,energy_type,max_load,load_standard_type,
                  load_standard_percent,status) VALUES(1,'云A001','TEST-VL-001',1,'ELECTRIC',30,'PERCENT',0.9,1)
                """);
        jdbc.update("""
                INSERT INTO route_template(id,stable_code,name,customer_name,product_id,loading_place,
                  unloading_place,default_unit_price,default_information_fee,default_driver_salary,created_by,updated_by)
                VALUES(1,'T-1','测试线路','客户',1,'昆明','曲靖',100,5,20,1,1)
                """);
        jdbc.update("""
                INSERT INTO transport_task(id,task_no,template_id,template_code_snapshot,template_name_snapshot,
                  customer_name_snapshot,product_name_snapshot,loading_place_snapshot,unloading_place_snapshot,
                  actual_unit_price,actual_information_fee,business_date,status,completed_at,created_by)
                VALUES(1,'TASK-1',1,'T-1','测试线路','客户','砂石','昆明','曲靖',100,5,
                  DATE '2026-08-15','COMPLETED',TIMESTAMP '2026-08-15 16:00:00',1)
                """);
        jdbc.update("""
                INSERT INTO transport_participant(id,task_id,sequence_no,driver_id,driver_type_snapshot,
                  company_vehicle_id,vehicle_plate_snapshot,source_type,status,salary_amount,is_current,
                  departed_at,ended_at)
                VALUES(1,1,1,1,'INTERNAL',1,'云A001','INITIAL','COMPLETED',20,1,
                  TIMESTAMP '2026-08-15 08:00:00',TIMESTAMP '2026-08-15 16:00:00')
                """);
    }

    private long company(String source) {
        var headers = List.of(new ChargingImportService.Header(0,"订单号"),
                new ChargingImportService.Header(1,"车牌号"),
                new ChargingImportService.Header(2,"充电时间"),
                new ChargingImportService.Header(3,"入账金额"));
        var mapping = Map.of("orderNo",0,"plateNo",1,"chargedAt",2,"amount",3);
        var saved = charging.saveCompany(null, new ChargingImportService.CompanyCommand(
                "充电公司"+source,1,headers,mapping),1);
        return ((Number)saved.get("id")).longValue();
    }

    @Test void newCompanyGeneratesStableSourceAndFixedOrderType() {
        long id = company("GEN");
        var created = charging.company(id);
        String source = String.valueOf(created.get("source_system"));
        assertThat(source).matches("[A-Z][0-9]{7}");
        assertThat(created.get("order_no_type")).isEqualTo("ORDER_NO");
        assertThat(charging.company(company("SECOND")).get("source_system")).isNotEqualTo(source);

        var changed = charging.saveCompany(id, new ChargingImportService.CompanyCommand(
                "重命名充电公司", null, null, null), 1);
        assertThat(changed.get("company_name")).isEqualTo("重命名充电公司");
        assertThat(changed.get("source_system")).isEqualTo(source);
        assertThat(changed.get("order_no_type")).isEqualTo("ORDER_NO");
        assertThatThrownBy(() -> charging.saveCompany(null,
                new ChargingImportService.CompanyCommand("未配置样表", null, null, null), 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("上传样本");
    }

    private MockMultipartFile file(String orderNo, String amount) throws Exception {
        return file(orderNo, amount, false);
    }

    private MockMultipartFile file(String orderNo, String amount, boolean unpreviewedAmountRow) throws Exception {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("订单");
            var header = sheet.createRow(0);
            String[] labels = {"订单号","车牌号","充电时间","入账金额"};
            for (int i=0; i<labels.length; i++) header.createCell(i).setCellValue(labels[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(orderNo);
            row.createCell(1).setCellValue("云A001");
            row.createCell(2).setCellValue("2026-08-15 10:00:00");
            row.createCell(3).setCellValue(Double.parseDouble(amount));
            if (unpreviewedAmountRow) sheet.createRow(2).createCell(3).setCellValue(2.00);
            workbook.write(bytes);
            return new MockMultipartFile("file","orders.xlsx","application/octet-stream",bytes.toByteArray());
        }
    }

    private ChargingImportService.Confirmation confirm(long companyId, String orderNo, String amount) {
        return new ChargingImportService.Confirmation(companyId,"订单",1,
                List.of(new ChargingImportService.Header(0,"订单号"),new ChargingImportService.Header(1,"车牌号"),
                        new ChargingImportService.Header(2,"充电时间"),new ChargingImportService.Header(3,"入账金额")),
                Map.of("orderNo",0,"plateNo",1,"chargedAt",2,"amount",3),
                List.of(new ChargingImportService.Order(2,"订单",orderNo,"云A001",
                        LocalDateTime.parse("2026-08-15T10:00:00"),new BigDecimal(amount))),
                1, new BigDecimal(amount).signum() == 0 ? 1 : 0, new BigDecimal(amount));
    }

    @Test void oneExpenseWithSourceFileAndUniqueTaskAndDuplicateIsIdempotent() throws Exception {
        long id = company("C1");
        String key = "123456789012345678901234567890";
        var first = charging.confirm(confirm(id,key,"12.34"),file(key,"12.34"),1);
        assertThat(((Map<?,?>)first.get("batch")).get("imported_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry",Integer.class)).isEqualTo(1);
        long expenseId = jdbc.queryForObject("SELECT id FROM expense_entry",Long.class);
        assertThat(charging.sourceOrder(expenseId).get("original_order_no")).isEqualTo(key);
        assertThat(jdbc.queryForObject("SELECT current_task_id FROM expense_entry",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT driver_id FROM expense_entry",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT expense_type FROM expense_entry",String.class)).isEqualTo("ELECTRIC");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_attribution_history",Integer.class)).isEqualTo(1);
        assertThat(charging.originalFile(((Number)((Map<?,?>)first.get("batch")).get("id")).longValue()).exists())
                .isTrue();
        charging.confirm(confirm(id,key,"12.34"),file(key,"12.34"),1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry",Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> charging.confirm(confirm(id,key,"13.00"),file(key,"13.00"),1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("金额不一致");
        assertThat(new BigDecimal(finance.company("2026-08").get("expenseEntryTotal").toString()))
                .isEqualByComparingTo("12.34");
    }

    @Test void forgedPreviewIsRejectedAndDifferentSourceCanUseSameOrderNo() throws Exception {
        long first = company("C2");
        String key = "ORDER-1";
        assertThatThrownBy(() -> charging.confirm(confirm(first,key,"99.00"),file(key,"12.34"),1))
                .isInstanceOf(BusinessException.class);
        charging.confirm(confirm(first,key,"12.34"),file(key,"12.34"),1);
        long second = company("C3");
        charging.confirm(confirm(second,key,"12.34"),file(key,"12.34"),1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry",Integer.class)).isEqualTo(2);
    }

    @Test void matchedTransferUsesDriverWhoParticipatedAtChargingTime() throws Exception {
        jdbc.update("INSERT INTO driver(id,driver_type,name,phone,id_card_no,driving_license_no,status) "
                + "VALUES(2,'INTERNAL','接手司机','13900000222','110105199001010020','TEST-DL-002',1)");
        jdbc.update("UPDATE transport_participant SET is_current=0,ended_at=TIMESTAMP '2026-08-15 09:00:00' WHERE id=1");
        jdbc.update("""
                INSERT INTO transport_participant(id,task_id,sequence_no,driver_id,driver_type_snapshot,
                  company_vehicle_id,vehicle_plate_snapshot,source_type,status,salary_amount,is_current,
                  departed_at,ended_at)
                VALUES(2,1,2,2,'INTERNAL',1,'云A001','TRANSFER','COMPLETED',20,1,
                  TIMESTAMP '2026-08-15 09:00:00',TIMESTAMP '2026-08-15 16:00:00')
                """);
        long companyId = company("TRANSFER");
        charging.confirm(confirm(companyId,"TRANSFER-ORDER","12.34"),file("TRANSFER-ORDER","12.34"),1);
        assertThat(jdbc.queryForObject("SELECT current_task_id FROM expense_entry", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT driver_id FROM expense_entry", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT expense_type FROM expense_entry", String.class)).isEqualTo("ELECTRIC");
    }

    @Test void amountOnlyOrderRowCannotBeSilentlyOmitted() throws Exception {
        long companyId = company("MISSING");
        assertThatThrownBy(() -> charging.confirm(confirm(companyId, "ORDER-1", "12.34"),
                file("ORDER-1", "12.34", true), 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("未纳入预览");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM charging_import_batch", Integer.class)).isZero();
    }

    @Test void clientCannotCountOneOriginalRowTwice() throws Exception {
        long companyId = company("ROW");
        var original = confirm(companyId, "ORDER-1", "12.34");
        var duplicated = new ChargingImportService.Confirmation(companyId, original.sheetName(),
                original.headerRowNo(), original.headers(), original.mapping(),
                List.of(original.orders().getFirst(), original.orders().getFirst()), 2, 0,
                new BigDecimal("24.68"));
        assertThatThrownBy(() -> charging.confirm(duplicated, file("ORDER-1", "12.34"), 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("行号不能重复");
    }

    @Test void zeroOrderIsKeptAndDeduplicatedWithoutExpense() throws Exception {
        long companyId = company("ZERO");
        var first = charging.confirm(confirm(companyId,"ZERO-1","0.00"),file("ZERO-1","0.00"),1);
        var firstBatch = (Map<?, ?>) first.get("batch");
        assertThat(firstBatch.get("confirmed_count")).isEqualTo(1);
        assertThat(firstBatch.get("imported_count")).isEqualTo(0);
        assertThat(firstBatch.get("zero_count")).isEqualTo(1);
        assertThat(firstBatch.get("duplicate_count")).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM charging_source_order", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT expense_id FROM charging_source_order WHERE original_order_no='ZERO-1'", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Integer.class)).isZero();
        assertThat(charging.originalFile(((Number) firstBatch.get("id")).longValue()).exists()).isTrue();
        var repeated = charging.confirm(confirm(companyId,"ZERO-1","0.00"),file("ZERO-1","0.00"),1);
        var repeatBatch = (Map<?, ?>) repeated.get("batch");
        assertThat(repeatBatch.get("confirmed_count")).isEqualTo(1);
        assertThat(repeatBatch.get("zero_count")).isEqualTo(0);
        assertThat(repeatBatch.get("duplicate_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM charging_source_order", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Integer.class)).isZero();
        var history = charging.batches(1, 1);
        assertThat(history.get("total")).isEqualTo(2L);
        assertThat((List<?>) history.get("items")).hasSize(1);
        assertThat((List<?>) charging.batches(2, 1).get("items")).hasSize(1);
    }

    @Test void noTaskOrAmbiguousTaskKeepsTheExpenseUnattributed() throws Exception {
        jdbc.update("UPDATE transport_participant SET departed_at=TIMESTAMP '2026-08-15 12:00:00' WHERE id=1");
        long companyId = company("MATCH");
        charging.confirm(confirm(companyId,"NO-TASK","3.00"),file("NO-TASK","3.00"),1);
        assertThat(jdbc.queryForObject("SELECT current_task_id FROM expense_entry WHERE source_record_key LIKE 'CHARGING_ORDER:%'", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT driver_id FROM expense_entry WHERE description='充电订单 NO-TASK'", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT expense_type FROM expense_entry WHERE description='充电订单 NO-TASK'", String.class))
                .isEqualTo("TEMP_ELECTRIC");
        jdbc.update("UPDATE transport_participant SET departed_at=TIMESTAMP '2026-08-15 08:00:00' WHERE id=1");
        jdbc.update("INSERT INTO transport_task(id,task_no,template_id,template_code_snapshot,template_name_snapshot,"
                + "customer_name_snapshot,product_name_snapshot,loading_place_snapshot,unloading_place_snapshot,"
                + "actual_unit_price,actual_information_fee,business_date,status,completed_at,created_by) "
                + "SELECT 2,'TASK-2',template_id,template_code_snapshot,template_name_snapshot,customer_name_snapshot,"
                + "product_name_snapshot,loading_place_snapshot,unloading_place_snapshot,actual_unit_price,"
                + "actual_information_fee,business_date,status,completed_at,created_by FROM transport_task WHERE id=1");
        jdbc.update("INSERT INTO transport_participant(id,task_id,sequence_no,driver_id,driver_type_snapshot,"
                + "company_vehicle_id,vehicle_plate_snapshot,source_type,status,salary_amount,is_current,departed_at,ended_at) "
                + "SELECT 2,2,sequence_no,driver_id,driver_type_snapshot,company_vehicle_id,vehicle_plate_snapshot,"
                + "source_type,status,salary_amount,is_current,departed_at,ended_at FROM transport_participant WHERE id=1");
        charging.confirm(confirm(companyId,"TWO-TASKS","4.00"),file("TWO-TASKS","4.00"),1);
        assertThat(jdbc.queryForObject("SELECT current_task_id FROM expense_entry WHERE description='充电订单 TWO-TASKS'", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT driver_id FROM expense_entry WHERE description='充电订单 TWO-TASKS'", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT expense_type FROM expense_entry WHERE description='充电订单 TWO-TASKS'", String.class))
                .isEqualTo("TEMP_ELECTRIC");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_attribution_history", Integer.class)).isZero();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CHARGING_SAMPLE_DIR", matches = ".+")
    void twelveRealFilesPassGenericServerVerificationAndReconcile() throws Exception {
        Path directory = Path.of(System.getenv("CHARGING_SAMPLE_DIR"));
        var samples = json.readTree(Files.readString(Path.of(System.getenv("CHARGING_SAMPLE_OUTPUT"))));
        assertThat(samples.size()).isEqualTo(12);
        var plates = new HashSet<String>();
        plates.add("云A001");
        for (var sample : samples) {
            System.out.println("核验真实样本：" + sample.get("name").asText());
            var payload = json.treeToValue(sample.get("payload"), ChargingImportService.Confirmation.class);
            assertThat(sample.get("errors").size()).isZero();
            for (var order : payload.orders()) if (plates.add(order.plateNo())) jdbc.update("""
                    INSERT INTO vehicle(plate_no,vehicle_license_no,insurance_complete,energy_type,max_load,
                      load_standard_type,load_standard_percent,status)
                    VALUES(?,CONCAT('TEST-VL-',?),1,'ELECTRIC',30,'PERCENT',0.9,1)
                    """, order.plateNo(), order.plateNo());
            var saved = charging.saveCompany(null, new ChargingImportService.CompanyCommand(
                    sample.get("company").asText(), payload.headerRowNo(), payload.headers(), payload.mapping()), 1);
            long companyId = ((Number) saved.get("id")).longValue();
            var confirmed = new ChargingImportService.Confirmation(companyId, payload.sheetName(),
                    payload.headerRowNo(), payload.headers(), payload.mapping(), payload.orders(),
                    payload.count(), payload.zeroCount(), payload.totalAmount());
            byte[] bytes = Files.readAllBytes(directory.resolve(sample.get("name").asText()));
            var file = new MockMultipartFile("file", sample.get("name").asText(),
                    "application/octet-stream", bytes);
            var result = charging.confirm(confirmed, file, 1);
            var batch = (Map<?, ?>) result.get("batch");
            int positive = ((Number) batch.get("imported_count")).intValue();
            int zero = ((Number) batch.get("zero_count")).intValue();
            int duplicates = ((Number) batch.get("duplicate_count")).intValue();
            assertThat(positive + zero + duplicates).isEqualTo(payload.count());
            assertThat(((Number) batch.get("confirmed_count")).intValue()).isEqualTo(payload.count());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM charging_import_batch", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM charging_source_order WHERE amount=0", Integer.class))
                .isPositive();
    }
}
