<script setup>
import { reactive, ref } from 'vue';
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

const props = defineProps({ drivers: { type: Array, default: () => [] }, pageNo: Number, total: Number, loading: Boolean });
const emit = defineEmits(['refresh', 'page-change', 'search']);
const filters = reactive({ keyword: '', driverType: '', status: '' });
const form = reactive({ name: '', phone: '', driverType: 'INTERNAL', status: 1, initialPassword: '', drivingLicenseNo: '', idCardNo: '' });
const openCreate = ref(false);
const editing = ref(null);
const selected = ref(null);
const saving = ref(false);
const formError = ref('');
const idCardError = ref('');
const vehicleName = (driver) => driver?.driverType === 'OUTSOURCED' ? '不适用' : (driver?.currentVehiclePlateNo || '未绑定');
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
  } catch (cause) { formError.value = cause.message || '新增司机失败'; }
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
  } catch (cause) { formError.value = cause.message || '编辑司机失败'; }
  finally { saving.value = false; }
}
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="司机管理" description="查看司机、当前车辆绑定与在职状态；按全部司机查询并分页。">
      <template #actions><button type="button" @click="$emit('refresh')">刷新</button><button v-if="can('BUTTON_DRIVERS_ADD')" type="button" class="primary" @click="openNew">＋ 新增司机</button></template>
    </PageHeader>
    <FilterBar>
      <label class="ui-search">姓名或手机号<input v-model.trim="filters.keyword" type="search" placeholder="搜索司机" @keyup.enter="emit('search', { ...filters })" /></label>
      <label>司机类型<select v-model="filters.driverType"><option value="">全部类型</option><option value="INTERNAL">自有司机</option><option value="OUTSOURCED">外协司机</option></select></label>
      <label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label>
      <button class="primary" type="button" @click="emit('search', { ...filters })">查询</button>
    </FilterBar>
    <DataTable :columns="[{ key: 'name', label: '姓名' }, { key: 'phone', label: '手机号' }, { key: 'driverType', label: '类型' }, { key: 'idCardNo', label: '身份证号' }, { key: 'drivingLicenseNo', label: '驾驶证号' }, { key: 'birthDate', label: '出生日期' }, { key: 'age', label: '年龄' }, { key: 'currentVehicleId', label: '当前车辆' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }]" :rows="drivers" :loading="loading" empty-text="当前条件下没有司机">
      <template #driverType="{ value }">{{ value === 'INTERNAL' ? '自有司机' : value === 'OUTSOURCED' ? '外协司机' : '—' }}</template>
      <template #currentVehicleId="{ row }">{{ vehicleName(row) }}</template>
      <template #birthDate="{ row }">{{ driverBirthDate(row) || (row.idCardNo ? '身份证待核对' : '未填写') }}</template>
      <template #age="{ row }">{{ fullAge(driverBirthDate(row)) == null ? '待核对' : `${fullAge(driverBirthDate(row))} 岁` }}</template>
      <template #status="{ value }"><StatusTag :value="value === 1 ? '启用' : '停用'" :tone="value === 1 ? 'success' : 'info'" /></template>
      <template #actions="{ row }"><div class="ui-inline-actions"><button type="button" @click="selected = row">查看详情</button><button v-if="can('BUTTON_DRIVERS_EDIT')" type="button" @click="openEdit(row)">编辑</button></div></template>
    </DataTable>
    <PaginationBar :page-no="pageNo" :page-size="20" :total="total" @change="emit('page-change', $event)" />
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
      <dl v-if="selected" class="ui-detail-list"><div><dt>手机号</dt><dd>{{ selected.phone || '—' }}</dd></div><div><dt>司机类型</dt><dd>{{ selected.driverType === 'INTERNAL' ? '自有司机' : '外协司机' }}</dd></div><div><dt>当前车辆</dt><dd>{{ vehicleName(selected) }}</dd></div><div><dt>状态</dt><dd>{{ selected.status === 1 ? '启用' : '停用' }}</dd></div><div><dt>身份证号</dt><dd>{{ selected.idCardNo || '—' }}</dd></div><div><dt>驾驶证号</dt><dd>{{ selected.drivingLicenseNo || '—' }}</dd></div><div><dt>出生日期</dt><dd>{{ driverBirthDate(selected) || '待核对' }}</dd></div><div><dt>年龄</dt><dd>{{ fullAge(driverBirthDate(selected)) == null ? '待核对' : `${fullAge(driverBirthDate(selected))} 岁` }}</dd></div></dl>
    </DetailDrawer>
  </div>
</template>
