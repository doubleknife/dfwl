<script setup>
import { ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import { settlementApi } from '../api/modules.js';
import { confirmAndRun, useRequest } from '../composables/useRequest.js';

const { error, run } = useRequest();
const month = ref(new Date().toISOString().slice(0, 7));
const versions = ref([]);
const details = ref([]);
const diffs = ref([]);

async function load() {
  await run(async () => {
    versions.value = await settlementApi.versions(month.value);
  });
}

async function generate() {
  await confirmAndRun('确认生成新的月结版本？历史版本不会被修改。', async () => run(async () => {
    await settlementApi.generate(month.value);
    await load();
  }));
}

async function showDetails(row) {
  await run(async () => {
    details.value = await settlementApi.details(row.id);
    diffs.value = await settlementApi.diff(row.id);
  });
}
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <PanelSection title="月份与版本">
      <div class="toolbar">
        <input v-model="month" type="month" />
        <button @click="load">查询</button>
        <button v-permission="'settlement:generate'" class="primary" @click="generate">生成新版本</button>
      </div>
      <DataTable :columns="[
        { key: 'versionNo', label: '版本' }, { key: 'totalIncome', label: '收入' }, { key: 'routeExpense', label: '线路费用' },
        { key: 'dailyExpense', label: '日常费用' }, { key: 'driverSalary', label: '工资' }, { key: 'totalProfit', label: '利润' },
        { key: 'generatedBy', label: '生成人' }, { key: 'actions', label: '操作' }
      ]" :rows="versions">
        <template #actions="{ row }"><button @click="showDetails(row)">详情 / 差异</button></template>
      </DataTable>
    </PanelSection>
    <PanelSection title="版本详情">
      <DataTable :columns="[
        { key: 'factType', label: '事实类型' }, { key: 'sourceId', label: '业务 ID' }, { key: 'incomeAmount', label: '收入' },
        { key: 'expenseAmount', label: '支出' }, { key: 'profitAmount', label: '利润' }
      ]" :rows="details" />
    </PanelSection>
    <PanelSection title="版本差异">
      <DataTable :columns="[
        { key: 'changeType', label: '类型' }, { key: 'factType', label: '对象' }, { key: 'sourceId', label: '业务 ID' },
        { key: 'incomeDelta', label: '收入影响' }, { key: 'expenseDelta', label: '支出影响' }, { key: 'profitDelta', label: '利润影响' },
        { key: 'businessOperatorId', label: '业务操作人' }, { key: 'businessReason', label: '原因' }
      ]" :rows="diffs" />
    </PanelSection>
  </div>
</template>
