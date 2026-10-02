<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { v3FinanceApi } from '../api/modules.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';

const props = defineProps({ scope: { type: String, default: 'TASK' } });
const today = new Date(); const filters = reactive({ month: `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}` });
const title = computed(() => ({ TASK: '任务经营档案', TEMPLATE: '线路模板经营', COMPANY: '公司经营总览' })[props.scope]);
const rows = ref([]); const company = ref(null); const selected = ref(null);
const selectedTemplate = ref(null);
const pageNo = ref(1); const total = ref(0); const loading = ref(false); const error = ref('');
const taskCols = [
  { key: 'task_no', label: '任务号' }, { key: 'template_name_snapshot', label: '线路模板' },
  { key: 'customer_name_snapshot', label: '客户' }, { key: 'net_weight', label: '有效净重' },
  { key: 'actual_unit_price', label: '实际单价', numeric: true }, { key: 'income_amount', label: '收入', numeric: true },
  { key: 'information_fee_amount', label: '信息费', numeric: true }, { key: 'salary_amount', label: '参与工资', numeric: true },
  { key: 'task_expense_amount', label: '归属费用净额', numeric: true }, { key: 'total_expense_amount', label: '总支出', numeric: true },
  { key: 'profit_amount', label: '利润', numeric: true }, { key: 'actions', label: '操作' }
];
const templateCols = [
  { key: 'template_code', label: '线路编号' }, { key: 'template_name', label: '线路名称' },
  { key: 'customer_name', label: '客户' }, { key: 'product_name', label: '货物' },
  { key: 'task_count', label: '已完成任务' }, { key: 'weight_total', label: '净重合计' },
  { key: 'income_total', label: '收入', numeric: true }, { key: 'information_fee_total', label: '信息费', numeric: true },
  { key: 'salary_total', label: '参与工资', numeric: true }, { key: 'task_expense_total', label: '归属费用', numeric: true },
  { key: 'profit_total', label: '利润', numeric: true }, { key: 'actions', label: '操作' }
];
const expenseCols = [
  { key: 'expense_no', label: '费用编号' }, { key: 'business_date', label: '日期' },
  { key: 'expense_type', label: '类型' }, { key: 'application_scope', label: '申请属性' },
  { key: 'source_type', label: '来源' }, { key: 'driver_name', label: '司机' },
  { key: 'vehicle_plate', label: '公司车牌' }, { key: 'description', label: '明细' },
  { key: 'amount', label: '金额', numeric: true }, { key: 'attribution_method', label: '当前归属来源' },
  { key: 'approval_no', label: '审批单' }, { key: 'display_status', label: '状态' }
];
const typeNames = { REPAIR: '维修费', PENALTY: '罚款', WATER: '水费', TEMP_ELECTRIC: '临时电费', TEMP_GAS: '临时气费', TOLL: '过路费', DAILY: '日常费用', TASK: '任务费用' };
const money = (v) => `¥ ${Number(v || 0).toFixed(2)}`;
const load = async (page = 1) => {
  loading.value = true; error.value = '';
  try {
    if (props.scope === 'COMPANY') { company.value = await v3FinanceApi.company(filters.month); rows.value = []; total.value = 0; }
    else {
      const result = props.scope === 'TASK' || selectedTemplate.value
        ? await v3FinanceApi.tasks({ month: filters.month, templateId: selectedTemplate.value?.template_id }, page)
        : await v3FinanceApi.templates({ month: filters.month }, page);
      rows.value = result.records || []; total.value = Number(result.total || 0);
    }
    pageNo.value = page;
  } catch (cause) { error.value = cause.message || '经营数据加载失败'; }
  finally { loading.value = false; }
};
async function openTask(id) {
  error.value = '';
  try { selected.value = await v3FinanceApi.task(id); }
  catch (cause) { error.value = cause.message || '任务经营档案加载失败'; }
}
watch(() => props.scope, () => { selected.value = null; load(1); });
function showTemplate(row) { selectedTemplate.value = row; load(1); }
function backToTemplates() { selectedTemplate.value = null; load(1); }
onMounted(() => load());
</script>

<template>
  <main class="ui-page-container">
    <PageHeader :title="title" description="收入、信息费、参与工资与正式费用取自同一业务事实；历史版本由月结保存。"><template #actions><button type="button" @click="load(pageNo)">刷新</button></template></PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load(pageNo)" />
    <FilterBar><label>月份<input v-model="filters.month" type="month" /></label><button type="button" @click="load(1)">查询</button></FilterBar>
    <template v-if="scope === 'COMPANY'">
      <section v-if="company" class="v3-summary" aria-label="公司经营汇总">
        <div><small>运输收入</small><strong>{{ money(company.incomeTotal) }}</strong></div>
        <div><small>信息费</small><strong>{{ money(company.informationFeeTotal) }}</strong></div>
        <div><small>参与工资</small><strong>{{ money(company.salaryTotal) }}</strong></div>
        <div><small>全部正式费用净额</small><strong>{{ money(company.expenseEntryTotal) }}</strong></div>
        <div><small>总支出</small><strong>{{ money(company.totalExpense) }}</strong></div>
        <div><small>利润</small><strong>{{ money(company.profitTotal) }}</strong></div>
      </section>
      <section v-if="company" class="ui-section"><h2>费用归属说明</h2><p>本月未归属任务的正式日常费用 {{ money(company.unassignedExpenseTotal) }}，已经计入公司总支出；后续归属任务不会再次增加公司支出。</p><p>收入按任务完成月份统计；信息费和参与工资按任务业务月份统计；正式费用按费用业务月份统计。</p></section>
      <p v-else-if="loading" class="ui-loading">正在加载经营汇总…</p>
    </template>
    <template v-else>
      <button v-if="selectedTemplate" type="button" @click="backToTemplates">← 返回线路模板汇总</button>
      <p class="muted">{{ scope === 'TASK' ? '按完成月份查看已完成任务经营事实。' : '按已完成任务汇总线路模板经营，模板默认金额不回写历史任务。' }}</p>
      <DataTable :columns="scope === 'TASK' || selectedTemplate ? taskCols : templateCols" :rows="rows" :row-key="scope === 'TASK' || selectedTemplate ? 'task_id' : 'template_id'" :loading="loading" empty-text="当前月份暂无已完成任务经营数据">
        <template #net_weight="{ row }">{{ row.net_weight == null ? '—' : `${row.net_weight} 吨` }}</template>
        <template #weight_total="{ row }">{{ row.weight_total }} 吨</template>
        <template #actual_unit_price="{ row }">{{ money(row.actual_unit_price) }} / 吨</template>
        <template #income_amount="{ row }">{{ money(row.income_amount) }}</template><template #information_fee_amount="{ row }">{{ money(row.information_fee_amount) }}</template>
        <template #salary_amount="{ row }">{{ money(row.salary_amount) }}</template><template #task_expense_amount="{ row }">{{ money(row.task_expense_amount) }}</template>
        <template #total_expense_amount="{ row }">{{ money(row.total_expense_amount) }}</template><template #profit_amount="{ row }">{{ money(row.profit_amount) }}</template>
        <template #income_total="{ row }">{{ money(row.income_total) }}</template><template #information_fee_total="{ row }">{{ money(row.information_fee_total) }}</template>
        <template #salary_total="{ row }">{{ money(row.salary_total) }}</template><template #task_expense_total="{ row }">{{ money(row.task_expense_total) }}</template>
        <template #profit_total="{ row }">{{ money(row.profit_total) }}</template>
        <template #actions="{ row }"><button type="button" @click="scope === 'TASK' || selectedTemplate ? openTask(row.task_id) : showTemplate(row)">{{ scope === 'TASK' || selectedTemplate ? '查看档案' : '查看任务明细' }}</button></template>
      </DataTable>
      <PaginationBar :page-no="pageNo" :page-size="20" :total="total" @change="load" />
    </template>
    <DetailDrawer :open="!!selected" title="任务经营档案" @close="selected = null">
      <template #subtitle>{{ selected?.summary?.task_no }}</template>
      <template v-if="selected">
        <section class="ui-section"><h2>经营结果</h2><dl class="ui-detail-list">
          <div><dt>客户 / 货物</dt><dd>{{ selected.summary.customer_name_snapshot }} · {{ selected.summary.product_name_snapshot }}</dd></div>
          <div><dt>运输线路</dt><dd>{{ selected.summary.loading_place_snapshot }} → {{ selected.summary.unloading_place_snapshot }}</dd></div>
          <div><dt>有效净重</dt><dd>{{ selected.summary.net_weight }} 吨</dd></div><div><dt>实际含税单价</dt><dd>{{ money(selected.summary.actual_unit_price) }} / 吨</dd></div>
          <div><dt>运输收入</dt><dd>{{ money(selected.summary.income_amount) }}</dd></div><div><dt>实际信息费</dt><dd>{{ money(selected.summary.information_fee_amount) }}</dd></div>
          <div><dt>参与工资</dt><dd>{{ money(selected.summary.salary_amount) }}</dd></div><div><dt>归属正式费用净额</dt><dd>{{ money(selected.summary.task_expense_amount) }}</dd></div>
          <div><dt>总支出</dt><dd>{{ money(selected.summary.total_expense_amount) }}</dd></div><div><dt>利润</dt><dd>{{ money(selected.summary.profit_amount) }}</dd></div>
        </dl></section>
        <section class="ui-section"><h2>参与司机与当时车辆</h2><DataTable :columns="[
          { key: 'sequence_no', label: '参与顺序' }, { key: 'driver_name', label: '司机' },
          { key: 'driver_type_snapshot', label: '类型' }, { key: 'vehicle_plate_snapshot', label: '当时车牌' },
          { key: 'trailer_plate_snapshot', label: '当时车挂' }, { key: 'salary_amount', label: '核定工资', numeric: true }
        ]" :rows="selected.participants" empty-text="暂无参与记录">
          <template #sequence_no="{ row }">{{ row.sequence_no === 1 ? '初始司机' : `第${row.sequence_no - 1}次接手` }}</template>
          <template #driver_type_snapshot="{ row }">{{ row.driver_type_snapshot === 'INTERNAL' ? '自有司机' : '外协司机' }}</template>
          <template #vehicle_plate_snapshot="{ row }">{{ row.driver_type_snapshot === 'INTERNAL' ? row.vehicle_plate_snapshot : row.outsourced_plate_no }}</template>
          <template #salary_amount="{ row }">{{ money(row.salary_amount) }}</template>
        </DataTable></section>
        <section class="ui-section"><h2>当前归属费用</h2><p>费用净额 {{ money(selected.summary.task_expense_amount) }}；<span v-for="type in selected.expenseTypes" :key="type.expense_type">{{ typeNames[type.expense_type] || '其他费用' }} {{ money(type.amount_total) }}（{{ type.entry_count }} 笔）　</span></p><DataTable :columns="expenseCols" :rows="selected.expenses" empty-text="当前没有归属此任务的正式费用">
          <template #expense_type="{ row }">{{ typeNames[row.expense_type] || '其他费用' }}</template><template #application_scope="{ row }">{{ typeNames[row.application_scope] }}</template>
          <template #source_type="{ row }">{{ row.source_type === 'REVERSAL' ? '费用冲销' : '审批通过' }}</template>
          <template #amount="{ row }">{{ money(row.amount) }}</template></DataTable></section>
      </template>
    </DetailDrawer>
  </main>
</template>
