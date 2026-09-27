<script setup>
import { computed, ref, watch } from 'vue';
import { masterApi, routeApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { displayValue } from '../utils/displayLabels.js';
import { actionName, approvalKind, displayTime, expenseExtensions, missingTireOcrEvidence, snapshotSummary, statusName } from '../views/approvalDetailModel.js';
import ApprovalEvidence from './ApprovalEvidence.vue';
import DataTable from './DataTable.vue';
import StatusTag from './StatusTag.vue';

const props = defineProps({ detail: { type: Object, required: true } });
const vehicleNames = ref({});
const driverNames = ref({});
const routeNames = ref({});
const currentTask = computed(() => props.detail.tasks?.find((task) => task.status === 'PENDING' && !task.invalidated));
const kind = computed(() => approvalKind(props.detail.approval));
const nameFor = (names, id, subject) => id == null ? '—' : names[id] || `${subject}资料受权限限制（编号 ${id}）`;
async function loadNames() {
  const vehicles = new Set(); const drivers = new Set(); const routes = new Set();
  for (const submission of props.detail.submissions || []) {
    const snapshot = submission.value || {};
    if (snapshot.vehicleId) vehicles.add(snapshot.vehicleId);
    if (snapshot.driverId) drivers.add(snapshot.driverId);
    if (snapshot.routeId) routes.add(snapshot.routeId);
    for (const item of snapshot.items || []) {
      if (item.vehicleId) vehicles.add(item.vehicleId);
      if (item.driverId) drivers.add(item.driverId);
    }
  }
  const requests = [];
  if (can('vehicle:view')) for (const id of vehicles) requests.push(masterApi.vehicle(id).then((row) => { vehicleNames.value[id] = row.plateNo; }).catch(() => {}));
  if (can('driver:view')) for (const id of drivers) requests.push(masterApi.driver(id).then((row) => { driverNames.value[id] = `${row.name} · ${row.phone}`; }).catch(() => {}));
  if (can('route:list')) for (const id of routes) requests.push(routeApi.detail(id).then((row) => { routeNames.value[id] = row.routeNo; }).catch(() => {}));
  await Promise.all(requests);
}
watch(() => props.detail.approval.id, () => { vehicleNames.value = {}; driverNames.value = {}; routeNames.value = {}; loadNames(); }, { immediate: true });
</script>

<template>
  <div class="ui-approval-detail">
    <section class="ui-section-card"><div class="ui-list-heading"><h2>审批摘要</h2><StatusTag :value="detail.approval.status" /></div><p class="ui-detail-lead">{{ detail.flowConfiguration?.flow?.flowName || detail.approval.flowName || displayValue('approvalType', detail.approval.approvalType) }}</p><dl class="ui-detail-grid"><dt>审批编号</dt><dd>{{ detail.approval.approvalNo }}</dd><dt>申请人</dt><dd>{{ detail.approval.applicantPhone || `账号编号 ${detail.approval.applicantUserId}` }}</dd><dt>申请时间</dt><dd>{{ displayTime(detail.approval.createdAt) }}</dd><dt>业务摘要</dt><dd>{{ snapshotSummary(detail.approval, detail.currentSubmission) }}</dd><dt>流程版本</dt><dd>V{{ detail.approval.flowVersion }}</dd></dl></section>
    <section v-if="currentTask" class="ui-section-card"><h2>当前待处理事项</h2><p>{{ currentTask.nodeName || detail.approval.currentNodeName || `第 ${currentTask.nodeOrder} 节点` }} · {{ statusName(currentTask.status) }}</p><p class="muted">提交审批意见前请核对各版本依据及当前申请事实。</p></section>
    <section class="ui-section-card"><h2>申请版本与依据</h2><p v-if="!detail.submissions?.length" class="ui-empty">暂无申请版本</p><article v-for="submission in detail.submissions" :key="submission.id" class="ui-submission"><div class="ui-list-heading"><h3>申请版本 V{{ submission.versionNo }}</h3><span class="muted">{{ displayTime(submission.submittedAt) }}</span></div><p v-if="submission.error" class="error">{{ submission.error }}；请在高级信息中核对原始快照。</p>
      <template v-else-if="kind === 'EXPENSE'"><p>{{ displayValue('expenseType', submission.value.expenseType) }} · {{ displayValue('attributionType', submission.value.attributionType) }}<span v-if="submission.value.attributionType === 'ROUTE'"> · {{ nameFor(routeNames, submission.value.routeId, '线路') }}</span></p><DataTable :columns="[{key:'itemNo',label:'明细'},{key:'vehicleId',label:'车辆'},{key:'driverId',label:'司机'},{key:'amount',label:'金额',numeric:true},{key:'businessDate',label:'业务日期'}]" :rows="submission.value.items || []" row-key="itemNo" empty-text="无费用明细"><template #vehicleId="{value}">{{ nameFor(vehicleNames,value,'车辆') }}</template><template #driverId="{value}">{{ nameFor(driverNames,value,'司机') }}</template><template #amount="{value}">¥ {{ value }}</template></DataTable><template v-for="item in submission.value.items || []" :key="item.itemNo"><div v-if="item.remark || expenseExtensions(item).length" class="ui-item-note"><strong>{{ item.itemNo }}</strong><p v-if="item.remark">备注：{{ item.remark }}</p><p v-for="extension in expenseExtensions(item)" :key="extension.name">{{ extension.name }}：{{ extension.text }}</p></div></template></template>
      <template v-else-if="kind === 'TIRE'"><p>司机 {{ nameFor(driverNames,submission.value.driverId,'司机') }} · 车辆 {{ nameFor(vehicleNames,submission.value.vehicleId,'车辆') }}</p><DataTable :columns="[{key:'confirmedTireNo',label:'确认胎号'},{key:'ocrRecordId',label:'OCR记录'}]" :rows="submission.value.items || []" row-key="tireId" empty-text="无换胎项"><template #ocrRecordId="{value}">{{ value ? '已有 OCR 记录' : '人工确认' }}</template></DataTable><p v-if="missingTireOcrEvidence(submission)" class="error">该版本缺少正式 OCR 原图依据。</p></template>
      <p v-else>其他申请 · V{{ submission.versionNo }}；无动态业务表单。</p>
      <ApprovalEvidence :attachments="submission.attachments" :title="`V${submission.versionNo} 申请依据`" />
      <details class="ui-advanced"><summary>高级信息：本版本原始快照</summary><pre>{{ submission.businessSnapshotJson }}</pre></details>
    </article></section>
    <section class="ui-section-card"><h2>审批动作时间线</h2><p v-if="!detail.tasks?.length" class="ui-empty">暂无审批任务</p><ol v-else class="ui-timeline"><li v-for="task in detail.tasks" :key="task.id"><strong>V{{ task.submissionVersionNo }} · {{ task.nodeName || `第 ${task.nodeOrder} 节点` }} · {{ statusName(task.status) }}</strong><p>创建 {{ displayTime(task.createdAt) }}<span v-if="task.completedAt"> · 完成 {{ displayTime(task.completedAt) }}</span></p><div v-for="action in task.actions" :key="action.id" class="ui-action"><strong>{{ actionName(action.actionType) }} · {{ displayTime(action.operatedAt) }}</strong><p v-if="action.comment">意见：{{ action.comment }}</p><p v-if="action.targetNodeOrder">打回至第 {{ action.targetNodeOrder }} 节点</p><ApprovalEvidence :attachments="action.attachments" title="审批动作附件" /></div></li></ol></section>
  </div>
</template>
