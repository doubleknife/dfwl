<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { expenseApi } from '../api/modules.js';
import { confirmAndRun, useRequest } from '../composables/useRequest.js';
import { can } from '../stores/auth.js';

const { loading, error, run } = useRequest();
const rows = ref([]);
const history = ref([]);
const approval = ref(null);
const form = reactive({
  expenseType: 'REPAIR',
  businessDate: new Date().toISOString().slice(0, 10),
  vehicleId: '',
  attributionType: 'DAILY',
  routeId: '',
  amount: '',
  sourceType: 'MANUAL'
});
const attributionForm = reactive({ expenseId: '', attributionType: 'DAILY', routeId: '', reason: '' });
const reversalForm = reactive({ expenseId: '', reason: '' });

async function load() {
  await run(async () => {
    const data = await expenseApi.list();
    rows.value = data.records || data || [];
  });
}

async function create() {
  await run(async () => {
    await expenseApi.create({
      ...form,
      vehicleId: Number(form.vehicleId),
      routeId: form.attributionType === 'ROUTE' && form.routeId ? Number(form.routeId) : null
    });
    await load();
  });
}

async function remove(row) {
  await confirmAndRun('确认删除该费用？审批来源费用会由后端拒绝。', async () => run(async () => {
    await expenseApi.delete(row.id);
    await load();
  }));
}

async function reverse() {
  await confirmAndRun('确认冲销该费用？', async () => run(async () => {
    await expenseApi.reverse(Number(reversalForm.expenseId), { reason: reversalForm.reason });
    await load();
  }));
}

async function adjustAttribution() {
  await run(async () => {
    await expenseApi.attribution(Number(attributionForm.expenseId), {
      attributionType: attributionForm.attributionType,
      routeId: attributionForm.attributionType === 'ROUTE' ? Number(attributionForm.routeId) : null,
      reason: attributionForm.reason
    });
    await load();
  });
}

async function showHistory(row) {
  await run(async () => {
    history.value = await expenseApi.attributionHistory(row.id);
  });
}

async function showApproval(row) {
  await run(async () => {
    approval.value = await expenseApi.approval(row.id);
  });
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <PanelSection title="费用列表">
      <template #actions><button @click="load">刷新</button></template>
      <DataTable :columns="[
        { key: 'expenseNo', label: '单号' }, { key: 'expenseType', label: '类型' }, { key: 'vehicleId', label: '车辆' },
        { key: 'attributionType', label: '归属' }, { key: 'routeId', label: '线路' }, { key: 'amount', label: '金额' },
        { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }
      ]" :rows="rows">
        <template #status="{ value }"><StatusBadge :value="value" :tone="value === 'PENDING_ATTRIBUTION' ? 'danger' : 'neutral'" /></template>
        <template #actions="{ row }">
          <div class="row-actions">
            <button @click="showHistory(row)">归属历史</button>
            <button v-if="row.sourceType === 'APPROVAL'" @click="showApproval(row)">审批链</button>
            <button v-if="can('expense:delete')" @click="remove(row)">删除</button>
          </div>
        </template>
      </DataTable>
    </PanelSection>
    <div class="two-column">
      <PanelSection title="人工新增">
        <form v-permission="'expense:add'" class="form-grid" @submit.prevent="create">
          <select v-model="form.expenseType">
            <option>PENALTY</option><option>CARRYING</option><option>REPAIR</option><option>ELECTRIC</option><option>GAS</option><option>TEMP_ELECTRIC</option><option>WATER</option><option>TOLL</option><option>CAR_WASH</option><option>INFORMATION</option><option>DRIVER_SALARY</option><option>OTHER</option>
          </select>
          <input v-model="form.businessDate" type="date" />
          <input v-model="form.vehicleId" aria-label="车辆 ID" />
          <select v-model="form.attributionType"><option>DAILY</option><option>ROUTE</option></select>
          <input v-model="form.routeId" aria-label="线路 ID" />
          <input v-model="form.amount" aria-label="金额" />
          <button class="primary">保存</button>
        </form>
      </PanelSection>
      <PanelSection title="归属确认 / 冲销">
        <form v-permission="'expense:attribution:edit'" class="inline-form" @submit.prevent="adjustAttribution">
          <input v-model="attributionForm.expenseId" aria-label="费用 ID" />
          <select v-model="attributionForm.attributionType"><option>DAILY</option><option>ROUTE</option></select>
          <input v-model="attributionForm.routeId" aria-label="线路 ID" />
          <input v-model="attributionForm.reason" aria-label="原因" />
          <button>确认归属</button>
        </form>
        <form v-permission="'expense:reversal'" class="inline-form" @submit.prevent="reverse">
          <input v-model="reversalForm.expenseId" aria-label="费用 ID" />
          <input v-model="reversalForm.reason" aria-label="冲销原因" />
          <button>冲销</button>
        </form>
      </PanelSection>
    </div>
    <PanelSection title="历史 / 审批链">
      <DataTable :columns="[
        { key: 'beforeAttributionType', label: '原归属' }, { key: 'beforeRouteId', label: '原线路' },
        { key: 'afterAttributionType', label: '新归属' }, { key: 'afterRouteId', label: '新线路' },
        { key: 'operatorId', label: '操作人' }, { key: 'reason', label: '原因' }
      ]" :rows="history" />
      <pre v-if="approval" class="json-box">{{ approval }}</pre>
    </PanelSection>
  </div>
</template>
