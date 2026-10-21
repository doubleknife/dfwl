<script setup>
import { computed, reactive, ref } from 'vue';
import { birthDateFromIdCard, fullAge, idCardValidationMessage } from '../utils/driverIdentity.js';
import { masterApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from './PageHeader.vue';
import FilterBar from './FilterBar.vue';
import DataTable from './DataTable.vue';
import StatusTag from './StatusTag.vue';
import FormDialog from './FormDialog.vue';
import DetailDrawer from './DetailDrawer.vue';
import PaginationBar from './PaginationBar.vue';
import ImportFilePicker from './ImportFilePicker.vue';
import ImportResultFilter from './ImportResultFilter.vue';
import SummaryCards from './SummaryCards.vue';
import ImportCompletionSummary from './ImportCompletionSummary.vue';

const props = defineProps({ drivers: { type: Array, default: () => [] }, pageNo: Number, pageSize: { type: Number, default: 20 }, total: Number, loading: Boolean });
const emit = defineEmits(['refresh', 'page-change', 'size-change', 'search']);
const filters = reactive({ keyword: '', driverType: '', status: '' });
const form = reactive({ name: '', phone: '', driverType: 'INTERNAL', status: 1, initialPassword: '', drivingLicenseNo: '', idCardNo: '' });
const openCreate = ref(false);
const editing = ref(null);
const selected = ref(null);
const saving = ref(false);
const formError = ref('');
const idCardError = ref('');
const importOpen = ref(false);
const importFile = ref(null);
const importPreview = ref(null);
const importFilter = ref('ALL');
const importBusy = ref(false);
const importCompletion = ref(null);
const importRows = computed(() => (importPreview.value?.rows || []).filter((row) => importFilter.value === 'ALL'
  || (importFilter.value === 'PASS' ? row.errors.length === 0 : row.errors.length > 0)));
async function selectImportFile(file) {
  importFile.value = file;
  importPreview.value = null;
  importFilter.value = 'ALL';
  importCompletion.value = null;
  if (importFile.value) await previewImport(importFile.value);
}
function clearImportFile() {
  if (importBusy.value) return;
  importFile.value = null;
  importPreview.value = null;
  importFilter.value = 'ALL';
  importCompletion.value = null;
}
function closeImport() {
  if (importBusy.value) return;
  clearImportFile();
  importOpen.value = false;
}
async function downloadImportTemplate() {
  const blob = await masterApi.initialDriverVehicleTemplate();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = '自有司机车辆初始导入模板.xlsx';
  link.click();
  URL.revokeObjectURL(url);
}
async function previewImport(file) {
  if (!file) return;
  importBusy.value = true; importPreview.value = null; importCompletion.value = null;
  try {
    importPreview.value = await masterApi.initialDriverVehiclePreview(file);
  } catch { /* API 层已统一弹窗。 */ }
  finally { importBusy.value = false; }
}
async function confirmImport() {
  if (!can('BUTTON_DRIVERS_IMPORT') || !importFile.value || !importPreview.value || importPreview.value.failed) return;
  importBusy.value = true; importCompletion.value = null;
  try {
    const result = await masterApi.initialDriverVehicleConfirm(importFile.value);
    importCompletion.value = { total: importPreview.value.total, success: result.imported,
      failed: 0, failures: [] };
    importPreview.value = null;
    importFile.value = null;
    emit('refresh');
  } catch (cause) {
    if (Number(cause?.status) >= 400 && Number(cause?.status) < 500 && ![401, 403].includes(Number(cause.status))) {
      importCompletion.value = { total: importPreview.value.total, success: 0,
        failed: importPreview.value.total, failures: [{ reason: `${cause.message || '校验失败'}；整批未导入` }] };
      importPreview.value = null;
    }
    // API 层已统一弹窗；网络或服务异常时不推断是否入库。
  }
  finally { importBusy.value = false; }
}
const vehicleName = (driver) => driver?.driverType === 'OUTSOURCED' ? '不适用' : (driver?.currentVehiclePlateNo || '未绑定');
const trailerName = (driver) => driver?.driverType === 'OUTSOURCED' ? '不适用' : (driver?.currentTrailerPlateNo || '未绑定');
const driverBirthDate = (driver) => driver?.idCardNo ? birthDateFromIdCard(driver.idCardNo) : (driver?.birthDate || null);
function validateCertificates() {
  idCardError.value = idCardValidationMessage(form.idCardNo);
  formError.value = !idCardError.value && !form.drivingLicenseNo.trim() ? '请填写驾驶证号' : '';
  return !idCardError.value && !formError.value;
}
function closeForm() {
  openCreate.value = false;
  editing.value = null;
  formError.value = '';
  idCardError.value = '';
}
function openNew() {
  Object.assign(form, { name: '', phone: '', driverType: 'INTERNAL', status: 1, initialPassword: '', drivingLicenseNo: '', idCardNo: '' });
  formError.value = '';
  idCardError.value = '';
  openCreate.value = true;
}
async function create() {
  if (!validateCertificates()) return;
  saving.value = true;
  try {
    await masterApi.createDriver({ ...form, idCardNo: form.idCardNo.trim().toUpperCase(), drivingLicenseNo: form.drivingLicenseNo.trim() });
    Object.assign(form, { name: '', phone: '', driverType: 'INTERNAL', status: 1, initialPassword: '', drivingLicenseNo: '', idCardNo: '' });
    closeForm();
    emit('refresh');
  } catch { /* API 层已统一弹窗。 */ }
  finally { saving.value = false; }
}
function openEdit(row) {
  editing.value = row;
  Object.assign(form, { name: row.name || '', phone: row.phone || '', driverType: row.driverType, status: Number(row.status), initialPassword: '', drivingLicenseNo: row.drivingLicenseNo || '', idCardNo: row.idCardNo || '' });
  formError.value = '';
  idCardError.value = '';
}
async function update() {
  if (!validateCertificates()) return;
  saving.value = true; formError.value = '';
  try {
    const { initialPassword, ...fields } = form;
    await masterApi.updateDriver(editing.value.id, { ...fields, idCardNo: fields.idCardNo.trim().toUpperCase(), drivingLicenseNo: fields.drivingLicenseNo.trim() });
    closeForm();
    emit('refresh');
  } catch { /* API 层已统一弹窗。 */ }
  finally { saving.value = false; }
}
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="司机管理" description="查看司机、当前车辆绑定与在职状态；按全部司机查询并分页。">
      <template #actions><button type="button" @click="$emit('refresh')">刷新</button><button v-if="can('BUTTON_DRIVERS_IMPORT')" type="button" @click="importOpen = true">批量导入</button><button v-if="can('BUTTON_DRIVERS_ADD')" type="button" class="primary" @click="openNew">＋ 新增司机</button></template>
    </PageHeader>
    <FormDialog :open="importOpen && can('BUTTON_DRIVERS_IMPORT')" title="批量导入" :busy="importBusy" wide fixed-height @close="closeImport">
    <section class="ui-initial-import" aria-label="自有司机车辆批量导入">
      <div class="ui-initial-import-head"><div><h2>自有司机与公司车辆初始导入</h2><p>只有未录入的司机、车辆才能进行导入。</p></div><button class="ui-import-template-button" type="button" @click="downloadImportTemplate">下载模板</button></div>
      <div class="ui-initial-import-controls"><ImportFilePicker :file="importFile" :disabled="importBusy" :loading="importBusy && !importPreview" label="" accept=".xlsx" @clear="clearImportFile" @select="selectImportFile" /><span v-if="importBusy && importPreview">正在导入…</span></div>
      <ImportCompletionSummary v-if="importCompletion" :title="importCompletion.failed ? '整批导入失败' : '整批导入完成'" :summary="importCompletion" unit="组" :note="importCompletion.failed ? '请修正文件后重新选择并预览。' : '自有司机、公司车辆、登录账号及绑定关系已整批创建。'" />
      <template v-if="importPreview">
        <div class="ui-import-result-toolbar"><SummaryCards label="司机车辆导入预览统计" :items="[{ label: '总计', value: `${importPreview.total} 行` }, { label: '通过', value: `${importPreview.passed} 行` }, { label: '失败', value: `${importPreview.failed} 行` }]" /><ImportResultFilter v-model="importFilter" /></div>
        <div class="ui-initial-import-table"><table><thead><tr><th>Excel 行号</th><th>车牌号</th><th>驾驶员姓名</th><th>电话</th><th>结果与原因</th></tr></thead><tbody><tr v-for="row in importRows" :key="row.rowNo"><td>{{ row.rowNo }}</td><td>{{ row.plateNo }}</td><td>{{ row.driverName }}</td><td>{{ row.phone }}</td><td><span v-if="!row.errors.length">校验通过</span><ul v-else><li v-for="(issue, index) in row.errors" :key="index">{{ issue.field }}：{{ issue.reason }}</li></ul></td></tr><tr v-if="!importRows.length"><td colspan="5" class="ui-initial-import-empty">{{ importFilter === 'FAIL' ? '没有失败记录' : importFilter === 'PASS' ? '没有通过记录' : '没有导入记录' }}</td></tr></tbody></table></div>
        <div class="ui-initial-import-actions"><p v-if="importPreview.failed" class="ui-import-validation-hint">请修正 Excel 后重新选择文件；任一行失败时整批不能确认。</p><button v-if="can('BUTTON_DRIVERS_IMPORT')" type="button" class="primary" :disabled="importBusy || !!importPreview.failed" @click="confirmImport">确认整批导入</button></div>
      </template>
    </section>
    </FormDialog>
    <FilterBar>
      <label class="ui-search">姓名或手机号<input v-model.trim="filters.keyword" type="search" placeholder="搜索司机" @keyup.enter="emit('search', { ...filters })" /></label>
      <label>司机类型<select v-model="filters.driverType"><option value="">全部类型</option><option value="INTERNAL">自有司机</option><option value="OUTSOURCED">外协司机</option></select></label>
      <label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label>
      <button class="primary" type="button" @click="emit('search', { ...filters })">查询</button>
    </FilterBar>
    <DataTable class="ui-paged-table" :columns="[{ key: 'name', label: '姓名' }, { key: 'phone', label: '手机号' }, { key: 'driverType', label: '类型' }, { key: 'idCardNo', label: '身份证号' }, { key: 'drivingLicenseNo', label: '驾驶证号' }, { key: 'birthDate', label: '出生日期' }, { key: 'age', label: '年龄' }, { key: 'currentVehicleId', label: '当前车辆' }, { key: 'currentTrailerPlateNo', label: '当前车挂' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }]" :rows="drivers" :loading="loading" empty-text="当前条件下没有司机">
      <template #driverType="{ value }">{{ value === 'INTERNAL' ? '自有司机' : value === 'OUTSOURCED' ? '外协司机' : '—' }}</template>
      <template #currentVehicleId="{ row }">{{ vehicleName(row) }}</template>
      <template #currentTrailerPlateNo="{ row }">{{ trailerName(row) }}</template>
      <template #birthDate="{ row }">{{ driverBirthDate(row) || (row.idCardNo ? '身份证待核对' : '未填写') }}</template>
      <template #age="{ row }">{{ fullAge(driverBirthDate(row)) == null ? '待核对' : `${fullAge(driverBirthDate(row))} 岁` }}</template>
      <template #status="{ value }"><StatusTag :value="value === 1 ? '启用' : '停用'" /></template>
      <template #actions="{ row }"><div class="ui-inline-actions"><button type="button" class="ui-detail-button" @click="selected = row">查看详情</button><button v-if="can('BUTTON_DRIVERS_EDIT')" type="button" @click="openEdit(row)">编辑</button></div></template>
    </DataTable>
    <PaginationBar :page-no="pageNo" :page-size="pageSize" :total="total" @change="emit('page-change', $event)" @change-size="emit('size-change', $event)" />
    <FormDialog :open="openCreate" title="新增司机" :busy="saving" @close="closeForm">
      <form @submit.prevent="create"><div class="ui-form-grid">
        <label class="ui-form-field">姓名<input v-model.trim="form.name" required maxlength="50" placeholder="请输入司机姓名" /></label>
        <label class="ui-form-field">手机号<input v-model.trim="form.phone" required inputmode="tel" placeholder="请输入手机号" /></label>
        <label class="ui-form-field">司机类型<select v-model="form.driverType"><option value="INTERNAL">自有司机</option><option value="OUTSOURCED">外协司机</option></select></label>
        <label class="ui-form-field">身份证号<input v-model.trim="form.idCardNo" required maxlength="18" :aria-invalid="!!idCardError" @input="idCardError = ''" @blur="form.idCardNo = form.idCardNo.toUpperCase(); idCardError = idCardValidationMessage(form.idCardNo)" /><span v-if="idCardError" class="ui-field-error" role="alert">{{ idCardError }}</span></label>
        <label class="ui-form-field">驾驶证号<input v-model.trim="form.drivingLicenseNo" required /></label>
        <label class="ui-form-field">初始密码<input v-model="form.initialPassword" type="password" autocomplete="new-password" minlength="6" required placeholder="至少 6 位" /></label>
      </div><p v-if="formError" class="ui-form-error" role="alert">{{ formError }}</p><div class="ui-dialog-actions"><button type="button" :disabled="saving" @click="closeForm">取消</button><button class="primary" :disabled="saving">{{ saving ? '正在保存…' : '保存司机' }}</button></div></form>
    </FormDialog>
    <FormDialog :open="!!editing" title="编辑司机" :busy="saving" @close="closeForm">
      <form v-if="editing" @submit.prevent="update"><div class="ui-form-grid">
        <label class="ui-form-field">姓名<input v-model.trim="form.name" required maxlength="50" /></label>
        <label class="ui-form-field">手机号<input v-model.trim="form.phone" required inputmode="tel" :readonly="!!editing.userId" /></label>
        <label class="ui-form-field">司机类型<select v-model="form.driverType"><option value="INTERNAL">自有司机</option><option value="OUTSOURCED">外协司机</option></select></label>
        <label class="ui-form-field">状态<select v-model.number="form.status"><option :value="1">启用</option><option :value="0">停用</option></select></label>
        <label class="ui-form-field">身份证号<input v-model.trim="form.idCardNo" required maxlength="18" :aria-invalid="!!idCardError" @input="idCardError = ''" @blur="form.idCardNo = form.idCardNo.toUpperCase(); idCardError = idCardValidationMessage(form.idCardNo)" /><span v-if="idCardError" class="ui-field-error" role="alert">{{ idCardError }}</span></label>
        <label class="ui-form-field">驾驶证号<input v-model.trim="form.drivingLicenseNo" required /></label>
      </div><p class="muted">已关联登录账号的手机号不可直接修改；司机类型及车辆绑定限制由服务端校验。</p><p v-if="formError" class="ui-form-error" role="alert">{{ formError }}</p><div class="ui-dialog-actions"><button type="button" :disabled="saving" @click="closeForm">取消</button><button class="primary" :disabled="saving">{{ saving ? '正在保存…' : '保存修改' }}</button></div></form>
    </FormDialog>
    <DetailDrawer :open="!!selected" :title="selected?.name || '司机详情'" @close="selected = null">
      <dl v-if="selected" class="ui-detail-list"><div><dt>手机号</dt><dd>{{ selected.phone || '—' }}</dd></div><div><dt>司机类型</dt><dd>{{ selected.driverType === 'INTERNAL' ? '自有司机' : '外协司机' }}</dd></div><div><dt>当前车辆</dt><dd>{{ vehicleName(selected) }}</dd></div><div><dt>当前车挂</dt><dd>{{ trailerName(selected) }}</dd></div><div><dt>状态</dt><dd><StatusTag :value="selected.status" /></dd></div><div><dt>身份证号</dt><dd>{{ selected.idCardNo || '—' }}</dd></div><div><dt>驾驶证号</dt><dd>{{ selected.drivingLicenseNo || '—' }}</dd></div><div><dt>出生日期</dt><dd>{{ driverBirthDate(selected) || '待核对' }}</dd></div><div><dt>年龄</dt><dd>{{ fullAge(driverBirthDate(selected)) == null ? '待核对' : `${fullAge(driverBirthDate(selected))} 岁` }}</dd></div></dl>
    </DetailDrawer>
  </div>
</template>

<style scoped>
.ui-initial-import { display: flex; flex: 1; flex-direction: column; min-height: 0; overflow: hidden; background: #fff; }
.ui-initial-import-head, .ui-initial-import-controls, .ui-initial-import-summary, .ui-initial-import-actions { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; justify-content: space-between; }
.ui-initial-import-head, .ui-initial-import-controls, .ui-initial-import-actions { flex: none; }
.ui-initial-import :deep(.ui-import-result-toolbar) { flex: none; }
.ui-initial-import-head h2 { margin: 0 0 4px; font-size: 18px; }
.ui-initial-import-head p { margin: 0; color: #5b746c; }
.ui-initial-import-controls, .ui-initial-import-summary { margin-top: 18px; }
.ui-initial-import-controls { justify-content: flex-start; }
.ui-initial-import-table { flex: 1; min-height: 0; overflow: auto; margin-top: 12px; }
.ui-initial-import-table table { width: 100%; border-collapse: collapse; min-width: 720px; }
.ui-initial-import-table th, .ui-initial-import-table td { padding: 10px; border-bottom: 1px solid #e2ebe5; text-align: left; vertical-align: top; }
.ui-initial-import-table th { position: sticky; top: 0; background: #eff6f1; }
.ui-initial-import-table ul { margin: 0; padding-left: 20px; color: #b14436; }
.ui-initial-import-table .ui-initial-import-empty { padding:24px 10px; text-align:center; color:#5b746c; }
.ui-initial-import-actions { margin-top: 14px; }
</style>
