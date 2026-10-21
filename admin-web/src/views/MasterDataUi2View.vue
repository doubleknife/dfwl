<script setup>
import { onMounted, reactive, ref } from 'vue';
import PageHeader from '../components/PageHeader.vue';
import DriverManagementSample from '../components/DriverManagementSample.vue';
import BindingManagement from '../components/BindingManagement.vue';
import MasterCatalogUi2 from '../components/MasterCatalogUi2.vue';
import ErrorState from '../components/ErrorState.vue';
import { masterApi } from '../api/modules.js';
import { can } from '../stores/auth.js';

const props = defineProps({ initialTab: { type: String, default: 'drivers' } });
const tabs = [
  { key: 'drivers', label: '司机', permission: 'PAGE_DRIVERS' },
  { key: 'vehicles', label: '车辆', permission: 'PAGE_VEHICLES' },
  { key: 'trailers', label: '车挂', permission: 'PAGE_TRAILERS' },
  { key: 'binding', label: '绑定', permission: 'PAGE_BINDING' },
  { key: 'products', label: '货物', permission: 'PAGE_PRODUCTS' }
];
const tab = ref(props.initialTab);
const drivers = ref([]);
const driverPage = ref(1); const driverPageSize = ref(20);
const driverTotal = ref(0);
const driverFilters = reactive({ keyword: '', driverType: '', status: '' });
const loading = ref(false);
const error = ref('');
async function loadDrivers(nextPage = driverPage.value) {
  if (!can('PAGE_DRIVERS')) return;
  loading.value = true; error.value = '';
  try {
    const result = await masterApi.drivers(nextPage, driverPageSize.value, driverFilters);
    drivers.value = result.records || [];
    driverTotal.value = Number(result.total || 0);
    driverPage.value = nextPage;
  } catch (cause) { error.value = cause.message || '司机数据加载失败'; }
  finally { loading.value = false; }
}
function selectTab(next) { tab.value = next; if (next === 'drivers') loadDrivers(); }
function searchDrivers(filters) { Object.assign(driverFilters, filters); loadDrivers(1); }
onMounted(() => { if (tab.value === 'drivers') loadDrivers(); });
</script>

<template>
  <div class="ui-page-container">
    <PageHeader v-if="!['drivers', 'binding'].includes(tab)" :title="`${tabs.find((item) => item.key === tab)?.label || '基础资料'}管理`" description="按业务对象管理资料与当前绑定。" />
    <ErrorState v-if="error && tab === 'drivers'" :message="error" @retry="loadDrivers" />
    <DriverManagementSample v-if="tab === 'drivers' && can('PAGE_DRIVERS')" :drivers="drivers" :page-no="driverPage" :page-size="driverPageSize" :total="driverTotal" :loading="loading" @refresh="loadDrivers" @page-change="loadDrivers" @size-change="(size) => { driverPageSize = size; loadDrivers(1); }" @search="searchDrivers" />
    <BindingManagement v-if="tab === 'binding'" />
    <MasterCatalogUi2 v-if="['vehicles', 'trailers', 'products'].includes(tab)" :key="tab" :type="tab" />
  </div>
</template>
