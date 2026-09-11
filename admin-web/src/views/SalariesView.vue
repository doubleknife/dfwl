<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import { salaryApi } from '../api/modules.js';
import { useRequest } from '../composables/useRequest.js';

const { error, run } = useRequest();
const rows = ref([]);
const history = ref([]);
const form = reactive({ routeId: '', driverId: '', salaryAmount: '', reason: '' });

async function load() {
  await run(async () => {
    const data = await salaryApi.list();
    rows.value = data.records || data || [];
  });
}

async function create() {
  await run(async () => {
    await salaryApi.create({
      routeId: Number(form.routeId),
      driverId: Number(form.driverId),
      salaryAmount: form.salaryAmount,
      reason: form.reason
    });
    await load();
  });
}

async function showHistory(row) {
  await run(async () => {
    history.value = await salaryApi.history(row.routeId);
  });
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <PanelSection title="工资列表">
      <DataTable :columns="[
        { key: 'routeNo', label: '线路' }, { key: 'driverName', label: '司机' }, { key: 'businessMonth', label: '月份' },
        { key: 'salaryAmount', label: '金额' }, { key: 'sourceType', label: '来源' }, { key: 'actions', label: '操作' }
      ]" :rows="rows">
        <template #actions="{ row }"><button @click="showHistory(row)">历史</button></template>
      </DataTable>
    </PanelSection>
    <div class="two-column">
      <PanelSection title="人工录入 / 调整">
        <form v-permission="'salary:manage'" class="form-grid" @submit.prevent="create">
          <input v-model="form.routeId" aria-label="线路 ID" />
          <input v-model="form.driverId" aria-label="司机 ID" />
          <input v-model="form.salaryAmount" aria-label="工资金额" />
          <input v-model="form.reason" aria-label="原因" />
          <button class="primary">保存工资</button>
        </form>
      </PanelSection>
      <PanelSection title="工资历史">
        <DataTable :columns="[
          { key: 'beforeAmount', label: '修改前' }, { key: 'afterAmount', label: '修改后' },
          { key: 'operatorId', label: '操作人' }, { key: 'operationTime', label: '时间' }, { key: 'reason', label: '原因' }
        ]" :rows="history" />
      </PanelSection>
    </div>
    <PanelSection title="工资导入">
      <p class="muted">工资导入使用导入中心的 SALARY 类型，原始文件通过附件中心上传，随后执行 preview 和 commit。</p>
    </PanelSection>
  </div>
</template>
