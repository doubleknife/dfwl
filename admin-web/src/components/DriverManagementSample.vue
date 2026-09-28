<script setup>
import { computed, reactive, ref } from 'vue';
import { masterApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from './PageHeader.vue';
import FilterBar from './FilterBar.vue';
import DataTable from './DataTable.vue';
import StatusTag from './StatusTag.vue';
import FormDialog from './FormDialog.vue';
import DetailDrawer from './DetailDrawer.vue';
import ErrorState from './ErrorState.vue';

const props = defineProps({ drivers: { type: Array, default: () => [] }, vehicles: { type: Array, default: () => [] }, loading: Boolean });
const emit = defineEmits(['refresh']);
const filters = reactive({ keyword: '', type: '', status: '' });
const form = reactive({ name: '', phone: '', driverType: 'INTERNAL', status: 1, initialPassword: '' });
const openCreate = ref(false);
const selected = ref(null);
const saving = ref(false);
const error = ref('');
const visibleDrivers = computed(() => props.drivers.filter((driver) => {
  const keyword = filters.keyword.trim().toLowerCase();
  return (!keyword || [driver.name, driver.phone].some((value) => String(value || '').toLowerCase().includes(keyword)))
    && (!filters.type || driver.driverType === filters.type)
    && (!filters.status || String(driver.status) === filters.status);
}));
const vehicleName = (id) => props.vehicles.find((vehicle) => vehicle.id === id)?.plateNo || (id ? `车辆 #${id}` : '未绑定');
async function create() {
  saving.value = true;
  error.value = '';
  try {
    await masterApi.createDriver({ ...form });
    Object.assign(form, { name: '', phone: '', driverType: 'INTERNAL', status: 1, initialPassword: '' });
    openCreate.value = false;
    emit('refresh');
  } catch (cause) { error.value = cause.message || '新增司机失败'; }
  finally { saving.value = false; }
}
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="司机管理" description="查看司机、车辆绑定与在职状态。筛选作用于当前加载的前 100 条记录。">
      <template #actions><button type="button" @click="$emit('refresh')">刷新</button><button v-if="can('driver:add')" type="button" class="primary" @click="openCreate = true">＋ 新增司机</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="error = ''" />
    <FilterBar>
      <label class="ui-search">姓名或手机号<input v-model.trim="filters.keyword" placeholder="搜索司机" /></label>
      <label>司机类型<select v-model="filters.type"><option value="">全部类型</option><option value="INTERNAL">自有司机</option><option value="OUTSOURCED">外协司机</option></select></label>
      <label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label>
    </FilterBar>
    <DataTable :columns="[{ key: 'name', label: '姓名' }, { key: 'phone', label: '手机号' }, { key: 'driverType', label: '类型' }, { key: 'currentVehicleId', label: '当前车辆' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }]" :rows="visibleDrivers" :loading="loading" empty-text="当前条件下没有司机">
      <template #driverType="{ value }">{{ value === 'INTERNAL' ? '自有司机' : value === 'OUTSOURCED' ? '外协司机' : '—' }}</template>
      <template #currentVehicleId="{ value }">{{ vehicleName(value) }}</template>
      <template #status="{ value }"><StatusTag :value="value === 1 ? '启用' : '停用'" :tone="value === 1 ? 'success' : 'info'" /></template>
      <template #actions="{ row }"><div class="ui-inline-actions"><button type="button" @click="selected = row">查看详情</button></div></template>
    </DataTable>
    <FormDialog :open="openCreate" title="新增司机" :busy="saving" @close="openCreate = false">
      <form @submit.prevent="create"><div class="ui-form-grid">
        <label class="ui-form-field">姓名<input v-model.trim="form.name" required maxlength="50" placeholder="请输入司机姓名" /></label>
        <label class="ui-form-field">手机号<input v-model.trim="form.phone" required inputmode="tel" placeholder="请输入手机号" /></label>
        <label class="ui-form-field">司机类型<select v-model="form.driverType"><option value="INTERNAL">自有司机</option><option value="OUTSOURCED">外协司机</option></select></label>
        <label class="ui-form-field">初始密码<input v-model="form.initialPassword" type="password" autocomplete="new-password" placeholder="新账号必填；已有合法司机账号可留空" /></label>
      </div><div class="ui-dialog-actions"><button type="button" :disabled="saving" @click="openCreate = false">取消</button><button class="primary" :disabled="saving">{{ saving ? '正在保存…' : '保存司机' }}</button></div></form>
    </FormDialog>
    <DetailDrawer :open="!!selected" :title="selected?.name || '司机详情'" @close="selected = null">
      <dl v-if="selected" class="ui-detail-list"><div><dt>手机号</dt><dd>{{ selected.phone || '—' }}</dd></div><div><dt>司机类型</dt><dd>{{ selected.driverType === 'INTERNAL' ? '自有司机' : '外协司机' }}</dd></div><div><dt>当前车辆</dt><dd>{{ vehicleName(selected.currentVehicleId) }}</dd></div><div><dt>状态</dt><dd>{{ selected.status === 1 ? '启用' : '停用' }}</dd></div><div><dt>驾驶证号</dt><dd>{{ selected.drivingLicenseNo || '未填写' }}</dd></div><div><dt>车辆驾驶证号</dt><dd>{{ selected.vehicleLicenseNo || '未填写' }}</dd></div></dl>
    </DetailDrawer>
  </div>
</template>
