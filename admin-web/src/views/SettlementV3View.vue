<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { v3ApprovalApi, v3SettlementApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import SummaryCards from '../components/SummaryCards.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ApprovalHistoryDetail from '../components/ApprovalHistoryDetail.vue';
import { formatDateTime } from '../utils/dateTime.js';

const today = new Date();
const filters = reactive({ month: `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}` });
const versions = ref([]); const chosen = ref(null); const mode = ref('details'); const rows = ref([]);
const pageNo = ref(1); const pageSize = ref(20); const total = ref(0); const loading = ref(false); const busy = ref(false); const error = ref('');
const generateConfirmOpen = ref(false);
const approvalDetail = ref(null);
const approvalOpen = ref(false);
const approvalLoading = ref(false);
const approvalError = ref('');
const money = (v) => `¥ ${Number(v || 0).toFixed(2)}`;
const versionCards = computed(() => chosen.value ? [
  { label: '收入', value: money(chosen.value.income_total) },
  { label: '信息费', value: money(chosen.value.information_fee_total) },
  { label: '参与工资', value: money(chosen.value.salary_total) },
  { label: '总支出', value: money(chosen.value.expense_total) },
  { label: '利润', value: money(chosen.value.profit_total) }
] : []);
const detailCols = [
  { key: 'factType', label: '事实类型' }, { key: 'businessNo', label: '业务编号' },
  { key: 'businessDate', label: '业务日期' }, { key: 'income', label: '收入', numeric: true },
  { key: 'expense', label: '支出', numeric: true }, { key: 'profit', label: '利润', numeric: true },
  { key: 'summary', label: '结算事实' }
];
const diffCols = [
  { key: 'factType', label: '事实类型' }, { key: 'businessNo', label: '业务编号' },
  { key: 'changeType', label: '变化' }, { key: 'reason', label: '原因' },
  { key: 'before', label: '前版' }, { key: 'after', label: '本版' },
  { key: 'operator', label: '操作人' }, { key: 'operatedAt', label: '操作时间' },
  { key: 'approval', label: '审批来源' },
  { key: 'incomeDelta', label: '收入影响', numeric: true },
  { key: 'expenseDelta', label: '支出影响', numeric: true },
  { key: 'profitDelta', label: '利润影响', numeric: true }
];
const summarize = (snapshot) => {
  if (!snapshot || !Object.keys(snapshot).length) return '—';
  const items = [];
  if (snapshot.net_weight != null) items.push(`净重 ${snapshot.net_weight} 吨`);
  if (snapshot.actual_unit_price != null) items.push(`单价 ${money(snapshot.actual_unit_price)}`);
  if (snapshot.actual_information_fee != null) items.push(`信息费 ${money(snapshot.actual_information_fee)}`);
  if (snapshot.salary_amount != null) items.push(`工资 ${money(snapshot.salary_amount)}`);
  if (snapshot.task_expense_amount != null) items.push(`归属费用 ${money(snapshot.task_expense_amount)}`);
  if (snapshot.amount != null) items.push(`费用 ${money(snapshot.amount)}`);
  return items.join(' · ') || '事实已记录';
};
const mask = (phone) => String(phone || '').replace(/^(\d{3})\d+(\d{4})$/, '$1****$2') || '—';
const approvalSummary = (approval) => approval
  ? `${formatDateTime(approval.date)} · 发起人 ${mask(approval.applicant)} · ${approval.type === 'EXPENSE' ? '费用审批' : approval.type}`
  : '—';
async function loadVersions() {
  loading.value = true; error.value = '';
  try { versions.value = await v3SettlementApi.versions(filters.month); chosen.value = null; rows.value = []; }
  catch (cause) { error.value = cause.message || '月结版本加载失败'; }
  finally { loading.value = false; }
}
async function selectVersion(version) {
  loading.value = true; error.value = '';
  try { chosen.value = await v3SettlementApi.version(version.id); mode.value = 'details'; await loadRows(1); }
  catch (cause) { chosen.value = null; error.value = cause.message || '月结版本详情加载失败'; }
  finally { loading.value = false; }
}
async function loadRows(page = 1) {
  if (!chosen.value) return;
  loading.value = true; error.value = '';
  try {
    const result = mode.value === 'details' ? await v3SettlementApi.details(chosen.value.id, page, pageSize.value) : await v3SettlementApi.diff(chosen.value.id, page, pageSize.value);
    rows.value = result.records || []; total.value = Number(result.total || 0); pageNo.value = page;
  } catch (cause) { error.value = cause.message || '月结明细加载失败'; }
  finally { loading.value = false; }
}
function changeMode(next) { mode.value = next; loadRows(1); }
async function openApproval(row) {
  if (!row.approvalId) return;
  approvalOpen.value = true;
  approvalDetail.value = null;
  approvalError.value = '';
  approvalLoading.value = true;
  try { approvalDetail.value = await v3ApprovalApi.detail(row.approvalId); }
  catch (cause) { approvalError.value = cause.message || '审批详情加载失败'; }
  finally { approvalLoading.value = false; }
}
async function generate() {
  if (busy.value || !generateConfirmOpen.value) return;
  generateConfirmOpen.value = false;
  busy.value = true; error.value = '';
  try { const version = await v3SettlementApi.generate(filters.month); versions.value = await v3SettlementApi.versions(filters.month); await selectVersion(version); }
  catch (cause) { error.value = cause.message || '月结生成失败'; }
  finally { busy.value = false; }
}
onMounted(loadVersions);
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="月度结算" description="每次生成只读历史版本；后续事实变化只影响新版本与差异，不改写旧版本。">
      <template #actions><button v-if="can('BUTTON_SETTLEMENTS_GENERATE')" type="button" :disabled="busy" @click="generateConfirmOpen = true">{{ busy ? '正在生成…' : '生成新版本' }}</button><button v-if="chosen" type="button" class="ui-return-button" @click="chosen = null">← 返回版本列表</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="chosen ? loadRows(pageNo) : loadVersions()" />
    <FilterBar><label>结算月份<input v-model="filters.month" type="month" @change="loadVersions" /></label></FilterBar>
    <template v-if="!chosen">
      <DataTable :columns="[
        { key: 'version_no', label: '版本' }, { key: 'generated_at', label: '生成时间' },
        { key: 'income_total', label: '收入', numeric: true }, { key: 'expense_total', label: '总支出', numeric: true },
        { key: 'profit_total', label: '利润', numeric: true }, { key: 'actions', label: '操作' }
      ]" :rows="versions" :loading="loading" empty-text="本月尚无月结版本">
        <template #version_no="{ row }">第 {{ row.version_no }} 版 · 只读</template><template #generated_at="{ row }">{{ formatDateTime(row.generated_at) }}</template><template #income_total="{ row }">{{ money(row.income_total) }}</template>
        <template #expense_total="{ row }">{{ money(row.expense_total) }}</template><template #profit_total="{ row }">{{ money(row.profit_total) }}</template>
        <template #actions="{ row }"><button type="button" class="ui-detail-button" @click="selectVersion(row)">查看详情</button></template>
      </DataTable>
    </template>
    <template v-else>
      <div class="ui-list-heading"><div><h2>{{ chosen.month_key || filters.month }} · 第 {{ chosen.version_no }} 版</h2><p class="muted">只读历史快照；后续事实变化只进入新版本。</p></div></div>
      <SummaryCards label="历史月结版本汇总" :items="versionCards" />
      <div class="ui-tabs" role="tablist"><button type="button" :aria-selected="mode === 'details'" @click="changeMode('details')">版本明细</button><button type="button" :aria-selected="mode === 'diff'" @click="changeMode('diff')">与前版差异</button></div>
      <DataTable class="ui-paged-table" :columns="mode === 'details' ? detailCols : diffCols" :rows="rows" row-key="businessNo" :loading="loading" :empty-text="mode === 'diff' ? '与前一版本没有事实差异' : '本版本暂无结算事实'">
        <template #summary="{ row }">{{ summarize(row.snapshot) }}</template>
        <template #before="{ row }">{{ summarize(row.before) }}</template><template #after="{ row }">{{ summarize(row.after) }}</template>
        <template #operator="{ row }">{{ mask(row.operator) }}</template><template #operatedAt="{ row }">{{ formatDateTime(row.operatedAt) }}</template>
        <template #approval="{ row }"><div class="settlement-approval-source"><span>{{ approvalSummary(row.approval) }}</span><button v-if="row.approvalId" type="button" class="ui-detail-button" @click="openApproval(row)">查看审批详情</button></div></template>
        <template #income="{ row }">{{ money(row.income) }}</template><template #expense="{ row }">{{ money(row.expense) }}</template><template #profit="{ row }">{{ money(row.profit) }}</template>
        <template #incomeDelta="{ row }">{{ money(row.incomeDelta) }}</template><template #expenseDelta="{ row }">{{ money(row.expenseDelta) }}</template><template #profitDelta="{ row }">{{ money(row.profitDelta) }}</template>
      </DataTable>
      <PaginationBar :page-no="pageNo" :page-size="pageSize" :total="total" @change="loadRows" @change-size="(size) => { pageSize = size; loadRows(1); }" />
    </template>
    <ConfirmDialog :open="generateConfirmOpen" title="生成月结版本" :message="`确定生成 ${filters.month} 的下一版只读月结？旧版本将永久保留。`" :busy="busy" @cancel="generateConfirmOpen = false" @confirm="generate" />
    <DetailDrawer :open="approvalOpen" title="审批详情" class="settlement-approval-drawer" @close="approvalOpen = false">
      <p v-if="approvalLoading">正在加载审批详情…</p>
      <ErrorState v-else-if="approvalError" :message="approvalError" />
      <template v-else-if="approvalDetail">
        <ApprovalHistoryDetail :detail="approvalDetail" section="summary" />
        <ApprovalHistoryDetail :detail="approvalDetail" section="timeline" />
      </template>
    </DetailDrawer>
  </main>
</template>

<style scoped>
.settlement-approval-drawer :deep(.ui-drawer) { width: min(calc(100vw - 24px), 960px); }
.settlement-approval-source { display:flex; flex-direction:column; align-items:flex-start; gap:2px; min-width:180px; max-width:260px; white-space:normal; overflow-wrap:anywhere; }
</style>
