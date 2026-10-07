<script setup>
import { onMounted, ref } from 'vue';
import PageHeader from '../components/PageHeader.vue';
import DriverManagementSample from '../components/DriverManagementSample.vue';
import BindingManagement from '../components/BindingManagement.vue';
import MasterCatalogUi2 from '../components/MasterCatalogUi2.vue';
import ErrorState from '../components/ErrorState.vue';
import { masterApi } from '../api/modules.js';
import { can } from '../stores/auth.js';

const props = defineProps({ initialTab: { type: String, default: 'drivers' } });
const tabs = [
  { key: 'drivers', label: '司机', permission: 'driver:list' },
  { key: 'vehicles', label: '车辆', permission: 'vehicle:list' },
  { key: 'trailers', label: '车挂', permission: 'trailer:list' },
  { key: 'binding', label: '绑定', permission: 'vehicle:bindingHistory' },
  { key: 'products', label: '货物', permission: 'template:view' }
];
const tab = ref(props.initialTab);
const drivers = ref([]);
const vehicles = ref([]);
const loading = ref(false);
const error = ref('');
async function loadDrivers() {
  if (!can('driver:list')) return;
  loading.value = true; error.value = '';
  try {
    const [driverPage, vehiclePage] = await Promise.all([
      masterApi.drivers(1, 100),
      can('vehicle:list') ? masterApi.vehicles(1, 100) : Promise.resolve({ records: [] })
    ]);
    drivers.value = driverPage.records || [];
    vehicles.value = vehiclePage.records || [];
  } catch (cause) { error.value = cause.message || '司机数据加载失败'; }
  finally { loading.value = false; }
}
function selectTab(next) { tab.value = next; if (next === 'drivers') loadDrivers(); }
onMounted(() => { if (tab.value === 'drivers') loadDrivers(); });
</script>

<template>
  <div class="ui-page-container">
    <PageHeader :title="tabs.find((item) => item.key === tab)?.label || '基础资料'" description="按业务对象管理资料与当前绑定。" />
    <ErrorState v-if="error && tab === 'drivers'" :message="error" @retry="loadDrivers" />
    <DriverManagementSample v-if="tab === 'drivers' && can('driver:list')" :drivers="drivers" :vehicles="vehicles" :loading="loading" @refresh="loadDrivers" />
    <BindingManagement v-if="tab === 'binding'" />
    <MasterCatalogUi2 v-if="['vehicles', 'trailers', 'products'].includes(tab)" :key="tab" :type="tab" />
  </div>
</template>
