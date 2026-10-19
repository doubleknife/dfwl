<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { v3ExpenseLedgerApi, chargingApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import FormDialog from '../components/FormDialog.vue';
import EntityPicker from '../components/EntityPicker.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import SummaryCards from '../components/SummaryCards.vue';

const props = defineProps({ focus: { type: String, default: 'LEDGER' } });
const today = new Date();
const filters = reactive({ month: props.focus === 'UNASSIGNED' ? '' : `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`, expenseType: props.focus === 'ENERGY' ? 'TEMP_ELECTRIC' : '', applicationScope: '', driverId: '', vehicleId: '', bound: '', taskId: '', sourceType: '', status: '' });
const rows = ref([]); const summary = ref(null); const pageNo = ref(1); const total = ref(0);
const attributedSummary = ref(null); const reversalSummary = ref(null);
const loading = ref(false); const saving = ref(false); const exporting = ref(false); const error = ref('');
const moreFilters = ref(['VEHICLE', 'DRIVER'].includes(props.focus));
const detail = ref(null); const dialog = ref(''); const targetTaskId = ref(''); const reason = ref('');
const chargingSource = ref(null);
const categories = ref([]);
const cols = [
  { key: 'business_date', label: '业务日期' }, { key: 'expense_no', label: '费用编号' },
  { key: 'expense_type', label: '费用类型' }, { key: 'amount', label: '金额', numeric: true },
  { key: 'vehicle_plate', label: '公司车牌' }, { key: 'driver_name', label: '司机' },
  { key: 'task_no', label: '当前归属任务' }, { key: 'display_status', label: '状态' },
  { key: 'source_type', label: '来源' }, { key: 'application_scope', label: '申请属性' },
  { key: 'actions', label: '操作' }
];
const names = { REPAIR: '维修费', PENALTY: '罚款', WATER: '水费', ELECTRIC: '电费', GAS: '气费', TEMP_ELECTRIC: '临时电费', TEMP_GAS: '临时气费', TOLL: '过路费', DAILY: '日常费用', TASK: '任务费用', APPROVAL: '审批通过', REVERSAL: '冲销记录', CHARGING: '充电公司' };
const pageTitle = { VEHICLE: '车辆支出', DRIVER: '司机支出', ENERGY: '能源费用', UNASSIGNED: '待归属费用' };
const label = (value) => names[value] || value || '—';
const money = (value) => `¥ ${Number(value || 0).toFixed(2)}`;
const mask = (phone) => String(phone || '').replace(/^(\d{3})\d+(\d{4})$/, '$1****$2') || '系统账号';
const params = () => Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''));
const pendingParams = () => ({ month: filters.month, expenseType: filters.expenseType,
  driverId: filters.driverId, vehicleId: filters.vehicleId, sourceType: filters.sourceType });
const optionPage = (kind) => (page, search) => v3ExpenseLedgerApi.options(kind, search, page);
const matchingTaskPage = (page, search) => v3ExpenseLedgerApi.options('TASK', search, page, detail.value?.entry?.vehicle_id);

async function load(page = 1) {
  if (props.focus === 'DRIVER' && !filters.driverId) {
    rows.value = []; summary.value = null; attributedSummary.value = null; reversalSummary.value = null;
    total.value = 0; pageNo.value = 1; error.value = ''; loading.value = false;
    return;
  }
  loading.value = true; error.value = '';
  try {
    if (props.focus === 'UNASSIGNED') {
      const [result, totals] = await Promise.all([
        v3ExpenseLedgerApi.pendingAttribution(pendingParams(), page),
        v3ExpenseLedgerApi.pendingAttributionSummary(pendingParams())
      ]);
      rows.value = result.records || []; total.value = Number(result.total || 0);
      summary.value = totals; attributedSummary.value = null; reversalSummary.value = null; pageNo.value = page;
      return;
    }
    const current = params();
    const [result, totals, attributed, reversals] = await Promise.all([
      v3ExpenseLedgerApi.list(current, page), v3ExpenseLedgerApi.summary(current),
      v3ExpenseLedgerApi.summary({ ...current, bound: 'true' }),
      v3ExpenseLedgerApi.summary({ ...current, sourceType: 'REVERSAL' })
    ]);
    rows.value = result.records || []; total.value = Number(result.total || 0);
    summary.value = totals; attributedSummary.value = attributed; reversalSummary.value = reversals; pageNo.value = page;
  } catch (cause) { error.value = cause.message || '费用台账加载失败'; }
  finally { loading.value = false; }
}
async function openDetail(id) {
  error.value = '';
  chargingSource.value = null;
  try {
    detail.value = await v3ExpenseLedgerApi.detail(id);
    if (detail.value?.entry?.source_type === 'CHARGING' && can('PAGE_CHARGING_IMPORTS'))
      chargingSource.value = await chargingApi.sourceOrder(id);
  }
  catch (cause) { error.value = cause.message || '费用详情加载失败'; }
}
async function openChargingFile() {
  if (!chargingSource.value || !can('PAGE_CHARGING_IMPORTS')) return;
  try {
    const blob = await chargingApi.originalFile(chargingSource.value.batch_id);
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = chargingSource.value.original_filename; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) { error.value = cause.message || '原始 Excel 打开失败'; }
}
function begin(kind) { dialog.value = kind; reason.value = ''; targetTaskId.value = ''; }
async function save() {
  if (saving.value || !detail.value || !reason.value.trim()) return;
  if (dialog.value === 'attribute' && String(targetTaskId.value || '') === String(detail.value.entry.current_task_id || '')) return;
  saving.value = true; error.value = '';
  try {
    detail.value = dialog.value === 'attribute'
      ? await v3ExpenseLedgerApi.attribute(detail.value.entry.id, { targetTaskId: targetTaskId.value ? Number(targetTaskId.value) : null, expectedVersion: detail.value.entry.version, reason: reason.value.trim() })
      : await v3ExpenseLedgerApi.reverse(detail.value.entry.id, { reason: reason.value.trim(), requestKey: crypto.randomUUID() });
    dialog.value = ''; await load(pageNo.value);
  } catch (cause) { error.value = cause.message || '操作失败，请刷新事实后重试'; }
  finally { saving.value = false; }
}
async function download() {
  exporting.value = true; error.value = '';
  try {
    const blob = await v3ExpenseLedgerApi.export(params());
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = `费用台账-${filters.month || '全部'}.xlsx`; link.click(); URL.revokeObjectURL(url);
  } catch (cause) { error.value = cause.message || '下载失败'; }
  finally { exporting.value = false; }
}
function showUnassigned() {
  filters.applicationScope = 'DAILY';
  filters.bound = 'false';
  load(1);
}
onMounted(() => {
  v3ExpenseLedgerApi.options('CATEGORY').then((result) => { categories.value = result.records || []; })
    .catch((cause) => { error.value = cause.message || '费用大类加载失败'; });
  load();
});
watch(() => props.focus, (focus) => { filters.month = focus === 'UNASSIGNED' ? '' : `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`; filters.applicationScope = ''; filters.bound = ''; filters.expenseType = focus === 'ENERGY' ? 'TEMP_ELECTRIC' : ''; filters.vehicleId = ''; filters.driverId = ''; moreFilters.value = ['VEHICLE', 'DRIVER'].includes(focus); load(1); });
</script>

<template>
  <main class="ui-page-container">
    <PageHeader :title="pageTitle[focus] || '费用台账'" :description="focus === 'ENERGY' ? '按电费、气费等能源费用类型查看正式费用事实；汇总随当前筛选变化。' : focus === 'DRIVER' ? '选择司机查看其相关正式费用；参与人工资请在工资台账查看，不与费用重复相加。' : '查看正式费用、当前任务归属与调整历史；日常费用可在详情中调整归属。'">
      <template #actions><button v-if="focus === 'LEDGER'" type="button" @click="showUnassigned">待归属日常费用</button><button type="button" @click="load(pageNo)">刷新</button><button v-if="focus !== 'UNASSIGNED' && can('BUTTON_EXPENSE_LEDGER_EXPORT')" type="button" :disabled="exporting" @click="download">{{ exporting ? '正在下载…' : '下载当前筛选 Excel' }}</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load(pageNo)" />
    <FilterBar>
      <label>业务月份<input v-model="filters.month" type="month" /></label>
      <label>费用类型<select v-model="filters.expenseType"><option v-if="focus !== 'ENERGY'" value="">全部类型</option><option v-for="item in categories.filter((entry) => focus !== 'ENERGY' || ['ELECTRIC','GAS','TEMP_ELECTRIC','TEMP_GAS'].includes(entry.id))" :key="item.id" :value="item.id">{{ item.label }}</option></select></label>
      <label v-if="focus !== 'UNASSIGNED'">申请属性<select v-model="filters.applicationScope"><option value="">全部</option><option value="DAILY">日常费用</option><option value="TASK">任务费用</option></select></label>
      <label v-if="focus !== 'UNASSIGNED'">当前归属<select v-model="filters.bound"><option value="">全部</option><option value="true">已归属任务</option><option value="false">未归属任务</option></select></label>
      <button type="button" :aria-expanded="moreFilters" @click="moreFilters = !moreFilters">{{ moreFilters ? '收起更多筛选' : '更多筛选' }}</button>
      <button class="primary" type="button" @click="load(1)">查询</button>
    </FilterBar>
    <FilterBar v-if="moreFilters" class="ledger-more-filter">
      <label>司机<EntityPicker v-model="filters.driverId" remote-search :load-page="optionPage('DRIVER')" :label-of="(item) => `${item.label} · ${mask(item.detail)}`" placeholder="全部司机" /></label>
      <label>公司车辆<EntityPicker v-model="filters.vehicleId" remote-search :load-page="optionPage('VEHICLE')" :label-of="(item) => item.label" placeholder="全部车辆" /></label>
      <label v-if="focus !== 'UNASSIGNED'">运输任务<EntityPicker v-model="filters.taskId" remote-search :load-page="optionPage('TASK')" :label-of="(item) => `${item.label} · ${item.detail || ''}`" placeholder="全部任务" /></label>
      <label>来源<select v-model="filters.sourceType"><option value="">全部</option><option value="APPROVAL">审批通过</option><option value="REVERSAL">冲销记录</option><option value="CHARGING">充电公司</option></select></label>
      <label v-if="focus !== 'UNASSIGNED'">状态<select v-model="filters.status"><option value="">全部</option><option value="ACTIVE">有效</option><option value="REVERSED">已冲销</option><option value="REVERSAL">冲销记录</option></select></label>
    </FilterBar>
    <SummaryCards v-if="summary && focus === 'UNASSIGNED'" label="待归属费用汇总" :items="[
      { label: '待归属费用', value: money(summary.amount_total), description: '当前筛选下可办理的有效未归属费用' },
      { label: '待归属笔数', value: summary.entry_count, description: '数量来自正式服务端统计' }
    ]" />
    <SummaryCards v-else-if="summary" label="当前筛选费用汇总" :items="[
      { label: '费用净额', value: money(summary.amount_total), description: '含正式冲销事实' },
      { label: '已归属任务', value: money(attributedSummary?.amount_total), description: '同一筛选条件下的任务费用净额' },
      { label: '未归属任务', value: money(summary.unassigned_total), description: '仍计入公司总费用' },
      { label: '冲销记录净额', value: money(reversalSummary?.amount_total), description: '负数冲销事实' }
    ]" />
    <p v-if="summary" class="ledger-count">当前筛选共 {{ summary.entry_count }} 条费用记录；数量和金额均采用服务端筛选口径。<template v-if="focus === 'ENERGY'">当前显示所选能源费用类型，可切换电费或气费类别查看。</template></p>
    <DataTable :columns="cols" :rows="rows" :loading="loading" :empty-text="focus === 'DRIVER' && !filters.driverId ? '请先选择司机，再查看对应正式费用' : '当前条件下没有正式费用记录'">
      <template #expense_no="{ row }"><span class="ui-business-id">{{ row.expense_no }}</span></template>
      <template #expense_type="{ row }">{{ label(row.expense_type) }}</template><template #application_scope="{ row }">{{ label(row.application_scope) }}</template>
      <template #amount="{ row }">{{ money(row.amount) }}</template><template #task_no="{ row }">{{ row.task_no || '尚未归属任务' }}</template>
      <template #source_type="{ row }">{{ label(row.source_type) }}</template><template #actions="{ row }"><button type="button" @click="openDetail(row.id)">详情与归属</button></template>
    </DataTable>
    <PaginationBar :page-no="pageNo" :page-size="20" :total="total" @change="load" />
    <DetailDrawer :open="!!detail" title="费用详情" @close="detail = null">
      <template #subtitle>{{ detail?.entry?.expense_no }}</template>
      <template v-if="detail">
        <section class="ui-section"><h2>正式费用事实</h2><dl class="ui-detail-list">
          <div><dt>业务日期</dt><dd>{{ detail.entry.business_date }}</dd></div><div><dt>费用类型</dt><dd>{{ label(detail.entry.expense_type) }}</dd></div>
          <div><dt>金额</dt><dd>{{ money(detail.entry.amount) }}</dd></div><div><dt>状态</dt><dd>{{ detail.entry.display_status }}</dd></div>
          <div><dt>申请属性</dt><dd>{{ label(detail.entry.application_scope) }}</dd></div><div><dt>当前经营归属</dt><dd>{{ detail.entry.task_no || '未归属任务，计入公司总费用' }}</dd></div>
          <div><dt>归属方式</dt><dd>{{ detail.entry.attribution_method }}</dd></div><div><dt>公司车牌</dt><dd>{{ detail.entry.vehicle_plate }}</dd></div>
          <div><dt>司机</dt><dd>{{ detail.entry.driver_name || '—' }}</dd></div><div><dt>费用来源</dt><dd>{{ label(detail.entry.source_type) }}</dd></div>
          <div><dt>明细</dt><dd>{{ detail.entry.description || '—' }}</dd></div><div><dt>票据号 / 发票号</dt><dd>{{ detail.entry.receipt_no || '—' }} / {{ detail.entry.invoice_no || '—' }}</dd></div>
          <div><dt>站点 / 备注</dt><dd>{{ detail.entry.station_name || '—' }} / {{ detail.entry.remark || '—' }}</dd></div>
          <div><dt>审批来源</dt><dd>{{ detail.entry.approval_no ? `${detail.entry.approval_name || '费用审批'} · ${detail.entry.approval_no}` : '非审批来源' }}</dd></div>
          <div><dt>申请时间 / 批次明细</dt><dd>{{ detail.entry.approval_started_at || '—' }}{{ detail.entry.approval_item_no ? ` · 第 ${detail.entry.approval_item_no} 条明细` : '' }}</dd></div>
        </dl></section>
        <section v-if="chargingSource" class="ui-section"><h2>充电原始订单</h2><dl class="ui-detail-list">
          <div><dt>充电公司</dt><dd>{{ chargingSource.company_name }}</dd></div>
          <div><dt>原始订单号</dt><dd>{{ chargingSource.original_order_no }}</dd></div>
          <div><dt>原始位置</dt><dd>{{ chargingSource.sheet_name }} · 第 {{ chargingSource.row_no }} 行</dd></div>
          <div><dt>充电时间</dt><dd>{{ chargingSource.charged_at }}</dd></div>
          <div><dt>原始文件</dt><dd>{{ chargingSource.original_filename }} <button v-if="can('PAGE_CHARGING_IMPORTS')" type="button" @click="openChargingFile">打开原始 Excel</button></dd></div>
        </dl></section>
        <section class="ui-section"><h2>归属历史</h2><p v-if="!detail.attributionHistory.length" class="muted">尚无归属调整</p><ol v-else><li v-for="(item, index) in detail.attributionHistory" :key="index">{{ item.previous_task_no || '未归属' }} → {{ item.new_task_no || '未归属' }} · {{ item.reason }} · {{ mask(item.operator_phone) }} · {{ item.operated_at }}</li></ol></section>
        <section class="ui-section"><h2>冲销记录</h2><p v-if="!detail.reversal.length" class="muted">没有冲销</p><p v-for="(item, index) in detail.reversal" :key="index">{{ item.original_expense_no }} → {{ item.reversal_expense_no }} · {{ item.reason }} · {{ mask(item.operator_phone) }} · {{ item.created_at }}</p></section>
      </template>
      <template v-if="detail && detail.entry.display_status === '有效' && detail.entry.source_type !== 'REVERSAL'" #footer>
        <button v-if="can('BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE')" type="button" @click="begin('attribute')">调整任务归属</button>
        <button v-if="can('BUTTON_EXPENSE_LEDGER_REVERSE')" type="button" @click="begin('reverse')">冲销费用</button>
      </template>
    </DetailDrawer>
    <FormDialog :open="!!dialog" :title="dialog === 'attribute' ? '调整费用归属' : '确认冲销费用'" :busy="saving" @close="dialog = ''">
      <div class="ui-form-grid">
        <template v-if="dialog === 'attribute'"><div class="ui-full attribution-context"><strong>{{ detail?.entry?.expense_no }}</strong><span>{{ detail?.entry?.business_date }} · {{ label(detail?.entry?.expense_type) }} · {{ money(detail?.entry?.amount) }}</span><span>费用车辆：{{ detail?.entry?.vehicle_plate }} · 当前归属：{{ detail?.entry?.task_no || '未归属任务' }}</span></div>
          <label class="ui-full ui-form-field">目标运输任务<EntityPicker v-model="targetTaskId" remote-search :load-page="matchingTaskPage" :label-of="(item) => `${item.label} · ${item.detail || ''}`" placeholder="选择匹配车辆的任务" /></label>
          <p class="ui-full muted">仅展示按当前费用车辆查询的候选任务；确认时服务端会再次核对正式参与车辆，不匹配将拒绝。归属调整不会重复增加公司总费用。</p></template>
        <p v-else class="ui-full">将新增一条等额负数冲销事实；原费用与审批记录永久保留。此操作不能重复。</p>
        <label class="ui-full ui-form-field">{{ dialog === 'attribute' ? '归属调整原因' : '冲销原因' }}<textarea v-model="reason" rows="3" maxlength="500" /></label>
      </div>
      <div class="ui-dialog-actions"><button type="button" :disabled="saving" @click="dialog = ''">取消</button><button type="button" :disabled="saving || !reason.trim()" @click="save">{{ saving ? '正在提交…' : '确认' }}</button></div>
    </FormDialog>
  </main>
</template>

<style scoped>
.ledger-more-filter { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); align-items:start; }
.ledger-more-filter label, .ledger-more-filter :deep(.ui-entity-picker) { min-width:0; width:100%; }
.ledger-more-filter :deep(.ui-entity-picker) { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:4px; }
.ledger-more-filter :deep(.ui-entity-picker > small), .ledger-more-filter :deep(.ui-entity-picker > .ui-pagination) { grid-column:1 / -1; }
@media (max-width:950px) { .ledger-more-filter { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media (max-width:640px) { .ledger-more-filter { grid-template-columns:1fr; } }
.ledger-count { margin: -3px 0 12px; color: var(--ui-text-secondary); font-size: 13px; }
.attribution-context { display: grid; gap: 6px; padding: 14px; border: 1px solid var(--ui-border); border-radius: 8px; background: #f7faf8; }
.attribution-context strong { color: #135d4d; }
</style>
