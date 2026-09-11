<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import { reportApi } from '../api/modules.js';
import { useRequest } from '../composables/useRequest.js';
import { can } from '../stores/auth.js';

const { error, run } = useRequest();
const tab = ref('profit');
const data = reactive({ profit: [], vehicle: [], driver: [], attendance: [], energy: [] });

const configs = {
  profit: { title: '利润报表', permission: 'report:profit', loader: reportApi.profit, columns: ['routeNo', 'customerName', 'productName', 'driverName', 'plateNo', 'actualWeight', 'incomeAmount', 'routeExpense', 'driverSalary', 'profitAmount'] },
  vehicle: { title: '车辆支出', permission: 'report:vehicle', loader: reportApi.vehicle, columns: ['plateNo', 'penaltyAmount', 'carWashAmount', 'waterAmount', 'repairAmount', 'electricAmount', 'routeCount'] },
  driver: { title: '司机支出', permission: 'report:driver', loader: reportApi.driver, columns: ['driverName', 'routeCount', 'mileageKm', 'penaltyAmount', 'carWashAmount', 'waterAmount', 'repairAmount', 'driverSalary'] },
  attendance: { title: '出勤', permission: 'report:attendance', loader: reportApi.attendance, columns: ['businessMonth', 'driverName', 'businessDate', 'departed'] },
  energy: { title: '电费 / 气费', permission: 'report:energy', loader: reportApi.energy, columns: ['plateNo', 'energyType', 'quantity', 'amount'] }
};

function columns(keys) {
  return keys.map((key) => ({ key, label: key }));
}

async function load(name = tab.value) {
  const config = configs[name];
  if (!config || !can(config.permission)) return;
  await run(async () => {
    data[name] = await config.loader();
  });
}

async function switchTab(name) {
  tab.value = name;
  await load(name);
}

onMounted(() => load());
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="tabs">
      <button v-for="(config, key) in configs" v-show="can(config.permission)" :key="key" :class="{ active: tab === key }" @click="switchTab(key)">
        {{ config.title }}
      </button>
    </div>
    <PanelSection :title="configs[tab].title">
      <DataTable :columns="columns(configs[tab].columns)" :rows="data[tab]" />
    </PanelSection>
  </div>
</template>
