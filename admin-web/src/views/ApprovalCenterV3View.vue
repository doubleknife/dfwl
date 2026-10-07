<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { v3ApprovalApi } from '../api/modules.js';
import { authStore, can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import StatusTag from '../components/StatusTag.vue';
import ApprovalEvidence from '../components/ApprovalEvidence.vue';

const props = defineProps({ initialScope: { type: String, default: '' }, initialTab: { type: String, default: 'list' } });
const scope = ref(props.initialScope || (can('approval:process') ? 'TODO' : 'MINE'));
const tab = ref(props.initialTab);
const rows = ref([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const detail = ref(null);
const templates = ref([]);
const options = ref({ roles: [], approvers: [] });
const flowDetail = ref(null);
const form = reactive({ stableCode: '', name: '', businessType: 'GENERAL', expenseType: '',
  initiatorRoleIds: [], nodes: [{ name: '', approverUserId: '', allowReturn: true }] });
const action = reactive({ comment: '', targetFlowNodeId: '' });
const generalTemplates = ref([]);
const generalTemplateId = ref('');
const generalDescription = ref('');
const resubmitRows = ref([]);
const resubmitOptions = ref({ drivers: [], vehicles: [] });
const taskChoices = ref({});
const resubmitDescription = ref('');
const currentTask = computed(() => detail.value?.tasks?.find((task) => task.status === 'PENDING' && !task.invalidated));
const canProcess = computed(() => detail.value?.approval?.status === 'PENDING'
  && Number(currentTask.value?.approver_user_id) === Number(authStore.user?.id));
const returnable = computed(() => detail.value?.returnableSteps || []);
const expenseItems = computed(() => {
  const items = detail.value?.expenseItems || [];
  const latest = Math.max(0, ...items.map((item) => Number(item.submission_version_id)));
  return items.filter((item) => Number(item.submission_version_id) === latest);
});
const snapshot = computed(() => {
  const latest = detail.value?.submissions?.at(-1)?.business_snapshot_json;
  try { return typeof latest === 'string' ? JSON.parse(latest) : latest || {}; }
  catch { return {}; }
});
const isOwnReturned = computed(() => detail.value?.approval?.status === 'RETURNED_TO_APPLICANT'
  && Number(detail.value?.approval?.applicant_user_id) === Number(authStore.user?.id));
const submissionEvidence = computed(() => (detail.value?.attachments || []).filter((file) =>
  Number(file.submission_version_id) === Number(detail.value?.submissions?.at(-1)?.id))
  .map((file) => ({ id: file.attachmentId, originalFilename: file.filename, contentType: file.contentType })));
const typeName = { EXPENSE: '费用申请', GENERAL: '普通审批', TIRE_REPLACEMENT: '换胎申请' };
const expenseTypeName = { REPAIR: '维修费', PENALTY: '罚款', WATER: '水费', TEMP_ELECTRIC: '临时电费',
  ELECTRIC: '电费', GAS: '气费', TOLL: '过路费', CARRYING: '运费',
  CAR_WASH: '洗车费', OTHER: '其他费用' };
const statusName = { PENDING: '待处理', APPROVED: '已通过', RETURNED_TO_APPLICANT: '已打回申请人' };
const actionName = { APPROVE: '通过', RETURN_STEP: '打回指定步骤', RETURN_APPLICANT: '打回申请人' };
const money = (value) => `¥${Number(value || 0).toFixed(2)}`;
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '—';
const fail = (cause) => { error.value = cause?.message || '请求失败，请重试'; };

async function load(next = 1) {
  loading.value = true; error.value = '';
  try {
    const response = await v3ApprovalApi.list(scope.value, next);
    rows.value = response.records || []; total.value = Number(response.total || 0); page.value = next;
  } catch (cause) { fail(cause); rows.value = []; }
  finally { loading.value = false; }
}
async function openDetail(row) {
  loading.value = true; error.value = '';
  try {
    detail.value = await v3ApprovalApi.detail(row.id);
    const latest = Math.max(0, ...(detail.value.expenseItems || []).map((entry) => Number(entry.submission_version_id)));
    resubmitRows.value = (detail.value.expenseItems || []).filter((entry) => Number(entry.submission_version_id) === latest)
      .map((entry) => ({ businessDate: String(entry.business_date).slice(0, 10), driverId: entry.driver_id,
        vehicleId: entry.vehicle_id, targetTaskId: entry.target_task_id, amount: entry.amount,
        description: entry.description || '', receiptNo: entry.receipt_no || '', invoiceNo: entry.invoice_no || '',
        remark: entry.remark || '', stationName: entry.station_name || '' }));
    try { resubmitDescription.value = JSON.parse(detail.value.submissions.at(-1).business_snapshot_json)?.content?.description || ''; }
    catch { resubmitDescription.value = ''; }
    if (isOwnReturned.value && detail.value.approval.business_type === 'EXPENSE') {
      resubmitOptions.value = await v3ApprovalApi.expenseOptions();
      for (const entry of resubmitRows.value) if (entry.driverId) await loadTaskChoices(entry.driverId);
    }
    tab.value = 'detail';
  }
  catch (cause) { fail(cause); }
  finally { loading.value = false; }
}
async function loadTaskChoices(driverId) {
  if (taskChoices.value[driverId]) return;
  taskChoices.value = { ...taskChoices.value, [driverId]: await v3ApprovalApi.taskCandidates(driverId) };
}
async function submitAgain() {
  if (!isOwnReturned.value || busy.value) return;
  busy.value = true; error.value = '';
  try {
    const general = detail.value.approval.business_type === 'GENERAL';
    await v3ApprovalApi.resubmit(detail.value.approval.id, {
      items: general ? null : resubmitRows.value.map((row) => ({ ...row, driverId: Number(row.driverId),
        vehicleId: Number(row.vehicleId), targetTaskId: row.targetTaskId ? Number(row.targetTaskId) : null })),
      generalSnapshot: general ? { description: resubmitDescription.value.trim() } : null,
      attachmentIds: []
    });
    await refreshDetail(); await load(page.value);
  } catch (cause) { fail(cause); }
  finally { busy.value = false; }
}
async function startGeneral() {
  error.value = '';
  try { generalTemplates.value = (await v3ApprovalApi.available()).filter((entry) => entry.business_type === 'GENERAL'); tab.value = 'general'; }
  catch (cause) { fail(cause); }
}
async function submitGeneral() {
  if (!generalTemplateId.value || !generalDescription.value.trim() || busy.value) return;
  busy.value = true; error.value = '';
  try {
    const created = await v3ApprovalApi.create({ templateId: Number(generalTemplateId.value),
      applicationScope: null, requestKey: crypto.randomUUID(), generalSnapshot: { description: generalDescription.value.trim() },
      items: null, attachmentIds: [] });
    generalDescription.value = ''; generalTemplateId.value = ''; await openDetail({ id: created.approval.id });
  } catch (cause) { fail(cause); }
  finally { busy.value = false; }
}
async function refreshDetail() { if (detail.value) await openDetail({ id: detail.value.approval.id }); }
async function process(kind) {
  if (!canProcess.value || busy.value) return;
  if (kind === 'RETURN_STEP' && !action.targetFlowNodeId) { error.value = '请选择合法的返回步骤'; return; }
  if (kind !== 'APPROVE' && !action.comment.trim()) { error.value = '请填写打回原因'; return; }
  busy.value = true; error.value = '';
  const body = { comment: action.comment.trim(), expectedVersion: detail.value.approval.version,
    targetFlowNodeId: kind === 'RETURN_STEP' ? Number(action.targetFlowNodeId) : null };
  try {
    if (kind === 'APPROVE') await v3ApprovalApi.approve(detail.value.approval.id, body);
    if (kind === 'RETURN_STEP') await v3ApprovalApi.returnStep(detail.value.approval.id, body);
    if (kind === 'RETURN_APPLICANT') await v3ApprovalApi.returnApplicant(detail.value.approval.id, body);
    action.comment = ''; action.targetFlowNodeId = ''; await refreshDetail(); await load(page.value);
  } catch (cause) { fail(cause); }
  finally { busy.value = false; }
}
async function loadTemplates() {
  loading.value = true; error.value = '';
  try { [templates.value, options.value] = await Promise.all([v3ApprovalApi.templates(), v3ApprovalApi.options()]); tab.value = 'flows'; }
  catch (cause) { fail(cause); }
  finally { loading.value = false; }
}
async function viewFlow(row) {
  try { flowDetail.value = await v3ApprovalApi.flow(row.flow_id); }
  catch (cause) { fail(cause); }
}
function resetForm() {
  Object.assign(form, { stableCode: '', name: '', businessType: 'GENERAL', expenseType: '',
    initiatorRoleIds: [], nodes: [{ name: '', approverUserId: '', allowReturn: true }] });
}
function actionEvidence(actionId) {
  return (detail.value?.actionAttachments || []).filter((file) => Number(file.actionId) === Number(actionId))
    .map((file) => ({ id: file.attachmentId, originalFilename: file.filename, contentType: file.contentType }));
}
async function saveTemplate() {
  busy.value = true; error.value = '';
  try {
    const body = { ...form, expenseType: form.businessType === 'EXPENSE' ? form.expenseType : null,
      nodes: form.nodes.map((node) => ({ ...node, approverUserId: Number(node.approverUserId) })) };
    const result = flowDetail.value ? await v3ApprovalApi.nextVersion(flowDetail.value.template_id, body)
      : await v3ApprovalApi.createTemplate(body);
    resetForm(); await loadTemplates(); flowDetail.value = await v3ApprovalApi.flow(result.id);
  } catch (cause) { fail(cause); }
  finally { busy.value = false; }
}
async function publish() {
  if (!flowDetail.value || busy.value) return;
  busy.value = true; error.value = '';
  try { flowDetail.value = await v3ApprovalApi.publish(flowDetail.value.id); await loadTemplates(); }
  catch (cause) { fail(cause); }
  finally { busy.value = false; }
}
function startNextVersion() {
  if (!flowDetail.value) return;
  form.stableCode = flowDetail.value.stable_code;
  form.name = flowDetail.value.name;
  form.businessType = flowDetail.value.business_type;
  form.expenseType = flowDetail.value.expense_type || '';
  form.initiatorRoleIds = (flowDetail.value.initiatorRoles || []).map((role) => Number(role.id));
  form.nodes = (flowDetail.value.nodes || []).map((node) => ({
    name: node.node_name, approverUserId: Number(node.approver_user_id), allowReturn: !!node.allow_return
  }));
  tab.value = 'configure';
}
onMounted(() => tab.value === 'flows' ? loadTemplates() : load());
</script>

<template>
  <div class="ui-page-container">
    <PageHeader :title="tab === 'flows' || tab === 'configure' ? '流程与模板配置' : scope === 'MINE' ? '我的申请' : scope === 'HISTORY' ? '审批记录' : '待我处理'" description="按正式流程版本处理审批；申请事实与操作历史分别保存。">
      <template #actions><button v-if="can('approval:create')" type="button" @click="startGeneral">发起普通审批</button>
        </template>
    </PageHeader>
    <p v-if="error" class="error" role="alert">{{ error }} <button type="button" @click="tab === 'detail' ? refreshDetail() : load(page)">重试</button></p>
    <section v-if="tab === 'list'">
      <DataTable :rows="rows" :loading="loading" empty-text="当前没有审批记录"
        :columns="[{key:'approvalNo',label:'审批编号'},{key:'templateName',label:'业务'},{key:'status',label:'状态'},
          {key:'applicantPhone',label:'申请人'},{key:'currentStepName',label:'当前步骤'},
          {key:'createdAt',label:'发起时间'},{key:'actions',label:'操作'}]">
        <template #status="{value}"><StatusTag :value="value" /> {{ statusName[value] || value }}</template>
        <template #createdAt="{value}">{{ formatTime(value) }}</template>
        <template #actions="{row}"><button type="button" @click="openDetail(row)">查看详情</button></template>
      </DataTable>
      <PaginationBar :page-no="page" :page-size="20" :total="total" @change="load" />
    </section>
    <section v-else-if="tab === 'detail' && detail" class="ui-workflow">
      <button type="button" @click="tab='list';detail=null">← 返回审批列表</button>
      <div class="ui-section-card">
        <h2>{{ detail.approval.template_name }} <small>{{ detail.approval.approval_no }}</small></h2>
        <p>{{ typeName[detail.approval.business_type] }} · {{ statusName[detail.approval.status] || detail.approval.status }}
          · 第 {{ detail.approval.version_no }} 版流程</p>
        <p v-if="detail.approval.application_scope">申请属性：{{ detail.approval.application_scope === 'DAILY' ? '日常费用' : '任务费用' }}</p>
        <p v-if="detail.approval.current_step_name">当前步骤：{{ detail.approval.current_step_name }}</p>
      </div>
      <div class="ui-section-card">
        <h2>本次申请 · 第 {{ detail.submissions.at(-1)?.version_no }} 次提交</h2>
        <DataTable v-if="expenseItems.length" :rows="expenseItems"
          :columns="[{key:'business_date',label:'业务日期'},{key:'description',label:'明细'},
            {key:'amount',label:'金额',numeric:true},{key:'receipt_no',label:'票据号'},
            {key:'invoice_no',label:'发票号'},{key:'station_name',label:'站点'}]">
          <template #amount="{value}">{{ money(value) }}</template>
        </DataTable>
        <p v-else-if="detail.approval.business_type === 'GENERAL'">{{ snapshot.content?.description || snapshot.content?.reason || '普通审批内容已提交' }}</p>
        <div v-else-if="detail.approval.business_type === 'TIRE_REPLACEMENT'" class="ui-section-card">
          <h3>换胎申请事实</h3>
          <dl v-if="detail.tireRequest" class="ui-detail-grid">
            <dt>确认胎号</dt><dd>{{ detail.tireRequest.tireNo }}</dd>
            <dt>申请司机</dt><dd>{{ detail.tireRequest.driverName }}</dd>
            <dt>公司车辆</dt><dd>{{ detail.tireRequest.vehiclePlate }}</dd>
            <dt>申请时间</dt><dd>{{ formatTime(detail.tireRequest.requestedAt) }}</dd>
            <dt>照片识别文字</dt><dd>{{ detail.tireRequest.recognizedText || '未识别出文字，已由司机人工确认胎号' }}</dd>
          </dl>
          <p class="muted">审批通过后才正式申领库存轮胎；不生成普通费用。</p>
        </div>
        <ApprovalEvidence v-if="submissionEvidence.length" title="申请附件" :attachments="submissionEvidence" />
      </div>
      <div v-if="isOwnReturned && detail.approval.business_type !== 'TIRE_REPLACEMENT'" class="ui-section-card">
        <h2>修改后重新提交</h2>
        <p>本次修改会形成新的提交版本，原审批和处理记录保留。</p>
        <label v-if="detail.approval.business_type === 'GENERAL'" class="ui-form-field">申请内容
          <textarea v-model="resubmitDescription" rows="4" /></label>
        <div v-for="(row,index) in resubmitRows" v-else :key="index" class="expense-form-grid">
          <h3>费用明细 {{ index + 1 }}</h3>
          <label class="ui-form-field">日期<input v-model="row.businessDate" type="date" /></label>
          <label class="ui-form-field">司机<select v-model="row.driverId" @change="loadTaskChoices(row.driverId)"><option value="">请选择</option>
            <option v-for="driver in resubmitOptions.drivers" :key="driver.id" :value="driver.id">{{ driver.name }} · {{ driver.phone }}</option></select></label>
          <label class="ui-form-field">公司车牌<select v-model="row.vehicleId"><option value="">请选择</option>
            <option v-for="vehicle in resubmitOptions.vehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.plateNo }}</option></select></label>
          <label v-if="detail.approval.application_scope === 'TASK'" class="ui-form-field">参与任务<select v-model="row.targetTaskId">
            <option value="">请选择任务</option><option v-for="task in taskChoices[row.driverId] || []" :key="task.taskId" :value="task.taskId">{{ task.taskNo }}</option></select></label>
          <label class="ui-form-field">金额（元）<input v-model="row.amount" type="number" min="0.01" step="0.01" /></label>
          <label class="ui-form-field">明细<input v-model="row.description" /></label>
          <label class="ui-form-field">票据号<input v-model="row.receiptNo" /></label>
          <label class="ui-form-field">发票号<input v-model="row.invoiceNo" /></label>
          <label class="ui-form-field">站点名称<input v-model="row.stationName" /></label>
          <label class="ui-form-field">备注<input v-model="row.remark" /></label>
        </div>
        <div class="ui-dialog-actions"><button class="primary" type="button" :disabled="busy" @click="submitAgain">重新提交</button></div>
      </div>
      <div class="ui-section-card">
        <h2>处理时间线</h2>
        <ol class="approval-timeline"><li v-for="entry in detail.actions" :key="entry.id">
          <strong>{{ actionName[entry.action_type] || entry.action_type }}</strong>
          <span v-if="entry.target_step_name_snapshot"> → {{ entry.target_step_name_snapshot }}</span>
          <span> · {{ formatTime(entry.operated_at) }}</span>
          <p v-if="entry.comment">{{ entry.comment }}</p>
          <ApprovalEvidence v-if="actionEvidence(entry.id).length" title="处理附件" :attachments="actionEvidence(entry.id)" />
        </li></ol>
        <p v-if="!detail.actions.length" class="ui-empty">尚无处理记录</p>
      </div>
      <div v-if="canProcess" class="ui-section-card">
        <h2>处理当前步骤</h2>
        <label class="ui-form-field">处理意见 <textarea v-model.trim="action.comment" rows="3" placeholder="打回时须说明原因"></textarea></label>
        <label v-if="returnable.length && can('approval:return')" class="ui-form-field">指定返回步骤
          <select v-model="action.targetFlowNodeId"><option value="">请选择步骤</option>
            <option v-for="step in returnable" :key="step.flowNodeId" :value="step.flowNodeId">
              {{ step.stepName }} · 审批人 {{ step.approverPhone }}
            </option>
          </select>
        </label>
        <div class="ui-dialog-actions">
          <button v-if="can('approval:return')" type="button" :disabled="busy" @click="process('RETURN_APPLICANT')">打回申请人</button>
          <button v-if="returnable.length && can('approval:return')" type="button" :disabled="busy || !action.targetFlowNodeId" @click="process('RETURN_STEP')">打回指定步骤</button>
          <button v-if="can('approval:process')" type="button" class="primary" :disabled="busy" @click="process('APPROVE')">通过当前步骤</button>
        </div>
      </div>
    </section>
    <section v-else-if="tab === 'general'" class="ui-workflow">
      <button type="button" @click="tab='list'">← 返回审批列表</button>
      <div class="ui-section-card"><h2>发起普通审批</h2>
        <label class="ui-form-field">审批业务<select v-model="generalTemplateId"><option value="">请选择</option>
          <option v-for="entry in generalTemplates" :key="entry.id" :value="entry.id">{{ entry.name }}</option></select></label>
        <label class="ui-form-field">申请内容<textarea v-model="generalDescription" rows="5" /></label>
        <div class="ui-dialog-actions"><button class="primary" type="button" :disabled="busy || !generalTemplateId || !generalDescription.trim()" @click="submitGeneral">提交审批</button></div>
      </div>
    </section>
    <section v-else-if="tab === 'flows'">
      <button type="button" @click="tab='list'">← 返回审批列表</button>
      <div class="ui-list-heading"><h2>流程与模板配置</h2><button class="primary" type="button" @click="flowDetail=null;resetForm();tab='configure'">新建模板</button></div>
      <DataTable :rows="templates" :loading="loading" empty-text="尚无审批模板"
        :columns="[{key:'name',label:'业务名称'},{key:'business_type',label:'业务类型'},
          {key:'version_no',label:'版本'},{key:'flow_status',label:'状态'},{key:'actions',label:'操作'}]">
        <template #business_type="{value}">{{ typeName[value] || value }}</template>
        <template #version_no="{value}">第 {{ value }} 版</template>
        <template #flow_status="{value}">{{ value === 'ACTIVE' ? '已发布' : value === 'DRAFT' ? '草稿' : '已归档' }}</template>
        <template #actions="{row}"><button type="button" @click="viewFlow(row)">查看步骤</button></template>
      </DataTable>
      <div v-if="flowDetail" class="ui-section-card">
        <h2>{{ flowDetail.name }} · 第 {{ flowDetail.version_no }} 版</h2>
        <ol><li v-for="node in flowDetail.nodes" :key="node.flowNodeId">{{ node.node_name }} · {{ node.approverPhone }}</li></ol>
        <div class="ui-dialog-actions"><button v-if="flowDetail.status === 'ACTIVE'" type="button" @click="startNextVersion">创建下一版本</button>
          <button v-if="flowDetail.status === 'DRAFT'" class="primary" type="button" :disabled="busy" @click="publish">发布流程</button></div>
      </div>
    </section>
    <section v-else-if="tab === 'configure'" class="ui-workflow">
      <button type="button" @click="tab='flows'">← 返回流程配置</button>
      <div class="ui-section-card"><h2>{{ flowDetail ? '创建下一流程版本' : '新建审批模板' }}</h2>
        <label class="ui-form-field">稳定业务编号<input v-model.trim="form.stableCode" :disabled="!!flowDetail" placeholder="例如：REPAIR_EXPENSE" /></label>
        <label class="ui-form-field">业务名称<input v-model.trim="form.name" placeholder="例如：维修费申请" /></label>
        <label class="ui-form-field">业务类型<select v-model="form.businessType" :disabled="!!flowDetail">
          <option value="EXPENSE">费用申请</option><option value="GENERAL">普通审批</option>
          <option value="TIRE_REPLACEMENT">换胎审批</option></select></label>
        <label v-if="form.businessType === 'EXPENSE'" class="ui-form-field">费用大类<select v-model="form.expenseType" :disabled="!!flowDetail">
          <option value="">请选择</option><option v-for="type in Object.keys(expenseTypeName)" :key="type" :value="type">{{ expenseTypeName[type] }}</option>
        </select></label>
        <fieldset><legend>可发起角色</legend><label v-for="role in options.roles" :key="role.id">
          <input v-model="form.initiatorRoleIds" type="checkbox" :value="Number(role.id)" /> {{ role.roleName }}</label></fieldset>
        <h3>审批步骤</h3>
        <div v-for="(node,index) in form.nodes" :key="index" class="approval-step">
          <label>第 {{ index + 1 }} 步名称<input v-model.trim="node.name" placeholder="例如：财务复核" /></label>
          <label>审批人<select v-model="node.approverUserId"><option value="">请选择审批人</option>
            <option v-for="person in options.approvers" :key="person.id" :value="Number(person.id)">{{ person.phone }}</option>
          </select></label>
          <label><input v-model="node.allowReturn" type="checkbox" /> 允许后续步骤返回到此步</label>
          <button v-if="form.nodes.length > 1" type="button" @click="form.nodes.splice(index,1)">移除</button>
        </div>
        <div class="ui-dialog-actions"><button type="button" @click="form.nodes.push({name:'',approverUserId:'',allowReturn:true})">添加步骤</button>
          <button class="primary" type="button" :disabled="busy" @click="saveTemplate">保存草稿</button></div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.approval-timeline { padding-left: 1.4rem; }
.approval-timeline li { padding: .7rem 0; border-bottom: 1px solid var(--border, #e5ece9); }
.approval-timeline p { margin: .3rem 0 0; }
.approval-step { display: grid; grid-template-columns: 1fr 1fr auto auto; gap: .8rem; align-items: end; padding: .8rem 0; border-bottom: 1px solid #e5ece9; }
.approval-step label { display: grid; gap: .35rem; }
.expense-form-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: .8rem; padding: .8rem 0; border-bottom: 1px solid var(--border,#e5ece9); }
.expense-form-grid h3 { grid-column: 1 / -1; }
fieldset { border: 1px solid #e5ece9; border-radius: 8px; padding: .8rem; display: flex; flex-wrap: wrap; gap: 1rem; }
@media (max-width: 900px) { .approval-step, .expense-form-grid { grid-template-columns: 1fr; } }
</style>
