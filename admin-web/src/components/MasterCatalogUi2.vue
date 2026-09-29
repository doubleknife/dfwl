<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { masterApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { displayValue } from '../utils/displayLabels.js';
import FilterBar from './FilterBar.vue';
import DataTable from './DataTable.vue';
import PaginationBar from './PaginationBar.vue';
import StatusTag from './StatusTag.vue';
import FormDialog from './FormDialog.vue';
import DetailDrawer from './DetailDrawer.vue';
import ConfirmDialog from './ConfirmDialog.vue';
import ErrorState from './ErrorState.vue';

const props = defineProps({ type: { type: String, required: true } });
const names = { vehicles: '车辆', trailers: '车挂', products: '货物' };
const permissions = {
  vehicles: { add: 'vehicle:add', edit: 'vehicle:edit' },
  trailers: { add: 'trailer:add', edit: 'trailer:edit' },
  products: { add: 'template:manage', edit: 'template:manage' }
};
const columns = {
  vehicles: [{ key: 'plateNo', label: '车牌' }, { key: 'energyType', label: '能源' }, { key: 'maxLoad', label: '最大载重' }, { key: 'currentDriverName', label: '当前司机' }, { key: 'currentTrailerPlateNo', label: '当前车挂' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }],
  trailers: [{ key: 'trailerNo', label: '车挂号' }, { key: 'plateNo', label: '车挂车牌' }, { key: 'insuranceComplete', label: '保险' }, { key: 'currentVehiclePlateNo', label: '当前车辆' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }],
  products: [{ key: 'name', label: '货物名称' }, { key: 'enabled', label: '状态' }, { key: 'actions', label: '操作' }]
};
const records = ref([]);
const page = ref(1);
const total = ref(0);
const search = ref('');
const statusFilter = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const editor = ref(false);
const detail = ref(null);
const history = ref([]);
const toggleTarget = ref(null);
const form = reactive({ id: null, plateNo: '', trailerNo: '', energyType: 'ELECTRIC', maxLoad: '', insuranceComplete: true, loadStandardType: 'PERCENT', loadStandardPercent: '0.9', loadStandardMinLoad: '', customerCode: '', customerName: '', productCode: '', productName: '', status: 1, remark: '' });
const listApi = computed(() => masterApi[props.type]);
const filtered = computed(() => records.value.filter((row) => {
  const label = `${row.plateNo || ''} ${row.trailerNo || ''} ${row.name || ''}`.toLowerCase();
  const status = props.type === 'products' ? (row.enabled ? 1 : 0) : row.status;
  return (!search.value || label.includes(search.value.toLowerCase())) && (!statusFilter.value || String(status) === statusFilter.value);
}));

async function load(nextPage = 1) {
  loading.value = true; error.value = '';
  try {
    const result = await listApi.value(nextPage, 20);
    records.value = result.records || [];
    total.value = Number(result.total || 0);
    page.value = nextPage;
  } catch (cause) { error.value = cause.message || `${names[props.type]}加载失败`; }
  finally { loading.value = false; }
}
function openEditor(row = null) {
  Object.assign(form, { id: row?.id || null, plateNo: row?.plateNo || '', trailerNo: row?.trailerNo || '', energyType: row?.energyType || 'ELECTRIC', maxLoad: row?.maxLoad || '', insuranceComplete: row?.insuranceComplete ?? true, loadStandardType: row?.loadStandardType || 'PERCENT', loadStandardPercent: row?.loadStandardPercent ?? '0.9', loadStandardMinLoad: row?.loadStandardMinLoad || '', productName: row?.name || '', status: row ? (row.enabled ? 1 : 0) : 1, version: row?.version ?? null });
  editor.value = true;
}
function payload() {
  if (props.type === 'vehicles') return { plateNo: form.plateNo, insuranceComplete: form.insuranceComplete, energyType: form.energyType, maxLoad: form.maxLoad, loadStandardType: form.loadStandardType, loadStandardPercent: form.loadStandardType === 'PERCENT' ? form.loadStandardPercent : null, loadStandardMinLoad: form.loadStandardType === 'FIXED' ? form.loadStandardMinLoad : null, status: Number(form.status) };
  if (props.type === 'trailers') return { trailerNo: form.trailerNo, plateNo: form.plateNo || null, insuranceComplete: form.insuranceComplete, status: Number(form.status) };
  return { name: form.productName, enabled: Number(form.status) === 1, expectedVersion: form.version };
}
async function save() {
  busy.value = true; error.value = '';
  try {
    const singular = { vehicles: 'Vehicle', trailers: 'Trailer', products: 'Product' }[props.type];
    if (form.id) await masterApi[`update${singular}`](form.id, payload());
    else await masterApi[`create${singular}`](payload());
    editor.value = false;
    await load(page.value);
  } catch (cause) { error.value = cause.message || '保存失败'; }
  finally { busy.value = false; }
}
async function show(row) {
  detail.value = row; history.value = []; error.value = '';
  if (props.type !== 'vehicles' || !can('vehicle:bindingHistory')) return;
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
    <DataTable v-if="!error" :columns="columns[type]" :rows="filtered" :loading="loading" :empty-text="search || statusFilter ? '当前页没有匹配结果' : `暂无${names[type]}`">
      <template #energyType="{value}">{{ displayValue('energyType', value) }}</template>
      <template #maxLoad="{value}">{{ value == null ? '—' : `${value} 吨` }}</template>
      <template #insuranceComplete="{value}"><StatusTag :value="value ? '保险完整' : '保险不完整'" :tone="value ? 'success' : 'warning'" /></template>
      <template #status="{value}"><StatusTag :value="value === 1 ? '启用' : '停用'" :tone="value === 1 ? 'success' : 'neutral'" /></template>
      <template #enabled="{value}"><StatusTag :value="value ? '启用' : '停用'" :tone="value ? 'success' : 'neutral'" /></template>
      <template #currentDriverName="{value}">{{ value || '未绑定' }}</template>
      <template #currentTrailerPlateNo="{value}">{{ value || '未绑定' }}</template>
      <template #currentVehiclePlateNo="{value}">{{ value || '未绑定' }}</template>
      <template #actions="{row}"><div class="ui-inline-actions"><button v-if="type === 'vehicles' || type === 'trailers'" type="button" @click="show(row)">详情</button><button v-if="can(permissions[type].edit)" type="button" @click="openEditor(row)">编辑</button><button v-if="type === 'products' && can(permissions[type].edit)" type="button" @click="toggleTarget = row">{{ row.enabled ? '停用' : '启用' }}</button></div></template>
    </DataTable>
    <PaginationBar v-if="!error" :page-no="page" :page-size="20" :total="total" @change="load" />

    <FormDialog :open="editor" :title="`${form.id ? '编辑' : '新增'}${names[type]}`" :busy="busy" @close="editor = false">
      <form @submit.prevent="save"><div class="ui-form-grid">
        <template v-if="type === 'vehicles'"><label class="ui-form-field">车牌号<input v-model.trim="form.plateNo" required /></label><label class="ui-form-field">能源类型<select v-model="form.energyType"><option value="ELECTRIC">电</option><option value="GAS">气</option></select></label><label class="ui-form-field">最大载重（吨）<input v-model="form.maxLoad" required type="number" min="0.001" step="0.001" /></label><label class="ui-form-field">保险<select v-model="form.insuranceComplete"><option :value="true">完整</option><option :value="false">不完整</option></select></label><label class="ui-form-field">达标标准<select v-model="form.loadStandardType"><option value="PERCENT">按最大载重比例</option><option value="FIXED">按最低载重</option></select></label><label v-if="form.loadStandardType === 'PERCENT'" class="ui-form-field">达标比例<input v-model="form.loadStandardPercent" required type="number" min="0" max="1" step="0.0001" /></label><label v-else class="ui-form-field">最低载重（吨）<input v-model="form.loadStandardMinLoad" required type="number" min="0" step="0.001" /></label></template>
        <template v-else-if="type === 'trailers'"><label class="ui-form-field">车挂号<input v-model.trim="form.trailerNo" required /></label><label class="ui-form-field">车挂车牌<input v-model.trim="form.plateNo" /></label><label class="ui-form-field">保险<select v-model="form.insuranceComplete"><option :value="true">完整</option><option :value="false">不完整</option></select></label></template>
        <template v-else><label class="ui-form-field">货物名称<input v-model.trim="form.productName" required /></label></template>
        <label class="ui-form-field">状态<select v-model.number="form.status"><option :value="1">启用</option><option :value="0">停用</option></select></label>
      </div><div class="ui-dialog-actions"><button type="button" @click="editor = false">取消</button><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '保存' }}</button></div></form>
    </FormDialog>
    <DetailDrawer :open="!!detail" :title="type === 'vehicles' ? '车辆详情' : '车挂详情'" @close="detail = null">
      <template v-if="detail"><p class="ui-detail-lead">{{ detail.plateNo || detail.trailerNo }}</p><dl class="ui-detail-grid"><dt>状态</dt><dd>{{ detail.status === 1 ? '启用' : '停用' }}</dd><dt>保险</dt><dd>{{ detail.insuranceComplete ? '完整' : '不完整' }}</dd><template v-if="type === 'vehicles'"><dt>能源</dt><dd>{{ displayValue('energyType', detail.energyType) }}</dd><dt>最大载重</dt><dd>{{ detail.maxLoad }} 吨</dd><dt>当前司机</dt><dd>{{ detail.currentDriverName || '未绑定' }}</dd><dt>当前车挂</dt><dd>{{ detail.currentTrailerPlateNo || '未绑定' }}</dd></template><template v-else><dt>当前车辆</dt><dd>{{ detail.currentVehiclePlateNo || '未绑定' }}</dd></template></dl>
        <template v-if="type === 'vehicles' && can('vehicle:bindingHistory')"><h3>绑定历史</h3><div v-if="!history.length" class="ui-empty">暂无绑定历史</div><ol v-else class="ui-timeline"><li v-for="item in history" :key="`${item.kind}-${item.id}`"><strong>{{ item.kind }}：{{ item.subjectName }} ↔ {{ item.targetName }}</strong><p>绑定 {{ item.bindTime }} · 操作人 {{ item.bindByPhone || '未记录' }}</p><p v-if="item.unbindTime">解绑 {{ item.unbindTime }} · 操作人 {{ item.unbindByPhone || '未记录' }} · {{ item.unbindReason || '未填写原因' }}</p></li></ol></template>
      </template>
    </DetailDrawer>
    <ConfirmDialog :open="!!toggleTarget" :title="`${toggleTarget?.enabled ? '停用' : '启用'}货物`" :message="`确认${toggleTarget?.enabled ? '停用' : '启用'} ${toggleTarget?.name || ''}？停用后不能用于新模板。`" :busy="busy" :danger="!!toggleTarget?.enabled" @cancel="toggleTarget = null" @confirm="toggleStatus" />
  </section>
</template>
