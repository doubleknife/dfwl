package com.dfwl.fleet.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.finance.v3.ExpenseLedgerService;
import com.dfwl.fleet.finance.v3.FinanceFactsService;
import com.dfwl.fleet.finance.v3.SettlementV3Service;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3finance;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class V3FinanceFactsTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired FinanceFactsService facts;
    @Autowired ExpenseLedgerService ledger;
    @Autowired SettlementV3Service settlement;

    @BeforeEach void seed() {
        for (String table : List.of("settlement_diff","settlement_detail","settlement_version","settlement_month",
                "expense_reversal_link","expense_attribution_history","audit_log","expense_entry",
                "task_weight_version","transport_participant","transport_task","route_template",
                "vehicle","driver","sys_user","product")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(1,'13900000001','HASH',2,1)");
        jdbc.update("INSERT INTO driver(id,user_id,driver_type,name,phone,id_card_no,driving_license_no,status) "
                + "VALUES(1,1,'INTERNAL','司机甲','13900000001','110105199001010010','TEST-DL-001',1)");
        jdbc.update("INSERT INTO vehicle(id,plate_no,vehicle_license_no,insurance_complete,energy_type,max_load,load_standard_type,load_standard_percent,status) "
                + "VALUES(1,'云A001','TEST-VL-001',1,'ELECTRIC',30,'PERCENT',0.9,1),"
                + "(2,'云A002','TEST-VL-002',1,'ELECTRIC',30,'PERCENT',0.9,1),"
                + "(3,'云A003','TEST-VL-003',1,'ELECTRIC',30,'PERCENT',0.9,1)");
        jdbc.update("INSERT INTO product(id,name) VALUES(1,'货物')");
        jdbc.update("""
                INSERT INTO route_template(id,stable_code,name,customer_name,product_id,loading_place,
                  unloading_place,default_unit_price,default_information_fee,default_driver_salary,
                  created_by,updated_by)
                VALUES(1,'T-1','测试线路','客户',1,'起点','终点',100,5,20,1,1)
                """);
        jdbc.update("""
                INSERT INTO transport_task(id,task_no,template_id,template_code_snapshot,template_name_snapshot,
                  customer_name_snapshot,product_name_snapshot,loading_place_snapshot,unloading_place_snapshot,
                  actual_unit_price,actual_information_fee,business_date,status,effective_weight_version_id,
                  completed_at,created_by)
                VALUES(1,'TASK-1',1,'T-1','测试线路','客户','货物','起点','终点',100,5,
                  DATE '2026-10-01','COMPLETED',1,TIMESTAMP '2026-10-01 15:00:00',1)
                """);
        jdbc.update("""
                INSERT INTO transport_participant(id,task_id,sequence_no,driver_id,driver_type_snapshot,
                  company_vehicle_id,vehicle_plate_snapshot,source_type,status,salary_amount,is_current)
                VALUES(1,1,1,1,'INTERNAL',1,'云A001','INITIAL','TRANSFERRED',20,0),
                      (2,1,2,1,'INTERNAL',2,'云A002','TRANSFER','COMPLETED',30,1)
                """);
        jdbc.update("""
                INSERT INTO task_weight_version(id,task_id,version_no,gross_weight,tare_weight,net_weight,
                  source_type,operator_id)
                VALUES(1,1,1,12,2,10,'UNLOAD',1)
                """);
        expense(1,"TASK",1,1,"7.00");
        expense(2,"DAILY",null,2,"11.00");
        expense(3,"DAILY",null,3,"3.00");
    }

    private void expense(int id, String scope, Integer taskId, int vehicleId, String amount) {
        jdbc.update("""
                INSERT INTO expense_entry(id,expense_no,application_scope,current_task_id,vehicle_id,driver_id,
                  expense_type,business_date,description,amount,source_type,source_record_key,created_by)
                VALUES(?,?,?,?,?,1,'REPAIR',DATE '2026-10-01','维修',?,'APPROVAL',?,1)
                """, id, "E-" + id, scope, taskId, vehicleId, new BigDecimal(amount), "K-" + id);
    }

    private BigDecimal amount(Map<String,Object> row, String key) { return new BigDecimal(row.get(key).toString()); }

    @Test void taskTemplateCompanyShareFactsAndDailyBindingMovesOnlyTaskCost() {
        assertThat(amount(facts.task(1), "income_amount")).isEqualByComparingTo("1000.00");
        assertThat(amount(facts.task(1), "salary_amount")).isEqualByComparingTo("50.00");
        assertThat(amount(facts.task(1), "information_fee_amount")).isEqualByComparingTo("5.00");
        assertThat(amount(facts.task(1), "task_expense_amount")).isEqualByComparingTo("7.00");
        assertThat(amount(facts.task(1), "profit_amount")).isEqualByComparingTo("938.00");
        assertThat(amount(facts.company("2026-10"), "expenseEntryTotal")).isEqualByComparingTo("21.00");
        assertThat(amount(facts.company("2026-10"), "profitTotal")).isEqualByComparingTo("924.00");
        assertThat(amount(facts.templates("2026-10",1,20).records().get(0), "profit_total"))
                .isEqualByComparingTo("938.00");
        // Version is read from the formal ledger row; no direct amount update is permitted.
        Map<?,?> entry = (Map<?,?>) ledger.detail(2).get("entry");
        ledger.attribute(2,new ExpenseLedgerService.Attribution(1L,"归属已完成任务",((Number) entry.get("version")).intValue()),1);
        assertThat(ledger.detail(2).get("attributionHistory")).asList().hasSize(1);
        assertThat(((Map<?,?>)ledger.detail(2).get("entry")).get("application_scope")).isEqualTo("DAILY");
        assertThat(amount(facts.task(1), "profit_amount")).isEqualByComparingTo("927.00");
        assertThat(amount(facts.company("2026-10"), "profitTotal")).isEqualByComparingTo("924.00");
        assertThat(facts.taskExpenses(1)).hasSize(2);
        assertThat(facts.taskExpenses(1).get(0)).containsKeys("approval_no", "driver_name", "attribution_method");
        assertThatThrownBy(() -> ledger.attribute(3,new ExpenseLedgerService.Attribution(1L,"错车",0),1))
                .isInstanceOf(BusinessException.class);
        assertThat(((Map<?,?>)ledger.detail(3).get("entry")).get("current_task_id")).isNull();
    }

    @Test void reversalKeepsOriginalAndWritesOneNegativeFact() {
        ledger.reverse(1,new ExpenseLedgerService.Reversal("金额错误","reverse-1"),1);
        assertThat(((Map<?,?>)ledger.detail(1).get("entry")).get("display_status")).isEqualTo("已冲销");
        assertThat(amount(facts.task(1),"task_expense_amount")).isEqualByComparingTo("0.00");
        assertThat(amount(facts.company("2026-10"),"expenseEntryTotal")).isEqualByComparingTo("14.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_reversal_link",Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> ledger.reverse(1,new ExpenseLedgerService.Reversal("重复","reverse-2"),1))
                .isInstanceOf(BusinessException.class);
    }

    @Test void ledgerExportUsesFullServerFilterRatherThanCurrentPage() throws Exception {
        var filter = new ExpenseLedgerService.Filter("2026-10", null, "DAILY", null,
                null, false, null, null, null);
        assertThat(ledger.list(filter, 1, 1).records()).hasSize(1);
        assertThat(ledger.list(filter, 1, 1).total()).isEqualTo(2);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(ledger.export(filter)))) {
            var sheet = workbook.getSheet("费用台账");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("日期");
        }
    }

    @Test void pendingAttributionCountUsesAllActiveUnboundExpensesAcrossMonthsAndPages() {
        var pending = new ExpenseLedgerService.Filter(null, null, null, null,
                null, false, null, null, "ACTIVE");
        assertThat(ledger.list(pending, 1, 1).total()).isEqualTo(2);
        assertThat(((Number) ledger.summary(pending).get("entry_count")).longValue()).isEqualTo(2);
        jdbc.update("UPDATE expense_entry SET business_date=DATE '2026-09-30' WHERE id=3");
        assertThat(((Number) ledger.summary(pending).get("entry_count")).longValue()).isEqualTo(2);
        jdbc.update("UPDATE expense_entry SET current_task_id=1 WHERE id=2");
        assertThat(((Number) ledger.summary(pending).get("entry_count")).longValue()).isEqualTo(1);
        ledger.reverse(3, new ExpenseLedgerService.Reversal("测试冲销", "pending-count-reversal"), 1);
        assertThat(((Number) ledger.summary(pending).get("entry_count")).longValue()).isZero();
        assertThat(ledger.list(pending, 1, 1).total()).isZero();
    }

    @Test void settlementVersionsAreImmutableAndExplainChangedFacts() {
        Map<String,Object> first = settlement.generate("2026-10",1);
        long v1 = ((Number)first.get("id")).longValue();
        String frozen = jdbc.queryForObject("SELECT fact_snapshot_json FROM settlement_detail "
                + "WHERE version_id=? AND fact_type='TASK'",String.class,v1);
        jdbc.update("UPDATE task_weight_version SET net_weight=11,gross_weight=13,version_no=2 WHERE id=1");
        jdbc.update("UPDATE transport_task SET actual_unit_price=110,actual_information_fee=6 WHERE id=1");
        jdbc.update("UPDATE transport_participant SET salary_amount=35 WHERE id=2");
        Map<?,?> entry = (Map<?,?>)ledger.detail(2).get("entry");
        ledger.attribute(2,new ExpenseLedgerService.Attribution(1L,"归属",((Number)entry.get("version")).intValue()),1);
        ledger.reverse(1,new ExpenseLedgerService.Reversal("冲销","r1"),1);
        Map<String,Object> second = settlement.generate("2026-10",1);
        long v2 = ((Number)second.get("id")).longValue();
        assertThat(v2).isNotEqualTo(v1);
        assertThat(jdbc.queryForObject("SELECT fact_snapshot_json FROM settlement_detail "
                + "WHERE version_id=? AND fact_type='TASK'",String.class,v1)).isEqualTo(frozen);
        assertThat(settlement.diffs(v2,1,20).total()).isGreaterThan(0);
        assertThat(amount(second,"income_total")).isEqualByComparingTo("1210.00");
        assertThat(amount(second,"expense_total")).isEqualByComparingTo("75.00");
        assertThat(amount(second,"profit_total")).isEqualByComparingTo("1135.00");
        assertThat(settlement.diffs(v1,1,20).total()).isZero();
    }

    @Test void settlementDetailsSortByFrozenCompletionAndPostingTimeAcrossPages() {
        jdbc.update("UPDATE expense_entry SET business_date=DATE '2026-10-03',created_at=TIMESTAMP '2026-10-01 17:00:00' WHERE id=2");
        jdbc.update("UPDATE expense_entry SET business_date=DATE '2026-10-02',created_at=TIMESTAMP '2026-10-01 16:00:00' WHERE id=3");
        jdbc.update("UPDATE expense_entry SET created_at=TIMESTAMP '2026-10-01 14:00:00' WHERE id=1");
        long id = ((Number) settlement.generate("2026-10", 1).get("id")).longValue();
        assertThat(settlement.details(id, 1, 2).records()).extracting(row -> row.get("businessNo"))
                .containsExactly("E-2", "E-3");
        assertThat(settlement.details(id, 2, 2).records()).extracting(row -> row.get("businessNo"))
                .containsExactly("TASK-1", "E-1");
        jdbc.update("UPDATE transport_task SET completed_at=TIMESTAMP '2026-10-31 12:00:00' WHERE id=1");
        jdbc.update("UPDATE expense_entry SET created_at=TIMESTAMP '2026-10-31 13:00:00' WHERE id=1");
        assertThat(settlement.details(id, 2, 2).records()).extracting(row -> row.get("businessNo"))
                .containsExactly("TASK-1", "E-1");
    }

    @Test void settlementDifferenceExposesApprovalIdFromFrozenExpenseSnapshot() {
        settlement.generate("2026-10", 1);
        jdbc.update("UPDATE expense_entry SET approval_instance_id=42 WHERE id=2");
        long id = ((Number) settlement.generate("2026-10", 1).get("id")).longValue();
        assertThat(settlement.diffs(id, 1, 20).records()).filteredOn(row -> "E-2".equals(row.get("businessNo")))
                .singleElement().satisfies(row ->
                        assertThat(((Number) row.get("approvalId")).longValue()).isEqualTo(42));
    }

    @Test void sameFactsCreateASecondImmutableVersionWithoutFalseDifferences() {
        settlement.generate("2026-10",1);
        Map<String,Object> second = settlement.generate("2026-10",1);
        long id = ((Number)second.get("id")).longValue();
        assertThat(settlement.diffs(id,1,20).total()).as("unchanged snapshots: %s", jdbc.queryForList(
                "SELECT fact_key,before_snapshot_json,after_snapshot_json FROM settlement_diff WHERE version_id=?",id)).isZero();
    }

    @Test void taskExpenseDifferenceNamesTheActualChangedExpenseAttribution() {
        settlement.generate("2026-10",1);
        ledger.attribute(2,new ExpenseLedgerService.Attribution(1L,"本次归属原因",0),1);
        Map<String,Object> second = settlement.generate("2026-10",1);
        long id = ((Number)second.get("id")).longValue();
        assertThat(settlement.diffs(id,1,20).records()).anySatisfy(row -> {
            assertThat(row.get("businessNo")).isEqualTo("TASK-1");
            assertThat(row.get("changeType")).isEqualTo("任务费用变化");
            assertThat(row.get("reason")).isEqualTo("本次归属原因");
            assertThat(row.get("operator")).isEqualTo("13900000001");
            assertThat(row.get("operatedAt")).isNotNull();
        });
    }
}
