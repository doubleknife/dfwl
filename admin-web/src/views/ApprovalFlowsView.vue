<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { approvalApi } from '../api/modules.js';
import { confirmAndRun, useRequest } from '../composables/useRequest.js';

const { loading, error, run } = useRequest();
const filters = reactive({ approvalType: '', status: '' });
const state = reactive({ flows: [], versions: [], detail: null, active: null });
const selected = ref(null);
const flowForm = reactive({ flowName: '', nodesJson: '[]' });
const statusTone = { DRAFT: 'info', ACTIVE: 'neutral', MAINTENANCE: 'warning' };

const selectedType = computed(() => selected.value?.approvalType || filters.approvalType || '');

async function load() {
  await run(async () => {
    const page = await approvalApi.flowList(filters);
    state.flows = page.records || page || [];
    if (selected.value && !state.flows.some((item) => item.id === selected.value.id)) {
      selected.value = null;
      state.detail = null;
    }
  });
}

function fillForm(detail) {
  flowForm.flowName = detail.flow.flowName;
  flowForm.nodesJson = JSON.stringify(detail.nodes.map((node) => ({
    nodeOrder: node.nodeOrder,
    nodeName: node.nodeName,
    positionId: node.positionId,
    approverUserId: node.approverUserId,
    allowReturn: node.allowReturn
  })), null, 2);
}

async function selectFlow(row) {
  selected.value = row;
  await run(async () => {
    const [detail, versions, active] = await Promise.all([
      approvalApi.flowDetail(row.id),
      approvalApi.flowVersions(row.approvalType),
      approvalApi.activeFlow(row.approvalType).catch(() => null)
    ]);
    state.detail = detail;
    state.versions = versions || [];
    state.active = active;
    fillForm(detail);
  });
}

async function viewActive() {
  if (!selectedType.value) return;
  await run(async () => {
    state.active = await approvalApi.activeFlow(selectedType.value);
    selected.value = state.active.flow;
    state.detail = state.active;
    await loadVersions(selectedType.value);
    fillForm(state.active);
  });
}

async function loadVersions(approvalType = selectedType.value) {
  if (!approvalType) return;
  await run(async () => {
    state.versions = await approvalApi.flowVersions(approvalType);
  });
}

async function maintenance() {
  if (!selected.value) return;
  await confirmAndRun('进入维护后将禁止新申请发起，确认继续？', async () => run(async () => {
    await approvalApi.maintenance(selected.value.id);
    await load();
    await selectFlow(selected.value);
  }));
}

async function updateFlow() {
  if (!selected.value) return;
  await run(async () => {
    const body = { flowName: flowForm.flowName, nodes: JSON.parse(flowForm.nodesJson) };
    const updated = await approvalApi.updateFlow(selected.value.id, body);
    selected.value = updated;
    await load();
    await selectFlow(updated);
  });
}

async function publish() {
  if (!selected.value) return;
  await confirmAndRun('确认重新发布该流程版本？', async () => run(async () => {
    const published = await approvalApi.publishFlow(selected.value.id);
    selected.value = published;
    await load();
    await selectFlow(published);
  }));
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <PanelSection title="审批流程列表">
      <template #actions>
        <div class="toolbar">
          <input v-model.trim="filters.approvalType" aria-label="审批类型" placeholder="审批类型" />
          <select v-model="filters.status" aria-label="流程状态">
            <option value="">全部状态</option>
            <option>DRAFT</option>
            <option>ACTIVE</option>
            <option>MAINTENANCE</option>
          </select>
          <button class="primary" :disabled="loading" @click="load">查询</button>
          <button :disabled="!selectedType || loading" @click="loadVersions()">查看版本</button>
          <button :disabled="!selectedType || loading" @click="viewActive">查看 ACTIVE</button>
        </div>
      </template>
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'approvalType', label: '类型' }, { key: 'flowName', label: '名称' },
        { key: 'versionNo', label: '版本' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }
      ]" :rows="state.flows">
        <template #status="{ value }"><StatusBadge :value="value" :tone="statusTone[value] || 'neutral'" /></template>
        <template #actions="{ row }"><button type="button" @click="selectFlow(row)">查看</button></template>
      </DataTable>
    </PanelSection>

    <div class="two-column">
      <PanelSection title="版本与当前 ACTIVE">
        <div v-if="state.active" class="summary-list">
          <span>当前 ACTIVE</span>
          <strong>{{ state.active.flow.flowName }} V{{ state.active.flow.versionNo }}</strong>
        </div>
        <DataTable :columns="[
          { key: 'id', label: 'ID' }, { key: 'flowName', label: '名称' },
          { key: 'versionNo', label: '版本' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }
        ]" :rows="state.versions">
          <template #status="{ value }"><StatusBadge :value="value" :tone="statusTone[value] || 'neutral'" /></template>
          <template #actions="{ row }"><button type="button" @click="selectFlow(row)">查看</button></template>
        </DataTable>
      </PanelSection>

      <PanelSection title="流程节点">
        <DataTable :columns="[
          { key: 'nodeOrder', label: '顺序' }, { key: 'nodeName', label: '节点' },
          { key: 'approverUserId', label: '审批人' }, { key: 'allowReturn', label: '允许打回' }
        ]" :rows="state.detail?.nodes || []">
          <template #allowReturn="{ value }">{{ value ? '是' : '否' }}</template>
        </DataTable>
      </PanelSection>
    </div>

    <PanelSection v-if="selected" title="流程维护">
      <form class="form-grid" @submit.prevent="updateFlow">
        <input v-model.trim="flowForm.flowName" required aria-label="流程名称" />
        <textarea v-model="flowForm.nodesJson" rows="10" required aria-label="流程节点 JSON"></textarea>
        <div class="row-actions">
          <button type="button" @click="maintenance">进入维护</button>
          <button class="primary" :disabled="loading">保存修改</button>
          <button type="button" @click="publish">重新发布</button>
        </div>
      </form>
    </PanelSection>
  </div>
</template>
