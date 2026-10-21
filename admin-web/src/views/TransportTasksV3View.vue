<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { attachmentApi, dispatchApi, masterApi, routeApi, routeTemplateApi, systemApi, v3FinanceApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import EntityPicker from '../components/EntityPicker.vue';
import DataTable from '../components/DataTable.vue';
import StatusTag from '../components/StatusTag.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import FormDialog from '../components/FormDialog.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import ApprovalEvidence from '../components/ApprovalEvidence.vue';
import RouteWeightImageUpload from '../components/RouteWeightImageUpload.vue';
import { adjustedNetWeight, operatorIdentity, weightAdjustmentBody, weightEntries, weightText } from './routeWeightEvidence.js';
import { formatDateTime } from '../utils/dateTime.js';
import { expenseTypeLabels } from '../utils/displayLabels.js';
import { canCancelTask, canTransferTask, cancelBody, transferBody } from './taskOperations.js';

const props = defineProps({ initialSection: { type: String, default: 'tasks' } });
const section = ref(props.initialSection);
const tasks = ref([]);
const financeByTask = ref({});
const selectedFinance = ref(null);
const templates = ref([]);
const products = ref([]);
const selected = ref(null);
const detailTab = ref('overview');
const templateKeyword = ref('');
const templateStatus = ref('');
const participants = ref([]);
const weights = ref([]);
const originalFiles = ref([]);
const adjustmentFiles = ref([]);
const users = ref([]);
const adjusting = ref(false);
const uploading = ref(false);
const adjustmentImages = ref([]);
const adjustmentForm = reactive({ grossWeight: '', tareWeight: '', reason: '' });
const editing = ref(null);
const saving = ref(false);
const operationTask = ref(null);
const operationType = ref('');
const operationParticipants = ref([]);
const operationDrivers = reactive({});
const operationForm = reactive({ newDriverId: '', outsourcedPlateNo: '', newSalary: '', reason: '', salaries: {}, requestKey: '' });
const operationDriver = computed(() => operationDrivers[operationForm.newDriverId] || null);
const loading = ref(false);
const error = ref('');
const pageNo = ref(1); const pageSize = ref(20);
const total = ref(0);
const filters = reactive({ businessDate: '', status: '', taskNo: '', templateId: '', driverId: '' });
const form = reactive({ name: '', customerName: '', productId: '', loadingPlace: '', unloadingPlace: '', defaultUnitPrice: '', defaultInformationFee: '', defaultDriverSalary: '', remark: '' });
const statusName = { PUBLISHED: '待发车', TRANSFER_PENDING: '待接手发车', IN_TRANSIT: '运输中', COMPLETED: '已完成', CANCELLED: '已取消' };
const standardName = { PENDING: '待判定', MET: '达标', NOT_MET: '未达标', NOT_APPLICABLE: '不适用' };
const standardTone = { PENDING: 'warning', MET: 'success', NOT_MET: 'danger', NOT_APPLICABLE: 'info' };
const loadDriverOptions = (page, keyword) => masterApi.drivers(page, 100, { keyword });
const taskBaseColumns = [
  { key: 'taskNo', label: '任务号' }, { key: 'status', label: '状态' },
  { key: 'currentDriverName', label: '当前司机' }, { key: 'currentVehiclePlate', label: '当前车牌' },
  { key: 'businessDate', label: '业务日期' }, { key: 'customerName', label: '客户 / 线路模板' },
  { key: 'journey', label: '装卸货地' }, { key: 'netWeight', label: '有效净重', numeric: true },
  { key: 'standardResult', label: '是否达标' }
];
const taskColumns = computed(() => [...taskBaseColumns,
  ...(can('PAGE_FINANCE_TASKS') ? [
    { key: 'income_amount', label: '收入', numeric: true },
    { key: 'total_expense_amount', label: '总支出', numeric: true },
    { key: 'profit_amount', label: '利润', numeric: true }
  ] : []), { key: 'actions', label: '操作' }]);
const templateColumns = [
  { key: 'stableCode', label: '模板编号' }, { key: 'name', label: '线路模板' },
  { key: 'customerName', label: '客户名称' }, { key: 'productName', label: '货物' },
  { key: 'journey', label: '装卸货地' }, { key: 'defaults', label: '默认金额' },
  { key: 'enabled', label: '状态' },
  { key: 'actions', label: '操作' }
];
const visibleTemplates = computed(() => templates.value.filter((item) => {
  const keyword = templateKeyword.value.trim().toLowerCase();
  return (!keyword || `${item.stableCode} ${item.name} ${item.customerName} ${item.productName || ''} ${item.loadingPlace} ${item.unloadingPlace}`.toLowerCase().includes(keyword))
    && (!templateStatus.value || String(item.enabled) === templateStatus.value);
}));

async function loadTasks(next = 1) {
  loading.value = true; error.value = '';
  try {
    const page = await routeApi.list(next, pageSize.value, filters);
    tasks.value = page.records || [];
    financeByTask.value = {};
    const completed = tasks.value.filter((item) => item.status === 'COMPLETED').map((item) => item.id);
    if (can('PAGE_FINANCE_TASKS') && completed.length) {
      try {
        const summaries = await v3FinanceApi.taskSummaries(completed);
        financeByTask.value = Object.fromEntries(summaries.map((item) => [item.task_id, item]));
      } catch (cause) { error.value = `经营摘要加载失败：${cause.message || '请重试'}`; }
    }
    total.value = Number(page.total || 0);
    pageNo.value = next;
  } catch (cause) { error.value = cause.message || '运输任务加载失败'; }
  finally { loading.value = false; }
}
async function loadTemplates() {
  loading.value = true; error.value = '';
  try { templates.value = await routeTemplateApi.list(); }
  catch (cause) { error.value = cause.message || '线路模板加载失败'; }
  finally { loading.value = false; }
}
function switchSection(target) {
  section.value = target;
  error.value = '';
  if (target === 'templates') loadTemplates(); else loadTasks(1);
}
async function openTask(row) {
  error.value = '';
  detailTab.value = 'overview';
  selectedFinance.value = null;
  try {
    selected.value = await routeApi.detail(row.id);
    if (selected.value.status === 'COMPLETED' && can('PAGE_FINANCE_TASKS')) {
      selectedFinance.value = await v3FinanceApi.task(row.id);
    }
    participants.value = can('PAGE_ROUTES') ? await routeApi.participants(row.id) : [];
    weights.value = can('PAGE_ROUTES') ? weightEntries(await routeApi.weightVersions(row.id)) : [];
    if (can('PAGE_ROUTES')) {
      const [tickets, changes] = await Promise.all([
        attachmentApi.list('TASK', row.id, 'ROUTE_UNLOAD_WEIGHT_TICKET'),
        attachmentApi.list('TASK', row.id, 'WEIGHT_ADJUST')
      ]);
      originalFiles.value = tickets || [];
      adjustmentFiles.value = changes || [];
    }
    if (can('PAGE_USERS')) {
      const page = await systemApi.users(1, 200);
      users.value = page.records || [];
    }
  } catch (cause) { error.value = cause.message || '任务详情加载失败'; }
}
async function loadOperationDrivers(page = 1, keyword = '') {
  if (!can('PAGE_DRIVERS') && !can('PAGE_DISPATCH')) throw new Error('当前账号没有接手司机候选查询权限');
  const result = can('PAGE_DRIVERS')
    ? await masterApi.drivers(page, 100, { keyword })
    : await dispatchApi.driverOptions(page, 100);
  for (const driver of result.records || []) operationDrivers[String(driver.id)] = driver;
  return result;
}
async function openOperation(kind, row) {
  if (saving.value || (kind === 'transfer' ? !can('BUTTON_ROUTES_TRANSFER') || !canTransferTask(row)
    : !can('BUTTON_ROUTES_CANCEL') || !canCancelTask(row))) return;
  error.value = '';
  try {
    const task = await routeApi.detail(row.id);
    if (kind === 'transfer' ? !canTransferTask(task) : !canCancelTask(task)) {
      await loadTasks(pageNo.value);
      throw new Error('任务状态已变化，请刷新后重试');
    }
    const people = kind === 'transfer' ? await routeApi.participants(row.id) : [];
    operationParticipants.value = people;
    Object.assign(operationForm, { newDriverId: '', outsourcedPlateNo: '', newSalary: '', reason: '',
      salaries: Object.fromEntries(people.map((person) => [person.id, person.salary == null ? '' : String(person.salary)])),
      requestKey: crypto.randomUUID() });
    operationType.value = kind;
    operationTask.value = task;
  } catch (cause) { error.value = cause.message || '无法打开任务操作'; }
}
async function submitOperation() {
  if (saving.value || !operationTask.value) return;
  let body;
  try {
    body = operationType.value === 'transfer'
      ? transferBody(operationTask.value, operationParticipants.value, operationForm, operationDriver.value)
      : cancelBody(operationTask.value, operationForm.reason, operationForm.requestKey);
  } catch (cause) { error.value = cause.message; return; }
  saving.value = true; error.value = '';
  const taskId = operationTask.value.id;
  try {
    if (operationType.value === 'transfer') await routeApi.transfer(taskId, body);
    else await routeApi.cancel(taskId, body);
    operationTask.value = null;
    await loadTasks(pageNo.value);
    if (selected.value?.id === taskId) await openTask({ id: taskId });
  } catch (cause) { error.value = cause.message || '任务操作失败，请重试'; }
  finally { saving.value = false; }
}
function filesFor(weight, kind) {
  const ids = kind === 'original' ? weight.originalTicketAttachmentIds : weight.evidenceAttachmentIds;
  const pool = kind === 'original' ? originalFiles.value : adjustmentFiles.value;
  return pool.filter((file) => (ids || []).includes(file.id));
}
function openAdjustment() {
  if (!selected.value || selected.value.status !== 'COMPLETED' || !can('BUTTON_ROUTES_WEIGHT_ADJUST')) return;
  Object.assign(adjustmentForm, { grossWeight: String(selected.value.grossWeight || ''),
    tareWeight: String(selected.value.tareWeight || ''), reason: '' });
  adjustmentImages.value = [];
  adjusting.value = true;
}
async function saveAdjustment() {
  if (saving.value || uploading.value || !adjustedNetWeight(adjustmentForm.grossWeight, adjustmentForm.tareWeight)) return;
  saving.value = true; error.value = '';
  try {
    await routeApi.adjustWeight(selected.value.id,
      weightAdjustmentBody(adjustmentForm, adjustmentImages.value, selected.value.version));
    adjusting.value = false;
    await openTask(selected.value);
    await loadTasks(pageNo.value);
  } catch (cause) { error.value = cause.message || '重量纠错失败；已上传图片仍保留'; }
  finally { saving.value = false; }
}
function openTemplate(row = null) {
  editing.value = row || {};
  Object.assign(form, {
    name: row?.name || '', customerName: row?.customerName || '',
    productId: row?.productId || '', loadingPlace: row?.loadingPlace || '',
    unloadingPlace: row?.unloadingPlace || '', defaultUnitPrice: row?.defaultUnitPrice ?? '',
    defaultInformationFee: row?.defaultInformationFee ?? '', defaultDriverSalary: row?.defaultDriverSalary ?? '',
    remark: row?.remark || ''
  });
}
async function saveTemplate() {
  if (saving.value) return;
  saving.value = true; error.value = '';
  try {
    const body = { ...form, productId: Number(form.productId), expectedVersion: editing.value?.id ? editing.value.version : null };
    if (editing.value?.id) await routeTemplateApi.update(editing.value.id, body);
    else await routeTemplateApi.create(body);
    editing.value = null;
    await loadTemplates();
  } catch (cause) { error.value = cause.message || '线路模板保存失败'; }
  finally { saving.value = false; }
}
async function setTemplateEnabled(row) {
  if (saving.value) return;
  saving.value = true; error.value = '';
  try { await routeTemplateApi.setEnabled(row.id, !row.enabled, row.version); await loadTemplates(); }
  catch (cause) { error.value = cause.message || '模板状态更新失败'; }
  finally { saving.value = false; }
}
onMounted(async () => {
  if (section.value === 'templates') await loadTemplates();
  else await loadTasks();
  if (can('PAGE_PRODUCTS') || can('BUTTON_TEMPLATES_MANAGE')) {
    try { const page = await masterApi.products(); products.value = page.records || page || []; }
    catch { /* 产品列表仅用于模板维护，模板列表仍可读取。 */ }
  }
  if (can('PAGE_TEMPLATES') && section.value === 'tasks') {
    try { templates.value = await routeTemplateApi.list(); }
    catch { /* 模板筛选不可用时仍可按日期与任务号查看任务。 */ }
  }
});
</script>

<template>
  <main class="ui-page-container">
    <PageHeader :title="section === 'tasks' ? '运输任务' : '线路模板'" :description="section === 'tasks' ? '查看运输进度、参与司机、重量证据及经营事实。' : '维护线路业务资料和默认金额；已创建任务使用自己的历史快照。'">
      <template #actions><button type="button" @click="section === 'tasks' ? loadTasks(pageNo) : loadTemplates()">刷新</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" :retryable="false" />
    <template v-if="section === 'tasks'">
      <FilterBar class="task-filters"><label>业务日期<input v-model="filters.businessDate" type="date" /></label>
        <label>状态<select v-model="filters.status"><option value="">全部</option><option v-for="(label, value) in statusName" :key="value" :value="value">{{ label }}</option></select></label>
        <label v-if="can('PAGE_TEMPLATES')">线路模板<select v-model="filters.templateId"><option value="">全部模板</option><option v-for="item in templates" :key="item.id" :value="item.id">{{ item.name }} · {{ item.customerName }}</option></select></label>
        <label v-if="can('PAGE_DRIVERS')">参与司机<EntityPicker v-model="filters.driverId" remote-search :load-page="loadDriverOptions" :label-of="(driver) => `${driver.name} · ${driver.phone}`" placeholder="全部司机" /></label>
        <label class="task-number-filter">任务号<input v-model.trim="filters.taskNo" placeholder="输入任务号" @keyup.enter="loadTasks(1)" /></label>
        <button class="task-search-button" type="button" @click="loadTasks(1)">查询</button>
      </FilterBar>
      <DataTable class="transport-task-table ui-paged-table" :columns="taskColumns" :rows="tasks" :loading="loading" empty-text="暂无运输任务">
        <template #customerName="{ row }"><strong>{{ row.customerName }}</strong><small class="task-table-subline">{{ row.templateName || '线路模板名称未提供' }}</small></template>
        <template #journey="{ row }">{{ row.loadingPlace }} → {{ row.unloadingPlace }}</template>
        <template #netWeight="{ row }">{{ row.netWeight == null ? '—' : `${row.netWeight} 吨` }}</template>
        <template #standardResult="{ row }"><StatusTag :value="standardName[row.standardResult] || '状态异常'" :tone="standardTone[row.standardResult] || 'danger'" /></template>
        <template #status="{ row }"><StatusTag :value="statusName[row.status] || '状态异常'" /></template>
        <template #income_amount="{ row }">{{ row.status === 'COMPLETED' && financeByTask[row.id] ? `¥ ${Number(financeByTask[row.id].income_amount).toFixed(2)}` : '—' }}</template>
        <template #total_expense_amount="{ row }">{{ row.status === 'COMPLETED' && financeByTask[row.id] ? `¥ ${Number(financeByTask[row.id].total_expense_amount).toFixed(2)}` : '—' }}</template>
        <template #profit_amount="{ row }">{{ row.status === 'COMPLETED' && financeByTask[row.id] ? `¥ ${Number(financeByTask[row.id].profit_amount).toFixed(2)}` : '—' }}</template>
        <template #actions="{ row }"><div class="task-row-actions">
          <button type="button" class="ui-detail-button" @click="openTask(row)">查看详情</button>
          <button v-if="can('BUTTON_ROUTES_TRANSFER')" type="button" :disabled="!canTransferTask(row)"
            :title="canTransferTask(row) ? '转派当前参与司机' : '仅运输中或待接手发车的任务可转派'"
            @click="openOperation('transfer', row)">转派</button>
          <button v-if="can('BUTTON_ROUTES_CANCEL')" type="button" class="ui-danger-link" :disabled="!canCancelTask(row)"
            :title="canCancelTask(row) ? '取消未发车任务' : '仅未实际发车且未进入转派链的任务可取消'"
            @click="openOperation('cancel', row)">取消任务</button>
        </div></template>
      </DataTable>
      <PaginationBar :page-no="pageNo" :page-size="pageSize" :total="total" @change="loadTasks" @change-size="(size) => { pageSize = size; loadTasks(1); }" />
    </template>
    <template v-else>
      <div class="ui-list-heading"><div><h2>线路模板</h2><p class="muted">共 {{ templates.length }} 个模板；默认金额仅用于新任务。</p></div><button v-if="can('BUTTON_TEMPLATES_MANAGE')" type="button" class="primary" @click="openTemplate()">新增模板</button></div>
      <FilterBar><label class="ui-search">模板编号、客户或货物<input v-model.trim="templateKeyword" type="search" placeholder="搜索当前模板列表" /></label><label>模板状态<select v-model="templateStatus"><option value="">全部状态</option><option value="true">启用</option><option value="false">停用</option></select></label></FilterBar>
      <DataTable :columns="templateColumns" :rows="visibleTemplates" :loading="loading" empty-text="暂无符合条件的模板">
        <template #productName="{ row }">{{ row.productName || products.find((item) => item.id === row.productId)?.name || '—' }}</template>
        <template #journey="{ row }">{{ row.loadingPlace }} → {{ row.unloadingPlace }}</template>
        <template #defaults="{ row }"><span class="task-amount-stack"><span>单价 ¥{{ Number(row.defaultUnitPrice).toFixed(2) }}</span><span>信息费 ¥{{ Number(row.defaultInformationFee).toFixed(2) }}</span><span>工资 ¥{{ Number(row.defaultDriverSalary).toFixed(2) }}</span></span></template>
        <template #enabled="{ row }"><StatusTag :value="row.enabled ? '启用' : '停用'" /></template>
        <template #actions="{ row }"><button v-if="can('BUTTON_TEMPLATES_MANAGE')" type="button" @click="openTemplate(row)">编辑</button>
          <button v-if="can('BUTTON_TEMPLATES_MANAGE')" type="button" :class="{ 'ui-danger-link': row.enabled }" @click="setTemplateEnabled(row)">{{ row.enabled ? '停用' : '启用' }}</button></template>
      </DataTable>
    </template>
    <DetailDrawer :open="!!selected" :class="{ 'task-finance-drawer': detailTab === 'expenses' }" :resizable="detailTab === 'expenses'" title="运输任务详情" @close="selected = null">
      <template #subtitle>{{ selected?.taskNo }}</template>
      <template v-if="selected">
        <div class="task-detail-tabs" role="tablist" aria-label="任务详情分区">
          <button v-for="item in [{key:'overview',label:'基本信息'},{key:'participants',label:'参与司机与工资'},{key:'weight',label:'重量与磅单'},...(can('PAGE_FINANCE_TASKS') ? [{key:'expenses',label:'费用与经营'}] : [])]" :key="item.key" type="button" role="tab" :aria-selected="detailTab === item.key" :class="{active:detailTab === item.key}" @click="detailTab = item.key">{{ item.label }}</button>
        </div>
        <section v-if="detailTab === 'overview'" class="task-detail-section">
          <h3>任务基本信息</h3>
          <dl class="ui-detail-list"><div><dt>当前状态</dt><dd><StatusTag :value="statusName[selected.status] || '状态异常'" /></dd></div><div><dt>业务日期</dt><dd>{{ selected.businessDate }}</dd></div><div><dt>线路模板</dt><dd>{{ selected.templateName || '—' }}</dd></div><div><dt>客户 / 货物</dt><dd>{{ selected.customerName }} / {{ selected.productName }}</dd></div><div><dt>运输路径</dt><dd>{{ selected.loadingPlace }} → {{ selected.unloadingPlace }}</dd></div><div><dt>当前司机 / 车辆</dt><dd>{{ selected.currentDriverName || '—' }} / {{ selected.currentVehiclePlate || '—' }}</dd></div></dl>
          <dl v-if="can('BUTTON_ROUTES_AMOUNT_ADJUST')" class="ui-detail-list"><div><dt>实际含税单价</dt><dd>¥{{ selected.actualUnitPrice }}</dd></div><div><dt>实际信息费</dt><dd>¥{{ selected.actualInformationFee }}</dd></div></dl>
          <p class="muted">已完成任务作为运输事实永久保留；重量等错误通过相应事实调整。</p>
        </section>
        <section v-if="detailTab === 'participants'" class="task-detail-section">
          <h3>参与司机与核定工资</h3>
          <ol v-if="participants.length" class="task-participant-list"><li v-for="person in participants" :key="person.id"><strong>第 {{ person.sequenceNo }} 位 · {{ person.driverName }}</strong><span>{{ person.driverType === 'OUTSOURCED' ? '外协司机' : '自有司机' }} · {{ person.driverType === 'OUTSOURCED' ? person.outsourcedPlate : person.companyVehiclePlate }}</span><span>核定工资：{{ person.salary == null ? '暂无' : `¥${Number(person.salary).toFixed(2)}` }}</span></li></ol>
          <p v-else class="ui-empty">暂无参与信息或当前账号无查看权限</p>
        </section>
        <section v-if="detailTab === 'expenses' && can('PAGE_FINANCE_TASKS')" class="task-detail-section">
        <section v-if="selectedFinance" class="ui-section-card task-finance-card">
          <h3>任务经营与费用</h3>
          <dl class="ui-detail-list task-finance-summary">
            <div><dt>收入</dt><dd>¥{{ Number(selectedFinance.summary.income_amount).toFixed(2) }}</dd></div>
            <div><dt>信息费</dt><dd>¥{{ Number(selectedFinance.summary.information_fee_amount).toFixed(2) }}</dd></div>
            <div><dt>参与工资</dt><dd>¥{{ Number(selectedFinance.summary.salary_amount).toFixed(2) }}</dd></div>
            <div><dt>归属费用净额</dt><dd>¥{{ Number(selectedFinance.summary.task_expense_amount).toFixed(2) }}</dd></div>
            <div><dt>总支出</dt><dd>¥{{ Number(selectedFinance.summary.total_expense_amount).toFixed(2) }}</dd></div>
            <div><dt>利润</dt><dd>¥{{ Number(selectedFinance.summary.profit_amount).toFixed(2) }}</dd></div>
            <div v-for="type in selectedFinance.expenseTypes" :key="type.expense_type"><dt>{{ expenseTypeLabels[type.expense_type] || '其他费用' }}（{{ type.entry_count }} 笔）</dt><dd>¥{{ Number(type.amount_total).toFixed(2) }}</dd></div>
          </dl>
          <div v-if="selectedFinance.expenses.length" class="task-expense-list">
            <article v-for="expense in selectedFinance.expenses" :key="expense.id" class="task-expense-card">
              <header class="task-expense-heading">
                <div><strong>{{ expenseTypeLabels[expense.expense_type] || '其他费用' }}</strong><span>{{ expense.business_date }} · {{ expense.application_scope === 'TASK' ? '任务费用' : '日常费用' }}</span></div>
                <div class="task-expense-amount"><strong>¥{{ Number(expense.amount).toFixed(2) }}</strong><StatusTag :value="expense.display_status" /></div>
              </header>
              <dl class="task-expense-fields">
                <div><dt>司机</dt><dd>{{ expense.driver_name || '—' }}</dd></div>
                <div><dt>公司车牌</dt><dd>{{ expense.vehicle_plate || '—' }}</dd></div>
                <div><dt>来源</dt><dd>{{ expense.source_type === 'REVERSAL' ? '费用冲销' : expense.source_type === 'CHARGING' ? '充电订单' : '审批通过' }}</dd></div>
                <div><dt>明细</dt><dd>{{ expense.description || '—' }}</dd></div>
                <div><dt>归属方式</dt><dd>{{ expense.attribution_method || '—' }}</dd></div>
                <div><dt>费用编号</dt><dd>{{ expense.expense_no || '—' }}</dd></div>
                <div><dt>审批单</dt><dd>{{ expense.approval_no || '—' }}</dd></div>
              </dl>
            </article>
          </div>
          <p v-else class="ui-empty">当前没有归属此任务的正式费用</p>
        </section>
        <p v-else class="ui-empty">当前任务暂无可查看的经营与费用信息</p>
        </section>
        <section v-if="detailTab === 'weight'" class="task-detail-section">
        <div class="ui-list-heading"><h3>重量记录</h3><button v-if="selected.status === 'COMPLETED' && can('BUTTON_ROUTES_WEIGHT_ADJUST')" type="button" @click="openAdjustment">重量纠错</button></div>
        <section v-for="weight in weights" :key="weight.id" class="ui-section-card">
          <div class="task-weight-heading"><h4>{{ weight.businessLabel }}</h4><span v-if="Number(weight.id) === Number(selected.effectiveWeightVersionId)" class="task-weight-current">当前有效</span><StatusTag :value="standardName[weight.standardResult] || '状态异常'" :tone="standardTone[weight.standardResult] || 'danger'" /></div>
          <p>毛重 {{ weightText(weight.grossWeight) }} · 皮重 {{ weightText(weight.tareWeight) }} · 净重 {{ weightText(weight.netWeight) }}</p>
          <p v-if="weight.reason">原因：{{ weight.reason }}</p>
          <p v-if="weight.sourceType === 'ADJUSTMENT'">调整人：{{ operatorIdentity(weight.operatorId, users).name }} · 调整时间：{{ formatDateTime(weight.operatedAt) }}</p>
          <ApprovalEvidence :attachments="filesFor(weight, weight.sourceType === 'UNLOAD' ? 'original' : 'adjustment')"
            :title="weight.sourceType === 'UNLOAD' ? '原始卸货凭证' : '本次调整证据'" />
        </section>
        <p v-if="!weights.length">暂无重量记录或当前账号无查看权限</p>
        </section>
      </template>
    </DetailDrawer>
    <FormDialog :open="!!operationTask" :title="operationType === 'transfer' ? '转派任务' : '取消任务'" :busy="saving" @close="operationTask = null">
      <form v-if="operationTask" class="ui-form-grid" @submit.prevent="submitOperation">
        <p class="ui-full">任务 {{ operationTask.taskNo }} · {{ statusName[operationTask.status] }}</p>
        <p v-if="error" class="task-operation-error ui-full" role="alert">{{ error }}</p>
        <template v-if="operationType === 'transfer'">
          <label class="ui-full">接手司机<EntityPicker v-model="operationForm.newDriverId" :load-page="loadOperationDrivers"
            :remote-search="can('PAGE_DRIVERS')" :label-of="(driver) => `${driver.name} · ${driver.phone}（${driver.driverType === 'OUTSOURCED' ? '外协' : '自有'}）`"
            placeholder="请选择接手司机" required /></label>
          <p v-if="operationDriver?.driverType === 'INTERNAL'" class="ui-full muted">接手车辆：{{ operationDriver.currentVehiclePlateNo || '未绑定公司车辆' }}</p>
          <label v-if="operationDriver?.driverType === 'OUTSOURCED'" class="ui-full">外协本次真实车牌<input v-model.trim="operationForm.outsourcedPlateNo" required maxlength="32" /></label>
          <p class="ui-full muted">请核定全部已参与司机工资，再填写接手司机工资。</p>
          <label v-for="person in operationParticipants" :key="person.id">第 {{ person.sequenceNo }} 位 · {{ person.driverName }}工资（元）
            <input v-model="operationForm.salaries[person.id]" type="number" min="0" step="0.01" required /></label>
          <label>接手司机工资（元）<input v-model="operationForm.newSalary" type="number" min="0" step="0.01" required /></label>
        </template>
        <p v-else class="ui-full muted">仅未实际发车且未进入转派链的任务可以取消。确认后任务状态将改为“已取消”。</p>
        <label class="ui-full">{{ operationType === 'transfer' ? '转派原因' : '取消原因' }}<textarea v-model.trim="operationForm.reason" required maxlength="500" /></label>
        <div class="ui-dialog-actions ui-full"><button type="button" :disabled="saving" @click="operationTask = null">返回</button>
          <button type="submit" :class="operationType === 'transfer' ? 'primary' : 'ui-danger-button'" :disabled="saving">
            {{ saving ? '处理中…' : operationType === 'transfer' ? '确认转派' : '确认取消任务' }}</button></div>
      </form>
    </FormDialog>
    <FormDialog :open="adjusting" title="重量纠错" :busy="saving || uploading" @close="adjusting = false">
      <form class="ui-form-grid" @submit.prevent="saveAdjustment">
        <p class="ui-full">当前有效重量：毛重 {{ weightText(selected?.grossWeight) }}，皮重 {{ weightText(selected?.tareWeight) }}，净重 {{ weightText(selected?.netWeight) }}</p>
        <label>新毛重（吨）<input v-model="adjustmentForm.grossWeight" inputmode="decimal" required /></label>
        <label>新皮重（吨）<input v-model="adjustmentForm.tareWeight" inputmode="decimal" required /></label>
        <p class="ui-full">新净重：{{ adjustedNetWeight(adjustmentForm.grossWeight, adjustmentForm.tareWeight) || '请填写有效重量' }} 吨</p>
        <label class="ui-full">纠错原因<textarea v-model.trim="adjustmentForm.reason" required /></label>
        <RouteWeightImageUpload v-if="selected" v-model="adjustmentImages" :task-id="selected.id" :disabled="saving" @uploading="uploading = $event" />
        <div class="ui-dialog-actions"><button type="button" @click="adjusting = false">取消</button><button class="primary" :disabled="saving || uploading || !adjustmentForm.reason || !adjustedNetWeight(adjustmentForm.grossWeight, adjustmentForm.tareWeight)">确认纠错</button></div>
      </form>
    </FormDialog>
    <FormDialog :open="!!editing" :title="editing?.id ? '编辑线路模板' : '新增线路模板'" :busy="saving" @close="editing = null">
      <form class="ui-form-grid" @submit.prevent="saveTemplate">
        <label>模板编号<input :value="editing?.id ? editing.stableCode : '保存后自动生成（L＋7 位数字）'" readonly /></label>
        <label>模板名称<input v-model.trim="form.name" required maxlength="128" /></label>
        <label>客户中文名称<input v-model.trim="form.customerName" required maxlength="128" /></label>
        <label>货物<select v-model="form.productId" required><option value="">请选择货物</option><option v-for="p in products" :key="p.id" :value="p.id">{{ p.name }}</option></select></label>
        <label>装货地<input v-model.trim="form.loadingPlace" required /></label>
        <label>卸货地<input v-model.trim="form.unloadingPlace" required /></label>
        <label>默认含税单价<input v-model="form.defaultUnitPrice" type="number" min="0" step="0.01" required /></label>
        <label>默认信息费<input v-model="form.defaultInformationFee" type="number" min="0" step="0.01" required /></label>
        <label>默认司机工资<input v-model="form.defaultDriverSalary" type="number" min="0" step="0.01" required /></label>
        <label>备注<textarea v-model.trim="form.remark" maxlength="500" /></label>
        <div class="ui-dialog-actions"><button type="button" @click="editing = null">取消</button><button class="primary" :disabled="saving">保存模板</button></div>
      </form>
    </FormDialog>
  </main>
</template>

<style scoped>
.task-table-subline { display: block; margin-top: 3px; color: var(--ui-muted); }
.task-amount-stack { display: flex; flex-wrap: wrap; gap: 2px 9px; line-height: 1.45; white-space: nowrap; font-size:12px; }
.task-amount-stack span:first-child { color:#185f4e; font-weight:700; }
.task-detail-tabs { display: flex; gap: 4px; overflow-x: auto; border-bottom: 1px solid var(--ui-border); margin-bottom: 16px; }
.task-detail-tabs button { flex: 0 0 auto; border: 0; border-bottom: 3px solid transparent; background: transparent; color: var(--ui-muted); padding: 10px 12px; }
.task-detail-tabs button.active { background: var(--ui-surface-muted, #eef6f2); border-bottom-color: var(--ui-primary, #087563); color: var(--ui-primary, #087563); font-weight: 700; }
.task-detail-section { display: grid; gap: 12px; min-width: 0; }
.task-finance-drawer :deep(.ui-drawer) { width: min(calc(100vw - 24px), 960px); }
.task-finance-card { min-width: 0; max-width: 100%; overflow: hidden; }
.task-expense-list { display: grid; gap: 10px; max-height: min(440px, 50dvh); overflow-y: auto; overflow-x: hidden; }
.task-expense-card { min-width: 0; padding: 14px; border: 1px solid var(--ui-border); border-radius: 9px; background: #fbfdfb; }
.task-expense-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; padding-bottom: 10px; border-bottom: 1px solid var(--ui-border); }
.task-expense-heading > div, .task-expense-amount { display: grid; gap: 4px; min-width: 0; }
.task-expense-heading span, .task-expense-fields dt { color: var(--ui-muted); font-size: 12px; }
.task-expense-amount { justify-items: end; white-space: nowrap; }
.task-expense-amount strong { color: var(--ui-primary); font-variant-numeric: tabular-nums; }
.task-expense-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px 16px; margin: 12px 0 0; }
.task-expense-fields > div { min-width: 0; }
.task-expense-fields dd { margin: 3px 0 0; overflow-wrap: anywhere; }
@media (max-width: 700px) { .task-expense-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.task-weight-heading { display:flex; align-items:center; flex-wrap:wrap; gap:8px; }
.task-weight-heading h4 { margin:0; }
.task-weight-current { color:var(--ui-muted); font-size:12px; }
.task-participant-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
.task-participant-list li { display: grid; gap: 5px; padding: 12px; border: 1px solid var(--ui-border); border-radius: 8px; }
.task-participant-list li span { color: var(--ui-muted); }
.task-filters { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px 12px; align-items: start; }
.task-filters > label { min-width: 0; }
.task-filters :deep(.ui-entity-picker) { min-width:0; width:100%; }
.task-number-filter { grid-column: span 3; }
.task-search-button { justify-self: start; align-self:end; }
.task-row-actions { display: flex; align-items: center; justify-content: flex-end; gap: 6px; white-space: nowrap; }
.task-row-actions button:disabled { opacity: .45; cursor: not-allowed; }
.transport-task-table :deep(.ui-data-table [data-column="income_amount"]),
.transport-task-table :deep(.ui-data-table [data-column="total_expense_amount"]),
.transport-task-table :deep(.ui-data-table [data-column="profit_amount"]) { min-width: 112px; width: 112px; text-align: right; font-variant-numeric: tabular-nums; }
.transport-task-table :deep(.ui-data-table [data-column="actions"]) { position: sticky; right: 0; min-width: max-content; text-align: right; }
.transport-task-table :deep(.ui-data-table th[data-column="actions"]) { z-index: 3; }
.task-operation-error { color: var(--ui-danger, #a72f37); margin: 0; }
@media (max-width: 1050px) { .task-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } .task-number-filter { grid-column: auto; } }
</style>
