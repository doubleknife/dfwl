<script setup>
import { computed, ref, watch } from 'vue';
import DataTable from './DataTable.vue';
import DetailDrawer from './DetailDrawer.vue';
import StatusTag from './StatusTag.vue';
import ApprovalEvidence from './ApprovalEvidence.vue';
const props = defineProps({ detail: { type: Object, required: true }, section: { type: String, required: true } });
const detail = computed(() => props.detail);
const selectedTask = ref(null);
watch(() => props.detail, () => { selectedTask.value = null; });
const displayedActions = computed(() => [...(detail.value.actions || [])].reverse());
const expenseItems = computed(() => {
  const items = detail.value.expenseItems || [];
  const latest = Math.max(0, ...items.map((item) => Number(item.submission_version_id)));
  return items.filter((item) => Number(item.submission_version_id) === latest);
});
const snapshot = computed(() => {
  const latest = detail.value.submissions?.at(-1)?.business_snapshot_json;
  try { return typeof latest === 'string' ? JSON.parse(latest) : latest || {}; }
  catch { return {}; }
});
const submissionEvidence = computed(() => (detail.value.attachments || []).filter((file) =>
  Number(file.submission_version_id) === Number(detail.value.submissions?.at(-1)?.id))
  .map((file) => ({ id: file.attachmentId, originalFilename: file.filename, contentType: file.contentType })));
function submissionEvidenceFor(versionId) {
  return (detail.value.attachments || []).filter((file) => Number(file.submission_version_id) === Number(versionId))
    .map((file) => ({ id: file.attachmentId, originalFilename: file.filename, contentType: file.contentType }));
}
function actionEvidence(actionId) {
  return (detail.value.actionAttachments || []).filter((file) => Number(file.actionId) === Number(actionId))
    .map((file) => ({ id: file.attachmentId, originalFilename: file.filename, contentType: file.contentType }));
}
const typeName = { EXPENSE: '费用申请', GENERAL: '普通审批', TIRE_REPLACEMENT: '换胎申请' };
const actionName = { APPROVE: '通过', RETURN_STEP: '打回指定步骤', RETURN_APPLICANT: '打回申请人', RESUBMIT: '修改并重新提交', WITHDRAW: '申请人撤回' };
const tireStatusName = { PENDING_APPROVAL: '审批中', RETURNED_TO_APPLICANT: '已打回', APPROVED: '已申领', WITHDRAWN: '已撤回' };
const taskStatusName = { PUBLISHED: '待发车', TRANSFER_PENDING: '待接手发车', IN_TRANSIT: '运输中', COMPLETED: '已完成', CANCELLED: '已取消' };
const money = (value) => `¥${Number(value || 0).toFixed(2)}`;
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '—';
</script>
<template>
  <template v-if="section === 'summary'">
      <div class="ui-section-card">
        <h2>{{ detail.approval.template_name }} <small>{{ detail.approval.approval_no }}</small></h2>
        <dl class="approval-facts">
          <div><dt>业务类型</dt><dd>{{ typeName[detail.approval.business_type] }}</dd></div>
          <div><dt>当前状态</dt><dd><StatusTag :value="detail.approval.status" /></dd></div>
          <div><dt>冻结流程</dt><dd>第 {{ detail.approval.version_no }} 版</dd></div>
          <div v-if="detail.approval.application_scope"><dt>申请属性</dt><dd>{{ detail.approval.application_scope === 'DAILY' ? '日常费用' : '任务费用' }}</dd></div>
          <div v-if="detail.approval.current_step_name"><dt>当前步骤</dt><dd>{{ detail.approval.current_step_name }}</dd></div>
          <div><dt>发起人</dt><dd>{{ detail.approval.applicant_name ? `${detail.approval.applicant_name} · ${detail.approval.applicant_phone}` : detail.approval.applicant_phone || '—' }}</dd></div>
          <div><dt>发起时间</dt><dd>{{ formatTime(detail.approval.created_at) }}</dd></div>
          <div><dt>发起人当前角色</dt><dd>{{ detail.approval.applicant_role_name || '—' }}</dd></div>
        </dl>
      </div>
      <div class="ui-section-card">
        <h2>本次申请 · 第 {{ detail.submissions.at(-1)?.version_no }} 次提交</h2>
        <DataTable v-if="expenseItems.length" :rows="expenseItems"
          :columns="[{key:'business_date',label:'业务日期'},{key:'driver_name',label:'司机'},
            {key:'vehicle_plate',label:'公司车辆'},
            ...(detail.approval.application_scope === 'TASK' ? [{key:'target_task_id',label:'运输任务'}] : []),
            {key:'trailer_no',label:'车挂号'},
            {key:'description',label:'明细'},
            {key:'amount',label:'金额',numeric:true},{key:'receipt_no',label:'票据号'},
            {key:'invoice_no',label:'发票号'},{key:'station_name',label:'站点'}]">
          <template #amount="{value}">{{ money(value) }}</template>
          <template #driver_name="{value}">{{ value || '—' }}</template>
          <template #vehicle_plate="{value}">{{ value || '—' }}</template>
          <template #target_task_id="{row}"><button v-if="row.target_task_id" type="button" class="ui-detail-button approval-task-link" @click="selectedTask = row">{{ row.task_business_date || row.business_date }} · {{ row.task_template_name || row.task_no || '运输任务' }}</button><span v-else>—</span></template>
        </DataTable>
        <p v-else-if="detail.approval.business_type === 'GENERAL'">{{ snapshot.content?.description || snapshot.content?.reason || '普通审批内容已提交' }}</p>
        <div v-else-if="detail.approval.business_type === 'TIRE_REPLACEMENT'" class="ui-section-card">
          <h3>换胎申请事实</h3>
          <dl v-if="detail.tireRequests?.length" class="approval-tire-fields">
            <div><dt>申请司机</dt><dd>{{ detail.tireRequests[0].driverName }}</dd></div>
            <div><dt>公司车辆</dt><dd>{{ detail.tireRequests[0].vehiclePlate }}</dd></div>
            <div><dt>申请时间</dt><dd>{{ formatTime(detail.tireRequests[0].requestedAt) }}</dd></div>
          </dl>
          <div v-for="(tire,index) in detail.tireRequests || []" :key="tire.ocrRecordId" class="approval-tire-fact">
            <h4>轮胎 {{ index + 1 }}</h4>
            <dl class="approval-tire-fields approval-tire-fields-two">
              <div><dt>确认胎号</dt><dd class="approval-tire-number">{{ tire.tireNo }}</dd></div>
              <div><dt>申领状态</dt><dd><StatusTag :value="tireStatusName[tire.status] || tire.status" /></dd></div>
            </dl>
            <div class="approval-ocr-block"><div class="approval-ocr-label">OCR 识别原文</div>
              <div class="approval-ocr-text">{{ tire.recognizedText || '未识别到文字' }}</div></div>
            <ApprovalEvidence title="申请照片" :attachments="submissionEvidence.filter(file => Number(file.id) === Number(tire.photoAttachmentId))" />
          </div>
          <p class="muted">审批通过后才正式申领库存轮胎；不生成普通费用。</p>
        </div>
        <ApprovalEvidence v-if="submissionEvidence.length && detail.approval.business_type !== 'TIRE_REPLACEMENT'" title="申请附件" :attachments="submissionEvidence" />
        <div v-for="version in (detail.submissions || []).slice(0, -1)" :key="version.id">
          <ApprovalEvidence v-if="submissionEvidenceFor(version.id).length"
            :title="`第 ${version.version_no} 次提交图片凭证`" :attachments="submissionEvidenceFor(version.id)" />
        </div>
      </div>
      <DetailDrawer :open="!!selectedTask" title="运输任务详情" class="approval-task-drawer" @close="selectedTask = null">
        <template #subtitle>{{ selectedTask?.task_no }}</template>
        <dl v-if="selectedTask" class="ui-detail-list">
          <div><dt>业务日期</dt><dd>{{ selectedTask.task_business_date || '—' }}</dd></div>
          <div><dt>线路名称</dt><dd>{{ selectedTask.task_template_name || '—' }}</dd></div>
          <div><dt>客户 / 货物</dt><dd>{{ selectedTask.task_customer_name || '—' }} / {{ selectedTask.task_product_name || '—' }}</dd></div>
          <div><dt>装卸地点</dt><dd>{{ selectedTask.task_loading_place || '—' }} → {{ selectedTask.task_unloading_place || '—' }}</dd></div>
          <div><dt>任务状态</dt><dd><StatusTag :value="taskStatusName[selectedTask.task_status] || '—'" /></dd></div>
          <div><dt>当前司机 / 车辆</dt><dd>{{ selectedTask.task_current_driver || '—' }} / {{ selectedTask.task_current_plate || '—' }}</dd></div>
          <div><dt>发车时间</dt><dd>{{ formatTime(selectedTask.task_departed_at) }}</dd></div>
          <div><dt>有效净重</dt><dd>{{ selectedTask.task_net_weight == null ? '—' : `${selectedTask.task_net_weight} 吨` }}</dd></div>
          <div><dt>完成时间</dt><dd>{{ formatTime(selectedTask.task_completed_at) }}</dd></div>
        </dl>
      </DetailDrawer>
  </template>
  <template v-else-if="section === 'timeline'">
      <div class="ui-section-card">
        <h2>处理时间线</h2>
        <ol class="approval-timeline"><li v-for="entry in displayedActions" :key="entry.id">
          <strong>{{ actionName[entry.action_type] || entry.action_type }}</strong>
          <div class="approval-timeline-fields" :class="{ 'with-target': entry.action_type === 'RETURN_APPLICANT' || entry.action_type === 'RETURN_STEP' }">
            <p>操作时间：{{ formatTime(entry.operated_at) }}</p>
            <p>操作人：{{ entry.operator_role }} · {{ entry.operator_phone }}</p>
            <p>审批节点：{{ entry.node_name }}</p>
            <p v-if="entry.action_type === 'RETURN_APPLICANT'">打回目标：申请人</p>
            <p v-if="entry.action_type === 'RETURN_STEP'">打回目标：{{ entry.target_step_name_snapshot }}</p>
            <p>审批意见：{{ entry.comment || '未填写' }}</p>
          </div>
          <ApprovalEvidence v-if="actionEvidence(entry.id).length" title="处理附件" :attachments="actionEvidence(entry.id)" />
        </li></ol>
        <p v-if="!detail.actions.length" class="ui-empty">尚无处理记录</p>
      </div>
  </template>
</template>
<style scoped>
.approval-facts { display:grid; grid-template-columns:repeat(auto-fit,minmax(170px,1fr)); gap:12px; margin:14px 0 0; }
.approval-facts > div { padding:12px; border:1px solid var(--ui-border); border-radius:8px; background:#f8fbf9; }
.approval-facts dt { color:var(--ui-text-secondary); font-size:12px; }
.approval-facts dd { margin:5px 0 0; font-weight:700; }
.approval-timeline { padding-left: 1.4rem; }
.approval-timeline li { padding: .7rem 0; border-bottom: 1px solid var(--border, #e5ece9); }
.approval-timeline-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px 20px; margin-top: 6px; }
.approval-timeline-fields.with-target { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.approval-timeline-fields p { min-width: 0; margin: 0; overflow-wrap: anywhere; }
@media (max-width: 900px) { .approval-timeline-fields.with-target { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.approval-tire-fact { min-width: 0; margin: 16px 0; padding: 16px; border: 1px solid var(--ui-border); border-radius: 8px; }
.approval-tire-fact h4 { margin: 0 0 12px; }
.approval-tire-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px 20px; margin: 0 0 16px; }
.approval-tire-fields-two { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.approval-tire-fields > div { min-width: 0; }
.approval-tire-fields dt, .approval-ocr-label { color: var(--ui-text-secondary); }
.approval-tire-fields dd { min-width: 0; margin: 5px 0 0; overflow-wrap: anywhere; }
.approval-tire-number { font-size: 1.15rem; color: #087462; }
.approval-ocr-block { min-width: 0; margin-bottom: 16px; }
.approval-ocr-text { margin-top: 5px; white-space: normal; overflow-wrap: anywhere; word-break: break-word; }
.approval-task-drawer :deep(.ui-drawer) { width: min(calc(100vw - 24px), 760px); }
.approval-task-link { white-space: normal; text-align: left; }
@media (max-width: 700px) { .approval-tire-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
