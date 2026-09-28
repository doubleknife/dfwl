<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { expenseApi, masterApi, routeApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { attributionLabels, expenseTypeLabels, sourceTypeLabels, statusLabels } from '../utils/displayLabels.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import StatusTag from '../components/StatusTag.vue';
import PaginationBar from '../components/PaginationBar.vue';
import EntityPicker from '../components/EntityPicker.vue';
import FormDialog from '../components/FormDialog.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ErrorState from '../components/ErrorState.vue';

const rows = ref([]);
const pageNo = ref(1);
const total = ref(0);
const pageSize = 20;
const vehicles = ref([]);
const routes = ref([]);
const eligibleVehicles = ref([]);
const routeVehicleError = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const selected = ref(null);
const moreTarget = ref(null);
const history = ref([]);
const approval = ref(null);
const reversalRecords = ref([]);
const detailLoading = ref(false);
const createOpen = ref(false);
const attributionTarget = ref(null);
const reversalTarget = ref(null);
const confirmReversal = ref(false);
const deleteTarget = ref(null);
const filters = reactive({ date: '', type: '', attribution: '', status: '', vehicle: '', routeId: '', keyword: '' });
const advancedFiltersOpen = ref(false);
const advancedFilterCount = computed(() => ['attribution', 'status', 'vehicle', 'routeId'].filter((key) => filters[key]).length);
const hasFilters = computed(() => Object.values(filters).some(Boolean));
function clearFilters() {
  Object.keys(filters).forEach((key) => { filters[key] = ''; });
  load(1);
}
const form = reactive({ expenseType: 'REPAIR', businessDate: new Date().toISOString().slice(0, 10), vehicleId: '', attributionType: 'DAILY', routeId: '', amount: '', remark: '', orderNo: '', startTime: '', quantity: '' });
const attributionForm = reactive({ attributionType: 'DAILY', routeId: '', reason: '' });
const reversalReason = ref('');
const amount = (value) => value == null ? '—' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const vehicleName = (id) => id ? vehicles.value.find((item) => item.id === id)?.plateNo || '车牌未加载' : '—';
const routeName = (id) => id ? routes.value.find((item) => item.id === id)?.routeNo || '线路号未加载' : '—';
const energy = computed(() => ['ELECTRIC', 'GAS', 'TEMP_ELECTRIC'].includes(form.expenseType));
async function load(page = 1) {
  loading.value = true; error.value = '';
  try {
    const [result, vehiclePage, routePage] = await Promise.all([
      expenseApi.list(page, pageSize, {
        routeId: filters.routeId, businessDate: filters.date, expenseType: filters.type,
        attributionType: filters.attribution, status: filters.status,
        vehicleId: filters.vehicle, keyword: filters.keyword
      }),
      can('vehicle:list') ? masterApi.vehicles(1) : Promise.resolve({ records: [] }),
      can('route:list') ? routeApi.list(1) : Promise.resolve({ records: [] })
    ]);
    rows.value = result.records || [];
    total.value = Number(result.total || 0);
    pageNo.value = page;
    vehicles.value = vehiclePage.records || [];
    routes.value = routePage.records || [];
  } catch (cause) { rows.value = []; total.value = 0; error.value = cause.message || '费用加载失败'; }
  finally { loading.value = false; }
}
async function create() {
  busy.value = true; error.value = '';
  try {
    await expenseApi.create({
      expenseType: form.expenseType, businessDate: form.businessDate, vehicleId: Number(form.vehicleId),
      attributionType: form.attributionType, routeId: form.attributionType === 'ROUTE' && form.routeId ? Number(form.routeId) : null,
      amount: form.amount, sourceType: 'MANUAL', remark: form.remark || null,
      energyDetail: energy.value ? { energyType: form.expenseType, orderNo: form.orderNo, startTime: form.startTime, quantity: form.quantity } : null
    });
    createOpen.value = false;
    Object.assign(form, { vehicleId: '', routeId: '', amount: '', remark: '', orderNo: '', startTime: '', quantity: '' });
    await load(1);
  } catch (cause) { error.value = cause.message || '保存费用失败'; }
  finally { busy.value = false; }
}
async function showDetail(row) {
  selected.value = row; history.value = []; approval.value = null; reversalRecords.value = []; detailLoading.value = true; error.value = '';
  try {
    const [historyResult, approvalResult, reversalResult] = await Promise.all([
      can('expense:attribution:edit') ? expenseApi.attributionHistory(row.id) : Promise.resolve([]),
      row.sourceType === 'APPROVAL' && can('expense:approval:view') ? expenseApi.approval(row.id) : Promise.resolve(null),
      row.status === 'REVERSED' ? expenseApi.reversalRecord(row.id) : Promise.resolve(null)
    ]);
    history.value = historyResult || [];
    approval.value = approvalResult;
    reversalRecords.value = reversalResult ? [reversalResult] : [];
  } catch (cause) { error.value = cause.message || '费用详情加载失败'; }
  finally { detailLoading.value = false; }
}
function openAttribution(row) { moreTarget.value = null; attributionTarget.value = row; Object.assign(attributionForm, { attributionType: row.attributionType, routeId: row.routeId || '', reason: '' }); }
function openReversal(row) { moreTarget.value = null; reversalTarget.value = row; reversalReason.value = ''; }
function openDelete(row) { moreTarget.value = null; deleteTarget.value = row; }
async function saveAttribution() {
  busy.value = true; error.value = '';
  try {
    await expenseApi.attribution(attributionTarget.value.id, { attributionType: attributionForm.attributionType, routeId: attributionForm.attributionType === 'ROUTE' && attributionForm.routeId ? Number(attributionForm.routeId) : null, reason: attributionForm.reason });
    attributionTarget.value = null; selected.value = null; await load(pageNo.value);
  } catch (cause) { error.value = cause.message || '归属调整失败'; }
  finally { busy.value = false; }
}
async function reverse() {
  busy.value = true; error.value = '';
  try { await expenseApi.reverse(reversalTarget.value.id, { reason: reversalReason.value }); confirmReversal.value = false; reversalTarget.value = null; reversalReason.value = ''; selected.value = null; await load(pageNo.value); }
  catch (cause) { error.value = cause.message || '冲销失败'; }
  finally { busy.value = false; }
}
async function remove() {
  busy.value = true; error.value = '';
  try { await expenseApi.delete(deleteTarget.value.id); deleteTarget.value = null; selected.value = null; await load(pageNo.value); }
  catch (cause) { error.value = cause.message || '删除失败'; }
  finally { busy.value = false; }
}
watch(() => form.routeId, async (id) => {
  eligibleVehicles.value = []; routeVehicleError.value = '';
  if (form.attributionType !== 'ROUTE' || !id) return;
  try {
    eligibleVehicles.value = await expenseApi.eligibleRouteVehicles(id) || [];
    if (!eligibleVehicles.value.some((item) => String(item.vehicleId) === String(form.vehicleId))) form.vehicleId = '';
  } catch (cause) { routeVehicleError.value = cause.message || '合格车辆读取失败'; }
});
onMounted(() => load(1));
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="费用管理" description="查看费用与归属；审批来源费用请在审批中心发起和处理。"><template #actions><button type="button" @click="load(pageNo)">刷新</button><button v-if="can('expense:add')" class="primary" type="button" @click="createOpen = true">＋ 人工新增</button></template></PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load(pageNo)" />
    <FilterBar class="expense-filter">
      <div class="expense-filter-main">
        <label>费用编号或备注<input v-model.trim="filters.keyword" type="search" placeholder="输入费用编号或备注" /></label>
        <label>业务日期<input v-model="filters.date" type="date" /></label>
        <label>费用类型<select v-model="filters.type"><option value="">全部类型</option><option v-for="(label, code) in expenseTypeLabels" :key="code" :value="code">{{ label }}</option></select></label>
        <div class="expense-filter-actions">
          <button class="primary" type="button" @click="load(1)">查询</button>
          <button type="button" :aria-expanded="advancedFiltersOpen" aria-controls="expense-advanced-filters" @click="advancedFiltersOpen = !advancedFiltersOpen">{{ advancedFiltersOpen ? '收起筛选' : '更多筛选' }}{{ advancedFilterCount ? `（${advancedFilterCount}）` : '' }}</button>
          <button v-if="hasFilters" type="button" @click="clearFilters">清空</button>
        </div>
      </div>
      <div v-show="advancedFiltersOpen" id="expense-advanced-filters" class="expense-filter-advanced">
        <label class="expense-filter-route">具体线路<EntityPicker v-model="filters.routeId" :load-page="routeApi.list" :label-of="(item) => `${item.routeNo} · ${item.loadingPlace || ''}→${item.unloadingPlace || ''}`" placeholder="请选择线路" /></label>
        <label>归属<select v-model="filters.attribution"><option value="">全部归属</option><option value="DAILY">日常费用</option><option value="ROUTE">线路费用</option></select></label>
        <label>状态<select v-model="filters.status"><option value="">全部状态</option><option v-for="row in [...new Set(rows.map((item) => item.status))]" :key="row" :value="row">{{ statusLabels[row] || row }}</option></select></label>
        <label>车辆<select v-model="filters.vehicle"><option value="">全部车辆</option><option v-for="vehicle in vehicles" :key="vehicle.id" :value="String(vehicle.id)">{{ vehicle.plateNo }}</option></select></label>
      </div>
    </FilterBar>
    <DataTable v-if="!error" :columns="[{key:'expenseNo',label:'费用编号'},{key:'businessDate',label:'日期'},{key:'expenseType',label:'类型'},{key:'vehicleId',label:'车辆'},{key:'attributionType',label:'归属'},{key:'routeId',label:'线路'},{key:'amount',label:'金额',numeric:true},{key:'status',label:'状态'},{key:'actions',label:'操作'}]" :rows="rows" :loading="loading" :empty-text="Object.values(filters).some(Boolean) ? '没有匹配费用' : '暂无费用'">
      <template #vehicleId="{value}">{{ vehicleName(value) }}</template><template #routeId="{value}">{{ routeName(value) }}</template>
      <template #amount="{value}">{{ amount(value) }}</template>
      <template #status="{value}"><StatusTag :value="statusLabels[value] || value" :tone="value === 'PENDING_ATTRIBUTION' ? 'warning' : value === 'REVERSED' ? 'danger' : 'success'" /></template>
      <template #actions="{row}"><div class="ui-inline-actions"><button type="button" @click="showDetail(row)">详情</button><button v-if="can('expense:attribution:edit') || can('expense:reversal') || can('expense:delete')" type="button" @click="moreTarget = row">更多</button></div></template>
    </DataTable>
    <PaginationBar v-if="!error" :page-no="pageNo" :page-size="pageSize" :total="total" @change="load" />
    <FormDialog :open="!!moreTarget" title="费用操作" @close="moreTarget = null"><div v-if="moreTarget"><p>{{ moreTarget.expenseNo }} · {{ amount(moreTarget.amount) }} · {{ vehicleName(moreTarget.vehicleId) }}</p><div class="ui-object-actions"><button v-if="can('expense:attribution:edit')" type="button" @click="openAttribution(moreTarget)">调整归属</button><button v-if="can('expense:reversal') && ['ACTIVE', 'PENDING_ATTRIBUTION'].includes(moreTarget.status)" type="button" class="ui-danger-link" @click="openReversal(moreTarget)">冲销</button><button v-if="can('expense:delete') && moreTarget.sourceType !== 'APPROVAL'" type="button" class="ui-danger-link" @click="openDelete(moreTarget)">删除</button></div></div></FormDialog>
    <FormDialog :open="createOpen" title="人工新增费用" :busy="busy" @close="createOpen = false"><form @submit.prevent="create"><div class="ui-form-grid">
      <label class="ui-form-field">费用类型<select v-model="form.expenseType"><option v-for="(label, code) in expenseTypeLabels" :key="code" :value="code">{{ label }}</option></select></label>
      <label class="ui-form-field">业务日期<input v-model="form.businessDate" type="date" required /></label>
      <label class="ui-form-field">车辆<select v-if="form.attributionType === 'ROUTE' && form.routeId" v-model="form.vehicleId" required><option value="">请选择合格车辆</option><option v-for="vehicle in eligibleVehicles" :key="vehicle.vehicleId" :value="String(vehicle.vehicleId)">{{ vehicle.plateNo }}</option></select><EntityPicker v-else v-model="form.vehicleId" :load-page="masterApi.vehicles" :label-of="(item) => item.plateNo" placeholder="请选择车辆" required /><small v-if="routeVehicleError" class="error">{{ routeVehicleError }}</small></label>
      <label class="ui-form-field">归属<select v-model="form.attributionType"><option value="DAILY">日常费用</option><option value="ROUTE">线路费用</option></select></label>
      <label v-if="form.attributionType === 'ROUTE'" class="ui-form-field ui-full">关联线路<EntityPicker v-model="form.routeId" :load-page="routeApi.list" :label-of="(item) => `${item.routeNo} · ${item.loadingPlace || ''}→${item.unloadingPlace || ''}`" placeholder="不选择则待归属" :required="!energy" /></label>
      <label class="ui-form-field">金额（元）<input v-model="form.amount" type="number" min="0.01" step="0.01" required /></label>
      <label v-if="energy" class="ui-form-field">订单号<input v-model.trim="form.orderNo" required /></label><label v-if="energy" class="ui-form-field">开始时间<input v-model="form.startTime" type="datetime-local" required /></label><label v-if="energy" class="ui-form-field">数量<input v-model="form.quantity" type="number" min="0.001" step="0.001" required /></label>
      <label class="ui-form-field ui-full">备注<textarea v-model.trim="form.remark" rows="2" placeholder="可选" /></label>
    </div><div class="ui-dialog-actions"><button type="button" @click="createOpen = false">取消</button><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '保存费用' }}</button></div></form></FormDialog>
    <DetailDrawer :open="!!selected" title="费用详情" @close="selected = null"><template #subtitle>{{ selected?.expenseNo }}</template><div v-if="selected" class="ui-detail-sections">
      <section><h3>基本信息</h3><dl class="ui-detail-list"><div><dt>业务日期</dt><dd>{{ selected.businessDate }}</dd></div><div><dt>类型</dt><dd>{{ expenseTypeLabels[selected.expenseType] || selected.expenseType }}</dd></div><div><dt>金额</dt><dd>{{ amount(selected.amount) }}</dd></div><div><dt>状态</dt><dd>{{ statusLabels[selected.status] || selected.status }}</dd></div><div><dt>车辆</dt><dd>{{ vehicleName(selected.vehicleId) }}</dd></div><div><dt>来源</dt><dd>{{ sourceTypeLabels[selected.sourceType] || selected.sourceType }}</dd></div></dl></section>
      <section><h3>归属信息</h3><p>{{ attributionLabels[selected.attributionType] || selected.attributionType }} · {{ routeName(selected.routeId) }}</p><button v-if="can('expense:attribution:edit')" type="button" @click="openAttribution(selected)">调整归属</button></section>
      <section><h3>审批来源</h3><p>{{ approval?.approvalInstanceId || selected.approvalInstanceId ? '由审批通过后生成，可在审批中心查看对应申请。' : '非审批来源' }}</p></section>
      <section><h3>变更历史</h3><p v-if="detailLoading" class="muted">正在加载历史…</p><p v-else-if="!history.length" class="muted">暂无归属变更</p><ol v-else class="ui-timeline"><li v-for="item in history" :key="item.id">{{ attributionLabels[item.beforeAttributionType] || '未归属' }} → {{ attributionLabels[item.afterAttributionType] || '未归属' }} · {{ item.operationTime }}<p>{{ item.reason || '未填写原因' }}</p></li></ol></section>
      <section><h3>冲销记录</h3><p v-if="detailLoading" class="muted">正在加载冲销记录…</p><p v-else-if="selected.reversalOfId">本笔为冲销记录。</p><p v-else-if="selected.status === 'REVERSED' && !reversalRecords.length">未找到关联冲销记录，请核对数据。</p><p v-else-if="!reversalRecords.length" class="muted">暂无冲销记录</p><p v-for="item in reversalRecords" :key="item.id">{{ item.expenseNo }} · {{ amount(item.amount) }}</p></section>
    </div></DetailDrawer>
    <FormDialog :open="!!attributionTarget" title="调整费用归属" :busy="busy" @close="attributionTarget = null"><form v-if="attributionTarget" @submit.prevent="saveAttribution"><p>{{ attributionTarget.expenseNo }} · {{ amount(attributionTarget.amount) }} · {{ vehicleName(attributionTarget.vehicleId) }}</p><label class="ui-form-field">新归属<select v-model="attributionForm.attributionType"><option value="DAILY">日常费用</option><option value="ROUTE">线路费用或待归属</option></select></label><label v-if="attributionForm.attributionType === 'ROUTE'" class="ui-form-field">目标线路<EntityPicker v-model="attributionForm.routeId" :load-page="routeApi.list" :label-of="(item) => item.routeNo" placeholder="留空为待归属" /><small>选择线路后，后端会再次校验该车辆是否为正式自有参与车辆。</small></label><label class="ui-form-field">调整原因<input v-model.trim="attributionForm.reason" required /></label><div class="ui-dialog-actions"><button type="button" @click="attributionTarget = null">取消</button><button class="primary" :disabled="busy">确认归属</button></div></form></FormDialog>
    <FormDialog :open="!!reversalTarget && !confirmReversal" title="冲销费用" :busy="busy" @close="reversalTarget = null"><form v-if="reversalTarget" @submit.prevent="confirmReversal = true"><p>费用 {{ reversalTarget.expenseNo }} · {{ amount(reversalTarget.amount) }} · {{ vehicleName(reversalTarget.vehicleId) }}</p><p class="muted">冲销会新增反向记录，原费用不会被直接覆盖。</p><label class="ui-form-field">冲销原因<input v-model.trim="reversalReason" required /></label><div class="ui-dialog-actions"><button type="button" @click="reversalTarget = null">取消</button><button class="ui-danger-button" :disabled="busy">下一步：确认冲销</button></div></form></FormDialog>
    <ConfirmDialog :open="confirmReversal" title="最终确认冲销" :message="reversalTarget ? `冲销费用 ${reversalTarget.expenseNo}，金额 ${amount(reversalTarget.amount)}，车辆 ${vehicleName(reversalTarget.vehicleId)}。原因：${reversalReason}。原费用不会被覆盖，将生成反向记录。` : ''" danger :busy="busy" @cancel="confirmReversal = false" @confirm="reverse" />
    <ConfirmDialog :open="!!deleteTarget" title="删除人工费用" :message="deleteTarget ? `确认删除费用 ${deleteTarget.expenseNo}（${amount(deleteTarget.amount)}）？审批来源费用不能直接删除。` : ''" danger :busy="busy" @cancel="deleteTarget = null" @confirm="remove" />
  </div>
</template>

<style scoped>
.expense-filter { display: block; }
.expense-filter-main { display: grid; grid-template-columns: minmax(240px, 1fr) minmax(155px, 180px) minmax(155px, 180px) auto; gap: 12px; align-items: end; }
.expense-filter-main > label, .expense-filter-advanced > label { min-width: 0; }
.expense-filter-main input, .expense-filter-main select, .expense-filter-advanced select { width: 100%; min-width: 0; }
.expense-filter-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.expense-filter-actions button { white-space: nowrap; }
.expense-filter-advanced { display: grid; grid-template-columns: minmax(280px, 1.7fr) repeat(3, minmax(150px, 1fr)); gap: 12px; align-items: start; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--ui-border); }
.expense-filter-route :deep(.ui-entity-picker) { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 6px; }
.expense-filter-route :deep(.ui-entity-picker input), .expense-filter-route :deep(.ui-entity-picker select) { width: 100%; min-width: 0; }
.expense-filter-route :deep(.ui-entity-picker small), .expense-filter-route :deep(.ui-entity-picker .ui-pagination) { grid-column: 1 / -1; }
@media (max-width: 1100px) {
  .expense-filter-main { grid-template-columns: minmax(220px, 1fr) minmax(150px, 180px); }
  .expense-filter-advanced { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .expense-filter-main, .expense-filter-advanced { grid-template-columns: 1fr; }
  .expense-filter-route :deep(.ui-entity-picker) { grid-template-columns: 1fr; }
}
</style>
