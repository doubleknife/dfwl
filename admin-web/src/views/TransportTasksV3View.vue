<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { attachmentApi, masterApi, routeApi, routeTemplateApi, systemApi, v3FinanceApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import StatusTag from '../components/StatusTag.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import FormDialog from '../components/FormDialog.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import ApprovalEvidence from '../components/ApprovalEvidence.vue';
import RouteWeightImageUpload from '../components/RouteWeightImageUpload.vue';
import { adjustedNetWeight, operatorIdentity, weightAdjustmentBody, weightEntries, weightText } from './routeWeightEvidence.js';

const section = ref('tasks');
const tasks = ref([]);
const financeByTask = ref({});
const selectedFinance = ref(null);
const templates = ref([]);
const products = ref([]);
const selected = ref(null);
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
const loading = ref(false);
const error = ref('');
const pageNo = ref(1);
const total = ref(0);
const filters = reactive({ businessDate: '', status: '', taskNo: '' });
const form = reactive({ stableCode: '', name: '', customerName: '', productId: '', loadingPlace: '', unloadingPlace: '', defaultUnitPrice: '', defaultInformationFee: '', defaultDriverSalary: '', remark: '' });
const statusName = { UNPUBLISHED: '未发布', PUBLISHED: '待发车', TRANSFER_PENDING: '待接手发车', IN_TRANSIT: '运输中', COMPLETED: '已完成', CANCELLED: '已取消' };
const taskBaseColumns = [
  { key: 'taskNo', label: '任务号' }, { key: 'businessDate', label: '业务日期' },
  { key: 'customerName', label: '客户' }, { key: 'loadingPlace', label: '装货地' },
  { key: 'unloadingPlace', label: '卸货地' }, { key: 'currentDriverName', label: '当前司机' },
  { key: 'currentVehiclePlate', label: '当前车牌' }, { key: 'status', label: '状态' }
];
const taskColumns = computed(() => [...taskBaseColumns,
  ...(can('report:task:view') ? [
    { key: 'income_amount', label: '收入', numeric: true },
    { key: 'total_expense_amount', label: '总支出', numeric: true },
    { key: 'profit_amount', label: '利润', numeric: true }
  ] : []), { key: 'actions', label: '操作' }]);
const templateColumns = [
  { key: 'stableCode', label: '模板编号' }, { key: 'name', label: '线路模板' },
  { key: 'customerName', label: '客户名称' }, { key: 'loadingPlace', label: '装货地' },
  { key: 'unloadingPlace', label: '卸货地' }, { key: 'enabled', label: '状态' },
  { key: 'actions', label: '操作' }
];

async function loadTasks(next = 1) {
  loading.value = true; error.value = '';
  try {
    const page = await routeApi.list(next, 20, filters);
    tasks.value = page.records || [];
    financeByTask.value = {};
    const completed = tasks.value.filter((item) => item.status === 'COMPLETED').map((item) => item.id);
    if (can('report:task:view') && completed.length) {
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
  selectedFinance.value = null;
  try {
    selected.value = await routeApi.detail(row.id);
    if (selected.value.status === 'COMPLETED' && can('report:task:view')) {
      selectedFinance.value = await v3FinanceApi.task(row.id);
    }
    participants.value = can('task:participants:view') ? await routeApi.participants(row.id) : [];
    weights.value = can('task:weight:view') ? weightEntries(await routeApi.weightVersions(row.id)) : [];
    if (can('task:weight:view')) {
      const [tickets, changes] = await Promise.all([
        attachmentApi.list('TASK', row.id, 'ROUTE_UNLOAD_WEIGHT_TICKET'),
        attachmentApi.list('TASK', row.id, 'WEIGHT_ADJUST')
      ]);
      originalFiles.value = tickets || [];
      adjustmentFiles.value = changes || [];
    }
    if (can('user:manage')) {
      const page = await systemApi.users(1, 200);
      users.value = page.records || [];
    }
  } catch (cause) { error.value = cause.message || '任务详情加载失败'; }
}
function filesFor(weight, kind) {
  const ids = kind === 'original' ? weight.originalTicketAttachmentIds : weight.evidenceAttachmentIds;
  const pool = kind === 'original' ? originalFiles.value : adjustmentFiles.value;
  return pool.filter((file) => (ids || []).includes(file.id));
}
function openAdjustment() {
  if (!selected.value || selected.value.status !== 'COMPLETED' || !can('task:weight:adjust')) return;
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
    stableCode: row?.stableCode || '', name: row?.name || '', customerName: row?.customerName || '',
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
  await loadTasks();
  if (can('template:view')) {
    try { const page = await masterApi.products(); products.value = page.records || page || []; }
    catch { /* 产品列表仅用于模板维护，模板列表仍可读取。 */ }
  }
});
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="运输任务" description="按运输任务查看执行事实；线路模板只保存默认业务信息。">
      <template #actions><button type="button" @click="section === 'tasks' ? loadTasks(pageNo) : loadTemplates()">刷新</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" />
    <div class="ui-tabs" role="tablist" aria-label="运输任务与线路模板">
      <button type="button" :aria-selected="section === 'tasks'" @click="switchSection('tasks')">运输任务</button>
      <button v-if="can('template:view')" type="button" :aria-selected="section === 'templates'" @click="switchSection('templates')">线路模板</button>
    </div>
    <template v-if="section === 'tasks'">
      <FilterBar><label>业务日期<input v-model="filters.businessDate" type="date" /></label>
        <label>状态<select v-model="filters.status"><option value="">全部</option><option v-for="(label, value) in statusName" :key="value" :value="value">{{ label }}</option></select></label>
        <label>任务号<input v-model.trim="filters.taskNo" placeholder="输入任务号" /></label>
        <button type="button" @click="loadTasks(1)">查询</button>
      </FilterBar>
      <DataTable :columns="taskColumns" :rows="tasks" :loading="loading" empty-text="暂无运输任务">
        <template #status="{ row }"><StatusTag :value="row.status" /> {{ statusName[row.status] || row.status }}</template>
        <template #income_amount="{ row }">{{ row.status === 'COMPLETED' && financeByTask[row.id] ? `¥ ${Number(financeByTask[row.id].income_amount).toFixed(2)}` : '—' }}</template>
        <template #total_expense_amount="{ row }">{{ row.status === 'COMPLETED' && financeByTask[row.id] ? `¥ ${Number(financeByTask[row.id].total_expense_amount).toFixed(2)}` : '—' }}</template>
        <template #profit_amount="{ row }">{{ row.status === 'COMPLETED' && financeByTask[row.id] ? `¥ ${Number(financeByTask[row.id].profit_amount).toFixed(2)}` : '—' }}</template>
        <template #actions="{ row }"><button type="button" @click="openTask(row)">详情</button></template>
      </DataTable>
      <PaginationBar :page-no="pageNo" :page-size="20" :total="total" @change="loadTasks" />
    </template>
    <template v-else>
      <div class="ui-list-heading"><h2>线路模板</h2><button v-if="can('template:manage')" type="button" class="primary" @click="openTemplate()">新增模板</button></div>
      <DataTable :columns="templateColumns" :rows="templates" :loading="loading" empty-text="暂无模板">
        <template #enabled="{ row }">{{ row.enabled ? '启用' : '停用' }}</template>
        <template #actions="{ row }"><button v-if="can('template:manage')" type="button" @click="openTemplate(row)">编辑</button>
          <button v-if="can('template:manage')" type="button" @click="setTemplateEnabled(row)">{{ row.enabled ? '停用' : '启用' }}</button></template>
      </DataTable>
    </template>
    <DetailDrawer :open="!!selected" title="运输任务详情" @close="selected = null">
      <template #subtitle>{{ selected?.taskNo }}</template>
      <template v-if="selected">
        <p>{{ selected.loadingPlace }} → {{ selected.unloadingPlace }} · {{ statusName[selected.status] || selected.status }}</p>
        <p>客户：{{ selected.customerName }} · 货物：{{ selected.productName }} · 业务日期：{{ selected.businessDate }}</p>
        <p v-if="can('task:amount:adjust')">实际含税单价：¥{{ selected.actualUnitPrice }} · 实际信息费：¥{{ selected.actualInformationFee }}</p>
        <h3>参与司机</h3>
        <p v-for="person in participants" :key="person.id">#{{ person.sequenceNo }} {{ person.driverName }} · {{ person.driverType === 'OUTSOURCED' ? person.outsourcedPlate : person.companyVehiclePlate }} · {{ person.status }}<span v-if="person.salary != null"> · 工资 ¥{{ person.salary }}</span></p>
        <p v-if="!participants.length">暂无参与信息或当前账号无查看权限</p>
        <section v-if="selectedFinance" class="ui-section-card">
          <h3>任务经营与费用</h3>
          <p>收入 ¥{{ Number(selectedFinance.summary.income_amount).toFixed(2) }} · 信息费 ¥{{ Number(selectedFinance.summary.information_fee_amount).toFixed(2) }} · 参与工资 ¥{{ Number(selectedFinance.summary.salary_amount).toFixed(2) }}</p>
          <p>归属费用净额 ¥{{ Number(selectedFinance.summary.task_expense_amount).toFixed(2) }} · 总支出 ¥{{ Number(selectedFinance.summary.total_expense_amount).toFixed(2) }} · 利润 ¥{{ Number(selectedFinance.summary.profit_amount).toFixed(2) }}</p>
          <p v-if="selectedFinance.expenseTypes.length">按类型：<span v-for="type in selectedFinance.expenseTypes" :key="type.expense_type">{{ ({ REPAIR: '维修费', PENALTY: '罚款', WATER: '水费', TEMP_ELECTRIC: '临时电费', TEMP_GAS: '临时气费', TOLL: '过路费' })[type.expense_type] || '其他费用' }} ¥{{ Number(type.amount_total).toFixed(2) }}（{{ type.entry_count }} 笔）　</span></p>
          <DataTable :columns="[
            { key: 'expense_no', label: '费用编号' }, { key: 'business_date', label: '日期' },
            { key: 'expense_type', label: '类型' }, { key: 'application_scope', label: '申请属性' },
            { key: 'source_type', label: '来源' }, { key: 'driver_name', label: '司机' },
            { key: 'vehicle_plate', label: '公司车牌' }, { key: 'description', label: '明细' },
            { key: 'amount', label: '金额', numeric: true },
            { key: 'attribution_method', label: '归属方式' }, { key: 'approval_no', label: '审批单' },
            { key: 'display_status', label: '状态' }
          ]" :rows="selectedFinance.expenses" empty-text="当前没有归属此任务的正式费用">
            <template #expense_type="{ row }">{{ ({ REPAIR: '维修费', PENALTY: '罚款', WATER: '水费', TEMP_ELECTRIC: '临时电费', TEMP_GAS: '临时气费', TOLL: '过路费' })[row.expense_type] || '其他费用' }}</template>
            <template #application_scope="{ row }">{{ row.application_scope === 'TASK' ? '任务费用' : '日常费用' }}</template>
            <template #source_type="{ row }">{{ row.source_type === 'REVERSAL' ? '费用冲销' : '审批通过' }}</template>
            <template #amount="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
          </DataTable>
        </section>
        <div class="ui-list-heading"><h3>重量记录</h3><button v-if="selected.status === 'COMPLETED' && can('task:weight:adjust')" type="button" @click="openAdjustment">重量纠错</button></div>
        <section v-for="weight in weights" :key="weight.id" class="ui-section-card">
          <h4>{{ weight.businessLabel }}</h4>
          <p>毛重 {{ weightText(weight.grossWeight) }} · 皮重 {{ weightText(weight.tareWeight) }} · 净重 {{ weightText(weight.netWeight) }}</p>
          <p v-if="weight.reason">原因：{{ weight.reason }}</p>
          <p v-if="weight.sourceType === 'ADJUSTMENT'">调整人：{{ operatorIdentity(weight.operatorId, users).name }} · 调整时间：{{ weight.operatedAt }}</p>
          <ApprovalEvidence :attachments="filesFor(weight, weight.sourceType === 'UNLOAD' ? 'original' : 'adjustment')"
            :title="weight.sourceType === 'UNLOAD' ? '原始卸货凭证' : '本次调整证据'" />
        </section>
        <p v-if="!weights.length">暂无重量记录或当前账号无查看权限</p>
      </template>
    </DetailDrawer>
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
        <label>模板编号<input v-model.trim="form.stableCode" required maxlength="64" /></label>
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
