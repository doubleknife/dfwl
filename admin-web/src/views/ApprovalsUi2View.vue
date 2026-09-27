<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { approvalApi } from '../api/modules.js';
import { authStore, can } from '../stores/auth.js';
import { displayValue } from '../utils/displayLabels.js';
import { detailError as describeError, prepareApprovalDetail, statusName } from './approvalDetailModel.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import StatusTag from '../components/StatusTag.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ErrorState from '../components/ErrorState.vue';
import ApprovalCreateForm from '../components/ApprovalCreateForm.vue';
import ApprovalDetailUi2 from '../components/ApprovalDetailUi2.vue';

const mode = ref('list');
const scopes = [
  { value: 'MINE', label: '我的申请' },
  { value: 'TODO', label: '待我审批' },
  { value: 'DONE', label: '已审批' },
  { value: 'ALL', label: '全部历史' }
];
const availableScopes = computed(() => scopes.filter((item) => item.value === 'MINE' || (item.value === 'ALL' ? can('approval:history:view') : can('approval:process') || can('approval:return') || can('approval:history:view'))));
const scope = ref(can('approval:process') || can('approval:return') ? 'TODO' : 'MINE');
const filters = reactive({ approvalType: '', status: '' });
const rows = ref([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const detail = ref(null);
const detailLoading = ref(false);
const busy = ref(false);
const action = ref('');
const form = reactive({ comment: '', targetNodeOrder: '' });
const pendingTask = computed(() => detail.value?.tasks?.find((task) => task.status === 'PENDING' && !task.invalidated));
const mayProcess = computed(() => detail.value?.approval?.status === 'PENDING' && pendingTask.value?.approverUserId === authStore.user?.id);

async function load(next = 1) {
  loading.value = true; error.value = '';
  try { const result = await approvalApi.list(scope.value, next, 20, filters); rows.value = result.records || []; total.value = Number(result.total || 0); page.value = next; }
  catch (cause) { error.value = describeError(cause, '审批列表'); rows.value = []; }
  finally { loading.value = false; }
}
function changeScope(value) { scope.value = value; load(1); }
async function openDetail(row) {
  mode.value = 'detail'; detail.value = null; detailLoading.value = true; error.value = '';
  try { detail.value = prepareApprovalDetail(await approvalApi.detail(row.id || row.instanceId)); }
  catch (cause) { error.value = describeError(cause, '审批详情'); }
  finally { detailLoading.value = false; }
}
async function refreshDetail() { if (detail.value) await openDetail(detail.value.approval); }
async function afterSubmitted() { mode.value = 'list'; scope.value = 'MINE'; await load(1); }
async function act() {
  if (!detail.value || !mayProcess.value) return;
  if (action.value === 'RETURN_NODE' && !/^[1-9]\d*$/.test(form.targetNodeOrder)) { error.value = '请输入有效的打回节点序号'; return; }
  busy.value = true; error.value = '';
  try {
    const id = detail.value.approval.id;
    const body = { comment: form.comment };
    if (action.value === 'APPROVE') await approvalApi.approve(id, body);
    if (action.value === 'RETURN_APPLICANT') await approvalApi.returnApplicant(id, body);
    if (action.value === 'RETURN_NODE') await approvalApi.returnNode(id, { ...body, targetNodeOrder: Number(form.targetNodeOrder) });
    action.value = ''; form.comment = ''; form.targetNodeOrder = '';
    await refreshDetail(); await load(page.value);
  } catch (cause) { error.value = describeError(cause, '审批操作'); }
  finally { busy.value = false; }
}
onMounted(() => load());
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="审批中心" :description="mode === 'list' ? '按申请和待办快速定位审批。' : mode === 'create' ? '选择已发布且有权发起的模板。' : '核对版本依据后处理当前节点。'" />
    <ErrorState v-if="error" :message="error" @retry="mode === 'list' ? load(page) : mode === 'detail' ? refreshDetail() : error = ''" />
    <template v-if="mode === 'list'"><div class="ui-list-heading"><h2>审批列表</h2><button v-if="can('approval:create')" class="primary" type="button" @click="mode='create'">＋ 发起审批</button></div><div class="ui-subtabs"><button v-for="item in availableScopes" :key="item.value" type="button" :class="{active:scope===item.value}" @click="changeScope(item.value)">{{ item.label }}</button></div><FilterBar><label>业务类型<select v-model="filters.approvalType" @change="load(1)"><option value="">全部类型</option><option value="EXPENSE">费用</option><option value="TIRE_REQUEST">换胎</option><option value="OTHER">其他</option></select></label><label>状态<select v-model="filters.status" @change="load(1)"><option value="">全部状态</option><option value="PENDING">待处理</option><option value="APPROVED">已通过</option><option value="RETURNED_TO_APPLICANT">已打回申请人</option></select></label><button type="button" @click="load(page)">刷新</button></FilterBar><DataTable v-if="!error" :columns="[{key:'approvalNo',label:'审批编号'},{key:'flowName',label:'模板/业务'},{key:'status',label:'状态'},{key:'applicantPhone',label:'申请人'},{key:'currentNodeName',label:'当前节点'},{key:'createdAt',label:'发起时间'},{key:'actions',label:'操作'}]" :rows="rows" :loading="loading" empty-text="没有符合条件的审批"><template #flowName="{row}">{{ row.flowName || displayValue('approvalType',row.approvalType) }}</template><template #status="{value}"><StatusTag :value="value" /></template><template #applicantPhone="{row}">{{ row.applicantPhone || `账号编号 ${row.applicantUserId}` }}</template><template #currentNodeName="{row}">{{ row.currentNodeName || (row.status === 'PENDING' ? '待分配节点' : '—') }}</template><template #actions="{row}"><button type="button" @click="openDetail(row)">查看详情</button></template></DataTable><PaginationBar v-if="!error" :page-no="page" :page-size="20" :total="total" @change="load" /></template>
    <section v-else-if="mode === 'create'" class="ui-workflow"><button type="button" @click="mode='list'">← 返回审批列表</button><div class="ui-section-card"><h2>发起审批</h2><ApprovalCreateForm @cancel="mode='list'" @submitted="afterSubmitted" /></div></section>
    <section v-else class="ui-workflow"><div class="ui-list-heading"><button type="button" @click="mode='list'; detail=null">← 返回审批列表</button><button v-if="detail" type="button" @click="refreshDetail">刷新详情</button></div><p v-if="detailLoading" class="ui-loading">正在读取审批详情…</p><ApprovalDetailUi2 v-if="detail" :detail="detail" /><div v-if="detail && mayProcess && (can('approval:process') || can('approval:return'))" class="ui-section-card"><h2>处理当前节点</h2><label class="ui-form-field">处理意见<input v-model.trim="form.comment" placeholder="可填写处理说明" /></label><label v-if="can('approval:return')" class="ui-form-field">打回目标节点序号<input v-model.trim="form.targetNodeOrder" type="number" min="1" placeholder="仅打回节点时填写" /></label><div class="ui-dialog-actions"><button v-if="can('approval:return')" type="button" @click="action='RETURN_APPLICANT'">打回申请人</button><button v-if="can('approval:return')" type="button" @click="action='RETURN_NODE'">打回指定节点</button><button v-if="can('approval:process')" class="primary" type="button" @click="action='APPROVE'">通过当前节点</button></div></div></section>
    <ConfirmDialog :open="!!action" :title="action === 'APPROVE' ? '通过审批' : '打回审批'" :message="`${action === 'APPROVE' ? '确认通过' : action === 'RETURN_APPLICANT' ? '确认打回申请人' : '确认打回指定节点'} ${detail?.approval?.approvalNo || ''}？该操作会记录在审批时间线中。`" :busy="busy" :danger="action !== 'APPROVE'" @cancel="action=''" @confirm="act" />
  </div>
</template>
