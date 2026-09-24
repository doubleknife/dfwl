<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import ApprovalCreateForm from '../components/ApprovalCreateForm.vue';
import ApprovalDetailPanel from '../components/ApprovalDetailPanel.vue';
import { approvalApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { detailError as describeError, prepareApprovalDetail, statusName } from './approvalDetailModel.js';

const approvals = ref([]);
const total = ref(0);
const pageNo = ref(1);
const creating = ref(false);
const selectedId = ref(null);
const detail = ref(null);
const listError = ref('');
const detailError = ref('');
const actionError = ref('');
const listLoading = ref(false);
const detailLoading = ref(false);
const actionBusy = ref(false);
const actionForm = reactive({ comment: '', targetNodeOrder: '' });
const canList = computed(() => ['approval:create', 'approval:process', 'approval:return', 'approval:history:view'].some(can));
const scopeOptions = computed(() => [
  { value: 'MINE', label: '我的申请' },
  ...(can('approval:process') || can('approval:return') || can('approval:history:view')
    ? [{ value: 'TODO', label: '待我审批' }, { value: 'DONE', label: '已审批' }] : []),
  ...(can('approval:history:view') ? [{ value: 'ALL', label: '全部历史' }] : [])
]);
const scope = ref(can('approval:history:view') ? 'ALL'
  : can('approval:process') || can('approval:return') ? 'TODO' : 'MINE');

async function load() {
  if (!canList.value || listLoading.value) return;
  listLoading.value = true;
  listError.value = '';
  try {
    const data = await approvalApi.list(scope.value, pageNo.value);
    approvals.value = data.records || [];
    total.value = data.total || 0;
  } catch (caught) {
    approvals.value = [];
    listError.value = describeError(caught, '审批列表');
  } finally { listLoading.value = false; }
}

async function chooseScope(value) {
  if (scope.value === value) return;
  scope.value = value;
  pageNo.value = 1;
  await load();
}

async function changePage(delta) {
  pageNo.value += delta;
  await load();
}

async function refreshDetail() {
  if (!selectedId.value || detailLoading.value) return;
  detailLoading.value = true;
  detailError.value = '';
  try {
    detail.value = prepareApprovalDetail(await approvalApi.detail(selectedId.value));
  } catch (caught) {
    detail.value = null;
    detailError.value = describeError(caught, '审批详情');
  } finally { detailLoading.value = false; }
}

async function show(row) {
  if (detailLoading.value || actionBusy.value) return;
  selectedId.value = row.id || row.instanceId;
  detail.value = null;
  actionError.value = '';
  await refreshDetail();
}

async function afterSubmitted() {
  scope.value = 'MINE';
  pageNo.value = 1;
  await load();
}

async function act(type) {
  if (actionBusy.value || !detail.value || detail.value.approval.status !== 'PENDING') return;
  if (type === 'RETURN_NODE' && !/^[1-9]\d*$/.test(actionForm.targetNodeOrder)) {
    actionError.value = '请选择有效的打回节点序号';
    return;
  }
  const prompts = {
    APPROVE: '确认通过当前审批任务？',
    RETURN_APPLICANT: '确认打回申请人？',
    RETURN_NODE: '确认打回指定节点？'
  };
  if (!window.confirm(prompts[type])) return;
  actionBusy.value = true;
  actionError.value = '';
  try {
    const id = detail.value.approval.id;
    const body = { comment: actionForm.comment };
    if (type === 'RETURN_NODE') body.targetNodeOrder = Number(actionForm.targetNodeOrder);
    if (type === 'APPROVE') await approvalApi.approve(id, body);
    if (type === 'RETURN_APPLICANT') await approvalApi.returnApplicant(id, body);
    if (type === 'RETURN_NODE') await approvalApi.returnNode(id, body);
    actionForm.comment = '';
    actionForm.targetNodeOrder = '';
    await Promise.all([load(), refreshDetail()]);
  } catch (caught) {
    actionError.value = describeError(caught, '审批操作');
  } finally { actionBusy.value = false; }
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <PanelSection title="审批中心">
      <button v-if="can('approval:create')" type="button" class="primary" @click="creating = !creating">{{ creating ? '收起发起表单' : '发起审批' }}</button>
    </PanelSection>
    <PanelSection v-if="creating && can('approval:create')" title="发起审批">
      <ApprovalCreateForm @cancel="creating = false" @submitted="afterSubmitted" />
    </PanelSection>
    <PanelSection v-if="canList" title="审批列表">
      <p v-if="listError" class="error" role="alert">{{ listError }}</p>
      <div class="row-actions">
        <button v-for="option in scopeOptions" :key="option.value" type="button" :class="{ primary: scope === option.value }" :disabled="listLoading" @click="chooseScope(option.value)">{{ option.label }}</button>
      </div>
      <DataTable :columns="[
        { key: 'approvalNo', label: '审批编号' }, { key: 'approvalType', label: '类型' },
        { key: 'status', label: '状态' }, { key: 'applicantUserId', label: '发起人' },
        { key: 'createdAt', label: '创建时间' }, { key: 'actions', label: '操作' }
      ]" :rows="approvals">
        <template #status="{ value }">{{ statusName(value) }}</template>
        <template #actions="{ row }"><button type="button" :disabled="detailLoading || actionBusy" @click="show(row)">详情</button></template>
      </DataTable>
      <div class="row-actions">
        <button type="button" :disabled="pageNo <= 1 || listLoading" @click="changePage(-1)">上一页</button>
        <span>第 {{ pageNo }} 页 · 共 {{ total }} 条</span>
        <button type="button" :disabled="pageNo * 100 >= total || listLoading" @click="changePage(1)">下一页</button>
      </div>
    </PanelSection>
    <PanelSection v-if="selectedId" title="审批详情">
      <div class="row-actions"><button type="button" :disabled="detailLoading || actionBusy" @click="refreshDetail">刷新详情</button><button type="button" :disabled="detailLoading || actionBusy" @click="selectedId = null; detail = null">关闭详情</button></div>
      <p v-if="detailLoading">正在读取审批详情…</p>
      <p v-if="detailError" class="error" role="alert">{{ detailError }}</p>
      <ApprovalDetailPanel v-if="detail" :detail="detail" />
      <div v-if="detail && detail.approval.status === 'PENDING' && (can('approval:process') || can('approval:return'))" class="approval-actions">
        <h3>处理当前审批</h3>
        <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
        <label>备注<input v-model="actionForm.comment" :disabled="actionBusy" /></label>
        <label v-if="can('approval:return')">打回节点序号<input v-model="actionForm.targetNodeOrder" :disabled="actionBusy" inputmode="numeric" /></label>
        <div class="row-actions">
          <button v-if="can('approval:process')" type="button" :disabled="actionBusy" @click="act('APPROVE')">通过</button>
          <button v-if="can('approval:return')" type="button" :disabled="actionBusy" @click="act('RETURN_APPLICANT')">打回申请人</button>
          <button v-if="can('approval:return')" type="button" :disabled="actionBusy" @click="act('RETURN_NODE')">打回节点</button>
        </div>
      </div>
    </PanelSection>
  </div>
</template>

<style scoped>
.approval-actions { display: grid; gap: 10px; margin-top: 24px; }
.approval-actions label { display: grid; gap: 5px; }
</style>
