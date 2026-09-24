<script setup>
import ApprovalEvidence from './ApprovalEvidence.vue';
import {
  actionName, approvalKind, displayTime, expenseExtensions, missingTireOcrEvidence,
  snapshotSummary, statusName
} from '../views/approvalDetailModel.js';

defineProps({ detail: { type: Object, required: true } });
</script>

<template>
  <div class="approval-detail">
    <section>
      <h3>审批基本信息</h3>
      <dl class="approval-facts">
        <div><dt>审批编号</dt><dd>{{ detail.approval.approvalNo || `#${detail.approval.id}` }}</dd></div>
        <div><dt>模板</dt><dd>{{ detail.flowConfiguration?.flow?.flowName || detail.approval.approvalType }}（#{{ detail.templateId || '-' }}）</dd></div>
        <div><dt>类型</dt><dd>{{ detail.approval.approvalType }}</dd></div>
        <div><dt>申请人</dt><dd>#{{ detail.approval.applicantUserId }}</dd></div>
        <div><dt>状态</dt><dd>{{ statusName(detail.approval.status) }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ displayTime(detail.approval.createdAt) }}</dd></div>
        <div><dt>当前版本</dt><dd>{{ detail.currentSubmission ? `V${detail.currentSubmission.versionNo}` : '-' }}</dd></div>
        <div><dt>流程版本</dt><dd>V{{ detail.approval.flowVersion }}</dd></div>
      </dl>
    </section>

    <section>
      <h3>当前业务摘要</h3>
      <p>{{ snapshotSummary(detail.approval, detail.currentSubmission) }}</p>
    </section>

    <section>
      <h3>申请版本历史</h3>
      <p v-if="!detail.submissions.length" class="muted">暂无申请版本。</p>
      <article v-for="submission in detail.submissions" :key="submission.id" class="approval-version">
        <h4>Submission V{{ submission.versionNo }}</h4>
        <p>提交人 #{{ submission.submittedBy }} · {{ displayTime(submission.submittedAt) }}</p>
        <p v-if="submission.error" class="error">{{ submission.error }}</p>
        <template v-if="!submission.error && approvalKind(detail.approval) === 'EXPENSE'">
          <p>费用类型：{{ submission.value.expenseType || '-' }} · 归属：{{ submission.value.attributionType === 'ROUTE' ? '线路费用' : submission.value.attributionType === 'DAILY' ? '日常费用' : '-' }}<span v-if="submission.value.attributionType === 'ROUTE'"> · 线路 #{{ submission.value.routeId || '-' }}</span></p>
          <table class="approval-items">
            <thead><tr><th>明细编号</th><th>车辆</th><th>司机</th><th>金额</th><th>业务日期</th></tr></thead>
            <tbody>
              <template v-for="item in submission.value.items || []" :key="item.itemNo">
                <tr><td>{{ item.itemNo }}</td><td>#{{ item.vehicleId }}</td><td>{{ item.driverId ? `#${item.driverId}` : '-' }}</td><td>{{ item.amount }}</td><td>{{ item.businessDate }}</td></tr>
                <tr v-if="item.remark || expenseExtensions(item).length"><td colspan="5">
                  <p v-if="item.remark">备注：{{ item.remark }}</p>
                  <p v-for="extension in expenseExtensions(item)" :key="extension.name">{{ extension.name }}：{{ extension.text }}</p>
                </td></tr>
              </template>
            </tbody>
          </table>
        </template>
        <template v-else-if="!submission.error && approvalKind(detail.approval) === 'TIRE'">
          <dl class="approval-facts">
            <div><dt>换胎申请</dt><dd>#{{ submission.value.requestId || detail.approval.businessId || '-' }}</dd></div>
            <div><dt>司机</dt><dd>#{{ submission.value.driverId || '-' }}</dd></div>
            <div><dt>车辆</dt><dd>#{{ submission.value.vehicleId || '-' }}</dd></div>
          </dl>
          <table class="approval-items">
            <thead><tr><th>轮胎 ID</th><th>确认胎号</th><th>OCR 记录</th></tr></thead>
            <tbody><tr v-for="(item, index) in submission.value.items || []" :key="`${item.tireId}-${index}`">
              <td>#{{ item.tireId }}</td><td>{{ item.confirmedTireNo || '-' }}</td><td>{{ item.ocrRecordId ? `#${item.ocrRecordId}` : '-' }}</td>
            </tr></tbody>
          </table>
          <p v-if="missingTireOcrEvidence(submission)" class="error">本版本 OCR 原图未在正式申请依据中返回。</p>
        </template>
        <template v-else-if="!submission.error">
          <p>业务类型：{{ detail.approval.businessType || detail.approval.approvalType }} · 业务 ID：{{ detail.approval.businessId || '-' }}</p>
          <pre class="approval-snapshot">{{ JSON.stringify(submission.value, null, 2) }}</pre>
        </template>
        <pre v-if="submission.error" class="approval-snapshot">{{ submission.businessSnapshotJson }}</pre>
        <ApprovalEvidence :attachments="submission.attachments" title="申请依据（本版本）" />
      </article>
    </section>

    <section>
      <h3>审批流程与操作历史</h3>
      <p v-if="!detail.tasks.length" class="muted">暂无审批任务。</p>
      <article v-for="task in detail.tasks" :key="task.id" class="approval-task">
        <h4>V{{ task.submissionVersionNo }} · {{ task.nodeName || `节点 ${task.nodeOrder}` }} · {{ statusName(task.status) }}</h4>
        <p>审批人 #{{ task.approverUserId }} · 创建 {{ displayTime(task.createdAt) }} · 完成 {{ displayTime(task.completedAt) }}<span v-if="task.invalidated"> · 已失效</span></p>
        <div v-for="action in task.actions" :key="action.id" class="approval-action">
          <p><strong>{{ actionName(action.actionType) }}</strong> · 操作人 #{{ action.operatorId }} · {{ displayTime(action.operatedAt) }}<span v-if="action.targetNodeOrder"> · 打回节点 {{ action.targetNodeOrder }}</span></p>
          <p v-if="action.comment">备注：{{ action.comment }}</p>
          <ApprovalEvidence :attachments="action.attachments" title="审批动作附件" />
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.approval-detail { display: grid; gap: 24px; }
.approval-detail h3, .approval-detail h4 { margin: 0 0 10px; }
.approval-detail p { margin: 6px 0; }
.approval-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px 20px; margin: 0; }
.approval-facts div { display: grid; gap: 3px; }
.approval-facts dt { color: #667; }
.approval-facts dd { margin: 0; overflow-wrap: anywhere; }
.approval-version, .approval-task { border: 1px solid #d9e1e8; border-radius: 8px; padding: 16px; margin-top: 12px; display: grid; gap: 8px; }
.approval-action { border-left: 3px solid #9ab; padding: 6px 0 6px 14px; margin-top: 8px; }
.approval-items { border-collapse: collapse; width: 100%; overflow-wrap: anywhere; }
.approval-items th, .approval-items td { border: 1px solid #d9e1e8; padding: 8px; text-align: left; }
.approval-items th { background: #f5f7fa; }
.approval-snapshot { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
