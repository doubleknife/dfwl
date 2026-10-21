<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { masterApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { displayValue } from '../utils/displayLabels.js';
import { formatDateTime } from '../utils/dateTime.js';
import FilterBar from './FilterBar.vue';
import DataTable from './DataTable.vue';
import PaginationBar from './PaginationBar.vue';
import StatusTag from './StatusTag.vue';
import FormDialog from './FormDialog.vue';
import DetailDrawer from './DetailDrawer.vue';
import ConfirmDialog from './ConfirmDialog.vue';
import ErrorState from './ErrorState.vue';
import { displayedPercent, submittedFraction, editingStatus, invalidFixedStandard, thresholdTons } from './masterCatalogForm.js';

const props = defineProps({ type: { type: String, required: true } });
const names = { vehicles: '车辆', trailers: '车挂', products: '货物' };
const permissions = {
  vehicles: { add: 'BUTTON_VEHICLES_ADD', edit: 'BUTTON_VEHICLES_EDIT' },
  trailers: { add: 'BUTTON_TRAILERS_ADD', edit: 'BUTTON_TRAILERS_EDIT' },
  products: { add: 'BUTTON_PRODUCTS_MANAGE', edit: 'BUTTON_PRODUCTS_MANAGE' }
};
const columns = {
  vehicles: [{ key: 'plateNo', label: '车牌' }, { key: 'vehicleLicenseNo', label: '行驶证号' }, { key: 'energyType', label: '车辆类型' }, { key: 'maxLoad', label: '限载' }, { key: 'loadStandardType', label: '达标方式' }, { key: 'standardPercent', label: '达标比例' }, { key: 'standardTons', label: '达标吨数' }, { key: 'insuranceComplete', label: '保险' }, { key: 'currentDriverName', label: '当前司机' }, { key: 'currentTrailerPlateNo', label: '当前车挂' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }],
  trailers: [{ key: 'trailerNo', label: '车挂号' }, { key: 'plateNo', label: '车挂车牌' }, { key: 'insuranceComplete', label: '保险' }, { key: 'currentVehiclePlateNo', label: '当前车辆' }, { key: 'currentDriverName', label: '当前司机' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }],
  products: [{ key: 'name', label: '货物名称' }, { key: 'enabled', label: '状态' }, { key: 'actions', label: '操作' }]
};
const records = ref([]);
const page = ref(1); const pageSize = ref(20);
const total = ref(0);
const search = ref('');
const statusFilter = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const saveError = ref('');
const editor = ref(false);
const detail = ref(null);
const history = ref([]);
const toggleTarget = ref(null);
const form = reactive({ id: null, plateNo: '', vehicleLicenseNo: '', trailerNo: '', energyType: 'ELECTRIC', maxLoad: '', insuranceComplete: true, loadStandardType: 'PERCENT', loadStandardPercent: '0.9', loadStandardMinLoad: '', customerCode: '', customerName: '', productCode: '', productName: '', status: 1, remark: '' });
const listApi = computed(() => masterApi[props.type]);
const draftThreshold = computed(() => thresholdTons({ maxLoad: form.maxLoad, loadStandardType: form.loadStandardType, loadStandardPercent: submittedFraction(form.loadStandardPercent), loadStandardMinLoad: form.loadStandardMinLoad }));
const fixedStandardInvalid = computed(() => form.loadStandardType === 'FIXED' && invalidFixedStandard(form.maxLoad, form.loadStandardMinLoad));
const filtered = computed(() => records.value.filter((row) => {
  const label = `${row.plateNo || ''} ${row.trailerNo || ''} ${row.name || ''}`.toLowerCase();
  const status = props.type === 'products' ? (row.enabled ? 1 : 0) : row.status;
  return (!search.value || label.includes(search.value.toLowerCase())) && (!statusFilter.value || String(status) === statusFilter.value);
}));

async function load(nextPage = 1) {
  loading.value = true; error.value = '';
  try {
    const result = await listApi.value(nextPage, pageSize.value);
    records.value = result.records || [];
    total.value = Number(result.total || 0);
    page.value = nextPage;
  } catch (cause) { error.value = cause.message || `${names[props.type]}加载失败`; }
  finally { loading.value = false; }
}
function openEditor(row = null) {
  saveError.value = '';
  Object.assign(form, { id: row?.id || null, plateNo: row?.plateNo || '', vehicleLicenseNo: row?.vehicleLicenseNo || '', trailerNo: row?.trailerNo || '', energyType: row?.energyType || 'ELECTRIC', maxLoad: row?.maxLoad || '', insuranceComplete: row?.insuranceComplete ?? true, loadStandardType: row?.loadStandardType || 'PERCENT', loadStandardPercent: displayedPercent(row?.loadStandardPercent), loadStandardMinLoad: row?.loadStandardMinLoad || '', productName: row?.name || '', status: editingStatus(row, props.type), version: row?.version ?? null });
  editor.value = true;
}
function payload() {
  if (props.type === 'vehicles') return { plateNo: form.plateNo, vehicleLicenseNo: form.vehicleLicenseNo.trim(), insuranceComplete: form.insuranceComplete, energyType: form.energyType, maxLoad: form.maxLoad, loadStandardType: form.loadStandardType, loadStandardPercent: form.loadStandardType === 'PERCENT' ? submittedFraction(form.loadStandardPercent) : null, loadStandardMinLoad: form.loadStandardType === 'FIXED' ? form.loadStandardMinLoad : null, status: Number(form.status) };
  if (props.type === 'trailers') return { trailerNo: form.trailerNo, plateNo: form.plateNo || null, insuranceComplete: form.insuranceComplete, status: Number(form.status) };
  return { name: form.productName, enabled: Number(form.status) === 1, expectedVersion: form.version };
}
async function save() {
  if (props.type === 'vehicles' && fixedStandardInvalid.value) {
    saveError.value = '达标吨数必须介于 0 和车辆限载吨数之间。';
    return;
  }
  busy.value = true; saveError.value = '';
  try {
    const singular = { vehicles: 'Vehicle', trailers: 'Trailer', products: 'Product' }[props.type];
    const saved = form.id
      ? await masterApi[`update${singular}`](form.id, payload())
      : await masterApi[`create${singular}`](payload());
    if (props.type === 'vehicles' && String(saved?.vehicleLicenseNo || '').trim() !== form.vehicleLicenseNo.trim()) {
      throw new Error('服务端未确认保存行驶证号；请勿重复提交，并核对当前测试环境。');
    }
    editor.value = false;
    await load(page.value);
  } catch (cause) { saveError.value = cause.message || '保存失败'; }
  finally { busy.value = false; }
}
async function show(row) {
  detail.value = row; history.value = []; error.value = '';
  if (props.type !== 'vehicles' || !can('PAGE_BINDING')) return;
  try {
    const [driverHistory, trailerHistory] = await Promise.all([masterApi.vehicleHistory(row.id), masterApi.trailerHistory(row.id)]);
    history.value = [...driverHistory.map((item) => ({ ...item, kind: '司机车辆' })), ...trailerHistory.map((item) => ({ ...item, kind: '车辆车挂' }))]
      .sort((a, b) => String(b.bindTime).localeCompare(String(a.bindTime)));
  } catch (cause) { error.value = cause.message || '绑定历史加载失败'; }
}
async function toggleStatus() {
  if (!toggleTarget.value) return;
  busy.value = true; error.value = '';
  try {
    await masterApi.setProductStatus(toggleTarget.value.id, toggleTarget.value.enabled ? 0 : 1);
    toggleTarget.value = null; await load(page.value);
  } catch (cause) { error.value = cause.message || '状态更新失败'; }
  finally { busy.value = false; }
}
onMounted(() => load());
</script>

<template>
  <section class="ui-page-container ui-catalog">
    <ErrorState v-if="error" :message="error" @retry="load(page)" />
    <div class="ui-list-heading"><h2>{{ names[type] }}列表</h2><button v-if="can(permissions[type].add)" class="primary" type="button" @click="openEditor()">＋ 新增{{ names[type] }}</button></div>
    <FilterBar><label class="ui-search">{{ names[type] }}名称或编号<input v-model.trim="search" type="search" placeholder="筛选当前页" /></label><label>状态<select v-model="statusFilter"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label><button type="button" @click="load(page)">刷新</button></FilterBar>
    <p class="muted">当前页筛选；使用下方分页查看全部记录。</p>
    <DataTable v-if="!error" class="ui-paged-table" :columns="columns[type]" :rows="filtered" :loading="loading" :empty-text="search || statusFilter ? '当前页没有匹配结果' : `暂无${names[type]}`">
      <template #energyType="{value}">{{ displayValue('energyType', value) }}</template>
      <template #loadStandardType="{value}">{{ value === 'PERCENT' ? '按比例' : value === 'FIXED' ? '固定吨数' : '待核对' }}</template>
      <template #maxLoad="{value}">{{ value == null ? '—' : `${value} 吨` }}</template>
      <template #standardPercent="{row}">{{ row.loadStandardType === 'PERCENT' ? `${displayedPercent(row.loadStandardPercent)}%` : '—' }}</template>
      <template #standardTons="{row}">{{ thresholdTons(row) == null ? '待核对' : `${thresholdTons(row)} 吨` }}<small v-if="row.loadStandardType === 'FIXED' && invalidFixedStandard(row.maxLoad, row.loadStandardMinLoad)" class="ui-warning">原值超出限载</small></template>
      <template #insuranceComplete="{value}"><StatusTag :value="value ? '保险完整' : '保险不完整'" :tone="value ? 'success' : 'warning'" /></template>
      <template #status="{value}"><StatusTag :value="value === 1 ? '启用' : '停用'" /></template>
      <template #enabled="{value}"><StatusTag :value="value ? '启用' : '停用'" /></template>
      <template #currentDriverName="{value}">{{ value || '未绑定' }}</template>
      <template #currentTrailerPlateNo="{value}">{{ value || '未绑定' }}</template>
      <template #currentVehiclePlateNo="{value}">{{ value || '未绑定' }}</template>
      <template #actions="{row}"><div class="ui-inline-actions"><button v-if="type === 'vehicles' || type === 'trailers'" class="ui-detail-button" type="button" @click="show(row)">查看详情</button><button v-if="can(permissions[type].edit)" type="button" @click="openEditor(row)">编辑</button><button v-if="type === 'products' && can(permissions[type].edit)" type="button" :class="{ 'ui-danger-link': row.enabled }" @click="toggleTarget = row">{{ row.enabled ? '停用' : '启用' }}</button></div></template>
    </DataTable>
    <PaginationBar v-if="!error" :page-no="page" :page-size="pageSize" :total="total" @change="load" @change-size="(size) => { pageSize = size; load(1); }" />

    <FormDialog :open="editor" :title="`${form.id ? '编辑' : '新增'}${names[type]}`" :busy="busy" @close="editor = false">
      <form @submit.prevent="save"><div class="ui-form-grid">
        <template v-if="type === 'vehicles'"><label class="ui-form-field">车牌号<input v-model.trim="form.plateNo" required /></label><label class="ui-form-field">行驶证号<input v-model.trim="form.vehicleLicenseNo" required /></label><label class="ui-form-field">能源类型<select v-model="form.energyType"><option value="ELECTRIC">电</option><option value="GAS">气</option></select></label><label class="ui-form-field">最大载重（吨）<input v-model="form.maxLoad" required type="number" min="0.001" step="0.001" /></label><label class="ui-form-field">保险<select v-model="form.insuranceComplete"><option :value="true">完整</option><option :value="false">不完整</option></select></label><label class="ui-form-field">达标标准<select v-model="form.loadStandardType"><option value="PERCENT">按最大载重比例</option><option value="FIXED">按固定达标吨数</option></select></label><label v-if="form.loadStandardType === 'PERCENT'" class="ui-form-field">达标比例（%）<input v-model="form.loadStandardPercent" required type="number" min="0.01" max="100" step="0.01" /></label><label v-else class="ui-form-field">达标吨数（吨）<input v-model="form.loadStandardMinLoad" required type="number" min="0.001" :max="form.maxLoad || undefined" step="0.001" :aria-invalid="fixedStandardInvalid" /><small v-if="fixedStandardInvalid" class="ui-warning">达标吨数须大于 0 且不超过 {{ form.maxLoad || '限载' }} 吨；请先修正再保存。</small></label></template>
        <template v-else-if="type === 'trailers'"><label class="ui-form-field">车挂号<input v-model.trim="form.trailerNo" required /></label><label class="ui-form-field">车挂车牌<input v-model.trim="form.plateNo" /></label><label class="ui-form-field">保险<select v-model="form.insuranceComplete"><option :value="true">完整</option><option :value="false">不完整</option></select></label></template>
        <template v-else><label class="ui-form-field">货物名称<input v-model.trim="form.productName" required /></label></template>
        <label class="ui-form-field">状态<select v-model.number="form.status"><option :value="1">启用</option><option :value="0">停用</option></select></label>
      </div><p v-if="type === 'vehicles'" class="ui-threshold-preview">当前输入的达标吨数：<strong>{{ draftThreshold == null ? '请填写有效限载与标准' : `${draftThreshold} 吨` }}</strong></p><p v-if="saveError" class="ui-warning" role="alert">{{ saveError }}</p><div class="ui-dialog-actions"><button type="button" @click="editor = false">取消</button><button class="primary" :disabled="busy || (type === 'vehicles' && fixedStandardInvalid)">{{ busy ? '正在保存…' : '保存' }}</button></div></form>
    </FormDialog>
    <DetailDrawer :open="!!detail" :title="type === 'vehicles' ? '车辆详情' : '车挂详情'" @close="detail = null">
      <template v-if="detail"><p class="ui-detail-lead">{{ detail.plateNo || detail.trailerNo }}</p><p v-if="type === 'vehicles' && detail.loadStandardType === 'FIXED' && invalidFixedStandard(detail.maxLoad, detail.loadStandardMinLoad)" class="ui-warning">原达标吨数超出限载，请核对；在确认前不要据此判断达标结果。</p><h3>车辆资料与当前关系</h3><dl class="ui-detail-grid"><dt>状态</dt><dd><StatusTag :value="detail.status" /></dd><dt>保险</dt><dd><StatusTag :value="detail.insuranceComplete ? '保险完整' : '保险不完整'" /></dd><template v-if="type === 'vehicles'"><dt>行驶证号</dt><dd>{{ detail.vehicleLicenseNo || '—' }}</dd><dt>能源</dt><dd>{{ displayValue('energyType', detail.energyType) }}</dd><dt>最大载重</dt><dd>{{ detail.maxLoad }} 吨</dd><dt>达标比例</dt><dd>{{ detail.loadStandardType === 'PERCENT' ? `${displayedPercent(detail.loadStandardPercent)}%` : '—' }}</dd><dt>达标吨数</dt><dd>{{ thresholdTons(detail) == null ? '待核对' : `${thresholdTons(detail)} 吨` }}</dd><dt>当前司机</dt><dd>{{ detail.currentDriverName || '未绑定' }}</dd><dt>当前车挂</dt><dd>{{ detail.currentTrailerPlateNo || '未绑定' }}</dd></template><template v-else><dt>车挂编号</dt><dd>{{ detail.trailerNo }}</dd><dt>当前车辆</dt><dd>{{ detail.currentVehiclePlateNo || '未绑定' }}</dd><dt>当前司机</dt><dd>{{ detail.currentDriverName || '未绑定' }}</dd></template></dl>
        <template v-if="type === 'vehicles' && can('PAGE_BINDING')"><h3>绑定历史</h3><div v-if="!history.length" class="ui-empty">暂无绑定历史</div><ol v-else class="ui-timeline"><li v-for="item in history" :key="`${item.kind}-${item.id}`"><strong>{{ item.kind }}：{{ item.subjectName }} ↔ {{ item.targetName }}</strong><p>绑定 {{ formatDateTime(item.bindTime) }} · 操作人 {{ item.bindByPhone || '未记录' }}</p><p v-if="item.unbindTime">解绑 {{ formatDateTime(item.unbindTime) }} · 操作人 {{ item.unbindByPhone || '未记录' }} · {{ item.unbindReason || '未填写原因' }}</p></li></ol></template>
      </template>
    </DetailDrawer>
    <ConfirmDialog :open="!!toggleTarget" :title="`${toggleTarget?.enabled ? '停用' : '启用'}货物`" :message="`确认${toggleTarget?.enabled ? '停用' : '启用'} ${toggleTarget?.name || ''}？停用后不能用于新模板。`" :busy="busy" :danger="!!toggleTarget?.enabled" @cancel="toggleTarget = null" @confirm="toggleStatus" />
  </section>
</template>

<style scoped>
.ui-warning { display: block; color: #9b5c12; background: #fff7e9; border: 1px solid #e9c78f; border-radius: 6px; padding: 8px 10px; margin: 6px 0; }
.ui-warning input { margin-right: 8px; }
.ui-threshold-preview { padding: 10px 12px; border-radius: 6px; background: #eaf4ef; color: #245448; }
</style>
