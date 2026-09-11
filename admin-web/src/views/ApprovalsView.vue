<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import { approvalApi } from '../api/modules.js';
import { confirmAndRun, useRequest } from '../composables/useRequest.js';

const { error, run } = useRequest();
const approvals = ref([]);
const detail = ref(null);
const actionForm = reactive({ instanceId: '', comment: '', targetNodeOrder: '' });

async function load() {
  await run(async () => {
    const data = await approvalApi.list();
    approvals.value = data.records || data || [];
  });
}

async function show(row) {
  await run(async () => {
    detail.value = await approvalApi.detail(row.id || row.instanceId);
    actionForm.instanceId = String(row.id || row.instanceId);
  });
}

async function approve() {
  await confirmAndRun('确认通过当前审批任务？', async () => run(async () => {
    await approvalApi.approve(Number(actionForm.instanceId), { comment: actionForm.comment });
    await load();
    detail.value = null;
  }));
}

async function returnApplicant() {
  await confirmAndRun('确认打回申请人？', async () => run(async () => {
    await approvalApi.returnApplicant(Number(actionForm.instanceId), { comment: actionForm.comment });
    await load();
  }));
}

async function returnNode() {
  await confirmAndRun('确认打回指定节点？', async () => run(async () => {
    await approvalApi.returnNode(Number(actionForm.instanceId), {
      comment: actionForm.comment,
      targetNodeOrder: Number(actionForm.targetNodeOrder)
    });
    await load();
  }));
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <PanelSection title="审批列表">
      <DataTable :columns="[
        { key: 'id', label: '实例' }, { key: 'approvalType', label: '类型' }, { key: 'status', label: '状态' },
        { key: 'applicantUserId', label: '发起人' }, { key: 'currentNodeName', label: '当前节点' }, { key: 'actions', label: '操作' }
      ]" :rows="approvals">
        <template #actions="{ row }"><button @click="show(row)">详情</button></template>
      </DataTable>
    </PanelSection>
    <PanelSection title="审批处理与时间线">
      <form class="inline-form">
        <input v-model="actionForm.instanceId" aria-label="审批实例 ID" />
        <input v-model="actionForm.comment" aria-label="备注" />
        <input v-model="actionForm.targetNodeOrder" aria-label="打回节点序号" />
        <button v-permission="'approval:process'" type="button" @click="approve">通过</button>
        <button v-permission="'approval:return'" type="button" @click="returnApplicant">打回申请人</button>
        <button v-permission="'approval:return'" type="button" @click="returnNode">打回节点</button>
      </form>
      <pre v-if="detail" class="json-box">{{ detail }}</pre>
    </PanelSection>
  </div>
</template>
