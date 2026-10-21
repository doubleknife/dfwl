<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { routeApi, salaryApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { formatDateTime } from '../utils/dateTime.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import PaginationBar from '../components/PaginationBar.vue';
import EntityPicker from '../components/EntityPicker.vue';
import ErrorState from '../components/ErrorState.vue';
import SummaryCards from '../components/SummaryCards.vue';

const today = new Date();
const filters = reactive({ month: `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`, driverId: '', driverType: '', templateId: '', taskId: '', transferOnly: '' });
const mode = ref('ledger');
const records = ref([]);
const summary = ref(null);
const pageNo = ref(1); const pageSize = ref(20);
const total = ref(0);
const loading = ref(false);
const exporting = ref(false);
const error = ref('');
const selected = ref(null);
const participants = ref([]);
const historyByParticipant = ref({});
const review = ref(false);
const reviewReason = ref('');
const reviewRows = ref([]);
const saving = ref(false);
const reviewKey = ref('');
const reviewPayload = ref('');
const effectiveFilters = computed(() => ({ ...filters, transferOnly: filters.transferOnly === '' ? null : filters.transferOnly }));
const loadDriverOptions = (page, search) => salaryApi.options('DRIVER', search, filters.month, page);
const loadTemplateOptions = (page, search) => salaryApi.options('TEMPLATE', search, filters.month, page);
const loadTaskOptions = (page, search) => salaryApi.options('TASK', search, filters.month, page);
watch(() => filters.month, () => { filters.driverId = ''; filters.templateId = ''; filters.taskId = ''; });
const ledgerColumns = [
  { key: 'businessDate', label: '业务日期' }, { key: 'taskNo', label: '任务号' },
  { key: 'templateName', label: '线路模板' }, { key: 'driverName', label: '司机' },
  { key: 'salaryAmount', label: '核定工资', numeric: true }, { key: 'driverType', label: '类型' },
  { key: 'sequenceNo', label: '参与身份' }, { key: 'vehiclePlate', label: '当时车牌' },
  { key: 'transferTask', label: '任务情况' }, { key: 'actions', label: '操作' }
];
const driverColumns = [
  { key: 'driverName', label: '司机' }, { key: 'driverType', label: '类型' },
  { key: 'taskCount', label: '参与任务数', numeric: true },
  { key: 'participantCount', label: '参与次数', numeric: true },
  { key: 'transferTaskCount', label: '转派任务数', numeric: true },
  { key: 'salaryTotal', label: '工资合计', numeric: true }, { key: 'actions', label: '操作' }
];
const typeName = (type) => type === 'OUTSOURCED' ? '外协司机' : '自有司机';
const money = (value) => `¥ ${Number(value || 0).toFixed(2)}`;
const identity = (index) => index === 1 ? '初始司机' : `第${index - 1}次接手`;
const maskedPhone = (phone) => phone?.replace(/^(\d{3})\d+(\d{4})$/, '$1****$2') || '系统账号';
const summaryItems = computed(() => summary.value ? [
  { label: '工资总额', value: money(summary.value.salaryTotal) },
  { label: '自有司机工资', value: money(summary.value.internalSalary) },
  { label: '外协司机工资', value: money(summary.value.outsourcedSalary) },
  { label: '转派任务 / 参与次数', value: `${summary.value.transferTaskCount} / ${summary.value.participantCount}` }
] : []);

async function load(next = 1) {
  loading.value = true; error.value = '';
  try {
    const [page, totals] = await Promise.all([
      mode.value === 'drivers' ? salaryApi.drivers(effectiveFilters.value, next, pageSize.value) : salaryApi.list(effectiveFilters.value, next, pageSize.value),
      salaryApi.summary(effectiveFilters.value)
    ]);
    records.value = page.records || [];
    total.value = Number(page.total || 0);
    summary.value = totals;
    pageNo.value = next;
  } catch (cause) { error.value = cause.message || '工资台账加载失败'; }
  finally { loading.value = false; }
}
function changeMode(next) { mode.value = next; load(1); }
function selectDriver(row) { filters.driverId = row.driverId; mode.value = 'ledger'; load(1); }
async function openRow(row) {
  error.value = '';
  try {
    const chain = await routeApi.participants(row.taskId);
    const allParticipants = chain || [];
    const changes = await Promise.all(allParticipants.map(async (item) => [item.id, (await salaryApi.history(item.id)) || []]));
    selected.value = row; participants.value = allParticipants;
    historyByParticipant.value = Object.fromEntries(changes);
    reviewRows.value = participants.value.map((item) => ({ participantId: item.id, amount: String(item.salary), expectedVersion: item.version }));
    reviewReason.value = ''; review.value = false;
    reviewKey.value = ''; reviewPayload.value = '';
  } catch (cause) { error.value = cause.message || '工资详情加载失败'; }
}
function beginReview() { review.value = true; reviewKey.value = ''; reviewPayload.value = ''; }
async function confirmReview() {
  if (saving.value || !reviewReason.value.trim() || reviewRows.value.some((item) =>
    !/^\d+(?:\.\d{1,2})?$/.test(item.amount) || !Number.isFinite(Number(item.amount)))) return;
  saving.value = true; error.value = '';
  try {
    const payload = {
      salaries: reviewRows.value.map((item) => ({ ...item, amount: Number(item.amount) })),
      reason: reviewReason.value.trim()
    };
    const fingerprint = JSON.stringify(payload);
    if (fingerprint !== reviewPayload.value) { reviewKey.value = crypto.randomUUID(); reviewPayload.value = fingerprint; }
    await routeApi.confirmSalaries(selected.value.taskId, { ...payload, requestKey: reviewKey.value });
    review.value = false;
    const taskId = selected.value.taskId;
    selected.value = null;
    await load(pageNo.value);
    const current = records.value.find((item) => item.taskId === taskId);
    if (current) await openRow(current);
  } catch (cause) { error.value = cause.message || '核薪失败，请核对工资与版本后重试'; }
  finally { saving.value = false; }
}
async function download() {
  exporting.value = true; error.value = '';
  try {
    const blob = await salaryApi.export(effectiveFilters.value);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a'); link.href = url; link.download = `工资台账-${filters.month || '全部'}.xlsx`;
    link.click(); URL.revokeObjectURL(url);
  } catch (cause) { error.value = cause.message || '工资台账导出失败'; }
  finally { exporting.value = false; }
}
onMounted(() => load());
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="工资台账" description="仅已完成卸货的任务计入工资台账；转派链的每位司机均单独计薪。">
      <template #actions><button type="button" @click="load(pageNo)">刷新</button><button v-if="can('BUTTON_SALARIES_EXPORT')" type="button" :disabled="exporting" @click="download">{{ exporting ? '正在导出…' : '下载当前筛选 Excel' }}</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load(pageNo)" />
    <FilterBar class="salary-filter">
      <label>月份<input v-model="filters.month" type="month" /></label>
      <label>司机<EntityPicker :key="`driver-${filters.month}`" v-model="filters.driverId" remote-search :load-page="loadDriverOptions" :label-of="(item) => `${item.label} · ${item.detail || ''}`" placeholder="全部司机" /></label>
      <label>司机类型<select v-model="filters.driverType"><option value="">全部</option><option value="INTERNAL">自有司机</option><option value="OUTSOURCED">外协司机</option></select></label>
      <label>线路模板<EntityPicker :key="`template-${filters.month}`" v-model="filters.templateId" remote-search :load-page="loadTemplateOptions" :label-of="(item) => `${item.label} · ${item.detail || ''}`" placeholder="全部线路模板" /></label>
      <label>运输任务<EntityPicker :key="`task-${filters.month}`" v-model="filters.taskId" remote-search :load-page="loadTaskOptions" :label-of="(item) => item.label" placeholder="全部任务" /></label>
      <label>任务情况<select v-model="filters.transferOnly"><option value="">全部</option><option value="false">普通任务</option><option value="true">转派任务</option></select></label>
      <button type="button" @click="load(1)">查询</button>
    </FilterBar>
    <SummaryCards v-if="summary" label="当前筛选工资汇总" :items="summaryItems" />
    <div class="ui-tabs" role="tablist" aria-label="工资视图"><button type="button" :aria-selected="mode === 'ledger'" @click="changeMode('ledger')">逐任务工资</button><button type="button" :aria-selected="mode === 'drivers'" @click="changeMode('drivers')">司机月度汇总</button></div>
    <DataTable v-if="mode === 'ledger'" class="ui-paged-table" :columns="ledgerColumns" :rows="records" row-key="participantId" :loading="loading" empty-text="当前条件下暂无参与人工资">
      <template #driverType="{ row }">{{ typeName(row.driverType) }}</template>
      <template #sequenceNo="{ row }">{{ identity(row.sequenceNo) }}</template>
      <template #salaryAmount="{ row }">{{ money(row.salaryAmount) }}</template>
      <template #transferTask="{ row }"><span :class="row.transferTask ? 'v3-transfer-tag' : ''">{{ row.transferTask ? '转派任务' : '普通任务' }}</span></template>
      <template #actions="{ row }"><button type="button" class="ui-detail-button" @click="openRow(row)">工资详情</button></template>
    </DataTable>
    <DataTable v-else class="ui-paged-table" :columns="driverColumns" :rows="records" row-key="driverId" :loading="loading" empty-text="当前月份暂无司机工资">
      <template #driverType="{ row }">{{ typeName(row.driverType) }}</template><template #salaryTotal="{ row }">{{ money(row.salaryTotal) }}</template>
      <template #actions="{ row }"><button type="button" @click="selectDriver(row)">逐任务明细</button></template>
    </DataTable>
    <PaginationBar :page-no="pageNo" :page-size="pageSize" :total="total" @change="load" @change-size="(size) => { pageSize = size; load(1); }" />
    <DetailDrawer :open="!!selected" title="参与人工资详情" @close="selected = null">
      <template #subtitle>{{ selected?.taskNo }} · {{ selected?.templateName }}</template>
      <p>按参与顺序核对全部司机；工资来自同一参与人事实。</p>
      <div v-for="(item, index) in participants" :key="item.id" class="v3-person-row">
        <strong>{{ identity(item.sequenceNo) }} · {{ item.driverName }} · {{ typeName(item.driverType) }}</strong>
        <span>{{ item.outsourcedPlate || item.companyVehiclePlate || '暂无车牌' }}<template v-if="item.trailerPlate"> · 车挂 {{ item.trailerPlate }}</template></span>
        <small>发车 {{ formatDateTime(item.departedAt, '未发车') }} · 结束 {{ formatDateTime(item.endedAt, '尚未结束') }}</small>
        <label v-if="review">核定工资（元）<input v-model="reviewRows[index].amount" type="number" min="0" step="0.01" /></label>
        <strong v-else>{{ money(item.salary) }}</strong>
        <details v-if="historyByParticipant[item.id]?.length" class="v3-salary-history"><summary>查看该司机工资调整历史（{{ historyByParticipant[item.id].length }} 条）</summary><ol class="ui-timeline"><li v-for="change in historyByParticipant[item.id]" :key="change.id">{{ money(change.beforeAmount) }} → {{ money(change.afterAmount) }}<p>{{ formatDateTime(change.operatedAt) }} · 操作账号 {{ maskedPhone(change.operatorPhone) }} · {{ change.reason }}</p></li></ol></details>
      </div>
      <template v-if="review"><label>调整原因<textarea v-model.trim="reviewReason" rows="3" placeholder="填写本次核薪原因" /></label></template>
      <p v-if="!Object.values(historyByParticipant).some((changes) => changes.length)" class="muted">本任务暂无工资调整历史</p>
      <template #footer><button v-if="review" type="button" @click="review = false">取消</button><button v-if="review" type="button" class="primary" :disabled="saving || !reviewReason.trim()" @click="confirmReview">{{ saving ? '正在核定…' : '确认全部工资' }}</button><button v-else-if="can('BUTTON_SALARIES_ADJUST') && participants.length" type="button" class="primary" @click="beginReview">核定全部参与人工资</button></template>
    </DetailDrawer>
  </main>
</template>

<style scoped>
.salary-filter { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: end; }
.salary-filter label, .salary-filter :deep(.ui-entity-picker) { min-width: 0; width: 100%; }
.salary-filter > button { justify-self: start; min-width: 88px; }
@media (max-width: 1100px) { .salary-filter { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 720px) { .salary-filter { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 520px) { .salary-filter { grid-template-columns: 1fr; } }
.v3-person-row{background:#fff;border:1px solid #e4ece8;border-radius:10px;padding:14px;margin:10px 0}.v3-person-row span,.v3-person-row small{display:block;color:#58716b}.v3-transfer-tag{color:#0c685e;background:#e8f4f0;border-radius:6px;padding:3px 7px}.v3-person-row strong,.v3-person-row span,.v3-person-row small{margin-bottom:6px}.v3-salary-history{margin-top:10px;padding-top:10px;border-top:1px solid #e4ece8}.v3-salary-history summary{cursor:pointer;color:#0c685e}
</style>
