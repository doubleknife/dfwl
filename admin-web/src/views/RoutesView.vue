<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { routeApi } from '../api/modules.js';
import { confirmAndRun, useRequest } from '../composables/useRequest.js';
import { can } from '../stores/auth.js';

const { loading, error, run } = useRequest();
const rows = ref([]);
const selected = ref(null);
const weights = ref([]);
const form = reactive({
  businessDate: new Date().toISOString().slice(0, 10),
  customerId: '',
  productId: '',
  direction: 'OUTBOUND',
  loadingPlace: '',
  unloadingPlace: '',
  assignedDriverId: '',
  taxUnitPrice: '',
  externalRouteNo: '',
  tripSequence: ''
});
const unloadForm = reactive({ grossWeight: '', tareWeight: '' });
const adjustForm = reactive({ grossWeight: '', tareWeight: '', reason: '', attachmentIds: '' });

async function load() {
  await run(async () => {
    const data = await routeApi.list();
    rows.value = data.records || data || [];
  });
}

function normalizeRoute() {
  return {
    ...form,
    customerId: Number(form.customerId),
    productId: Number(form.productId),
    assignedDriverId: form.assignedDriverId ? Number(form.assignedDriverId) : null,
    externalRouteNo: form.externalRouteNo || null,
    tripSequence: form.tripSequence || null
  };
}

async function save() {
  await run(async () => {
    if (selected.value?.id) await routeApi.update(selected.value.id, normalizeRoute());
    else await routeApi.create(normalizeRoute());
    await load();
  });
}

async function detail(row) {
  await run(async () => {
    selected.value = await routeApi.detail(row.id);
    weights.value = await routeApi.weightVersions(row.id);
  });
}

async function action(row, actionName, message, body = {}) {
  await confirmAndRun(message, async () => run(async () => {
    await routeApi.action(row.id, actionName, body);
    await load();
    if (selected.value?.id === row.id) await detail(row);
  }));
}

async function remove(row) {
  await confirmAndRun('确认删除该线路？', async () => run(async () => {
    await routeApi.delete(row.id);
    await load();
  }));
}

async function unload() {
  await run(async () => {
    await routeApi.unload(selected.value.id, unloadForm);
    await detail(selected.value);
    await load();
  });
}

async function adjustWeight() {
  await run(async () => {
    await routeApi.adjustWeight(selected.value.id, {
      grossWeight: adjustForm.grossWeight,
      tareWeight: adjustForm.tareWeight,
      reason: adjustForm.reason,
      attachmentIds: adjustForm.attachmentIds ? adjustForm.attachmentIds.split(',').map((id) => Number(id.trim())) : []
    });
    await detail(selected.value);
    await load();
  });
}

function rowTone(row) {
  return row.loadStandardMet === false ? 'danger' : 'neutral';
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <PanelSection title="线路列表">
      <template #actions><button :disabled="loading" @click="load">刷新</button></template>
      <DataTable :columns="[
        { key: 'routeNo', label: '线路' }, { key: 'businessDate', label: '日期' }, { key: 'assignedDriverId', label: '司机' },
        { key: 'status', label: '状态' }, { key: 'loadStandardMet', label: '达标' }, { key: 'actions', label: '操作' }
      ]" :rows="rows">
        <template #status="{ value }"><StatusBadge :value="value" /></template>
        <template #loadStandardMet="{ row, value }"><StatusBadge :tone="rowTone(row)" :value="value === false ? '不达标' : '正常'" /></template>
        <template #actions="{ row }">
          <div class="row-actions">
            <button @click="detail(row)">详情</button>
            <button v-if="row.status === 'UNPUBLISHED' && can('route:publish')" @click="action(row, 'publish', '确认发布线路？')">发布</button>
            <button v-if="row.status === 'PUBLISHED' && can('route:depart')" @click="action(row, 'depart', '确认发车？')">发车</button>
            <button v-if="row.status === 'PUBLISHED' && can('route:cancel')" @click="action(row, 'cancel', '确认取消线路？', { reason: '管理端取消' })">取消</button>
            <button v-if="row.status === 'IN_TRANSIT' && can('route:void')" @click="action(row, 'void', '确认作废运输中线路？', { reason: '管理端作废' })">作废</button>
            <button v-if="row.status === 'VOIDED' && can('route:reactivate')" @click="action(row, 'reactivate', '确认重新启用？')">重新启用</button>
            <button v-if="can('route:delete')" @click="remove(row)">删除</button>
          </div>
        </template>
      </DataTable>
    </PanelSection>
    <div class="two-column">
      <PanelSection title="创建 / 编辑">
        <form class="form-grid" @submit.prevent="save">
          <input v-model="form.businessDate" type="date" />
          <input v-model="form.customerId" required aria-label="客户 ID" />
          <input v-model="form.productId" required aria-label="产品 ID" />
          <select v-model="form.direction"><option>OUTBOUND</option><option>RETURN</option></select>
          <input v-model="form.loadingPlace" required aria-label="装货地" />
          <input v-model="form.unloadingPlace" required aria-label="卸货地" />
          <input v-model="form.taxUnitPrice" required aria-label="含税单价" />
          <input v-model="form.assignedDriverId" aria-label="司机 ID" />
          <button v-permission="selected?.id ? 'route:edit' : 'route:create'" class="primary">保存</button>
        </form>
      </PanelSection>
      <PanelSection title="详情 / 重量历史">
        <pre v-if="selected" class="json-box">{{ selected }}</pre>
        <form v-if="selected && selected.status === 'IN_TRANSIT'" v-permission="'route:unload'" class="inline-form" @submit.prevent="unload">
          <input v-model="unloadForm.grossWeight" aria-label="毛重" />
          <input v-model="unloadForm.tareWeight" aria-label="皮重" />
          <button class="primary">卸货</button>
        </form>
        <form v-if="selected" v-permission="'route:weight:adjust'" class="inline-form" @submit.prevent="adjustWeight">
          <input v-model="adjustForm.grossWeight" aria-label="纠错毛重" />
          <input v-model="adjustForm.tareWeight" aria-label="纠错皮重" />
          <input v-model="adjustForm.reason" aria-label="原因" />
          <input v-model="adjustForm.attachmentIds" aria-label="附件 ID，逗号分隔" />
          <button>财务纠错</button>
        </form>
        <DataTable :columns="[
          { key: 'versionNo', label: '版本' }, { key: 'grossWeight', label: '毛重' }, { key: 'tareWeight', label: '皮重' },
          { key: 'netWeight', label: '实际载重' }, { key: 'loadStandardMet', label: '达标' }, { key: 'reason', label: '原因' }
        ]" :rows="weights" />
      </PanelSection>
    </div>
  </div>
</template>
