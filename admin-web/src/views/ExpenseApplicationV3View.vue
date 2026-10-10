<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { v3ApprovalApi } from '../api/modules.js';
import { navigate } from '../router/routes.js';
import PageHeader from '../components/PageHeader.vue';
import { appendPreviewRows, dailyItems, emptyExpenseRow, expenseRowError } from './expenseApplicationDraft.js';

const stage = ref(1);
const scope = ref('DAILY');
const templateId = ref('');
const templates = ref([]);
const options = ref({ drivers: [], vehicles: [] });
const candidates = ref([]);
const dailyRows = ref([]);
const busy = ref(false);
const error = ref('');
const requestKey = ref(crypto.randomUUID());
const success = ref(null);
const item = reactive({ businessDate: new Date().toLocaleDateString('sv-SE'), driverId: '',
  vehicleId: '', targetTaskId: '', amount: '', description: '', receiptNo: '',
  invoiceNo: '', remark: '', stationName: '' });
const expenseTemplates = computed(() => templates.value.filter((entry) => entry.business_type === 'EXPENSE'));
const selectedTemplate = computed(() => expenseTemplates.value.find((entry) => String(entry.id) === String(templateId.value)));
const total = computed(() => scope.value === 'DAILY'
  ? dailyRows.value.reduce((sum, row) => sum + (Number(row.amount) || 0), 0)
  : Number(item.amount || 0));
const importedCount = computed(() => dailyRows.value.filter((row) => row.source.startsWith('Excel')).length);
const money = (value) => `¥${Number(value || 0).toFixed(2)}`;
const chosenDriver = computed(() => options.value.drivers.find((row) => String(row.id) === String(item.driverId)));
const chosenVehicle = computed(() => options.value.vehicles.find((row) => String(row.id) === String(item.vehicleId)));
const chosenTask = computed(() => candidates.value.find((row) => String(row.taskId) === String(item.targetTaskId)));
const canAdvance = computed(() => {
  if (!templateId.value) return false;
  if (scope.value === 'DAILY') return dailyRows.value.length > 0 && dailyRows.value.length <= 500
    && dailyRows.value.every((row) => !expenseRowError(row));
  return item.businessDate && item.driverId && item.vehicleId && Number(item.amount) > 0
    && (scope.value === 'DAILY' || item.targetTaskId);
});
function resetIdempotency() { requestKey.value = crypto.randomUUID(); }
watch([scope, templateId], () => { stage.value = 1; resetIdempotency(); });
watch(dailyRows, resetIdempotency, { deep: true });
watch(() => item.driverId, async (id) => {
  item.targetTaskId = ''; candidates.value = [];
  if (scope.value !== 'TASK' || !id) return;
  try { candidates.value = await v3ApprovalApi.taskCandidates(id); }
  catch (cause) { error.value = cause.message || '参与任务加载失败'; }
});
watch(() => item.targetTaskId, (id) => {
  const task = candidates.value.find((row) => String(row.taskId) === String(id));
  if (task) item.vehicleId = String(task.vehicleId);
});
onMounted(async () => {
  busy.value = true;
  try { [templates.value, options.value] = await Promise.all([v3ApprovalApi.available(), v3ApprovalApi.expenseOptions()]); }
  catch (cause) { error.value = cause.message || '费用申请数据加载失败'; }
  finally { busy.value = false; }
});
async function downloadTemplate() {
  error.value = '';
  try {
    const blob = await v3ApprovalApi.dailyTemplate();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a'); link.href = url; link.download = '日常费用申请固定模板.xlsx';
    link.click(); URL.revokeObjectURL(url);
  } catch (cause) { error.value = cause.message || '模板下载失败'; }
}
async function chooseFile(event) {
  const file = event.target.files?.[0]; error.value = '';
  if (!file) return;
  busy.value = true;
  try {
    const preview = await v3ApprovalApi.dailyPreview(file);
    if (dailyRows.value.length + preview.rows.length > 500) throw new Error('一个申请最多 500 条费用明细');
    dailyRows.value = appendPreviewRows(dailyRows.value, preview.rows, options.value);
    if (preview.errorCount) error.value = `已导入 ${preview.rows.length} 条，其中 ${preview.errorCount} 条需要在下表修正后才能提交。`;
  }
  catch (cause) { error.value = cause.message || '预览失败，请检查文件格式'; }
  finally { busy.value = false; event.target.value = ''; }
}
function addDailyRow() { dailyRows.value.push(emptyExpenseRow()); }
function removeDailyRow(index) { dailyRows.value.splice(index, 1); }
function next() {
  error.value = '';
  if (!canAdvance.value) { error.value = '请先补齐必填信息并修正所有错误行'; return; }
  if (scope.value === 'TASK' && (!Number.isFinite(Number(item.amount))
    || Number(item.amount) <= 0 || !/^\d+(\.\d{1,2})?$/.test(String(item.amount)))) {
    error.value = '金额必须大于 0 且最多两位小数'; return;
  }
  if (scope.value === 'DAILY') {
    try { dailyItems(dailyRows.value); } catch (cause) { error.value = cause.message; return; }
  }
  stage.value = 2;
}
async function submit() {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try {
    const items = scope.value === 'DAILY' ? dailyItems(dailyRows.value) : [{
      businessDate: item.businessDate, driverId: Number(item.driverId),
      vehicleId: Number(item.vehicleId), targetTaskId: Number(item.targetTaskId),
      amount: item.amount, description: item.description || null, receiptNo: item.receiptNo || null,
      invoiceNo: item.invoiceNo || null, remark: item.remark || null, stationName: item.stationName || null
    }];
    const result = await v3ApprovalApi.create({
      templateId: Number(templateId.value), applicationScope: scope.value, requestKey: requestKey.value,
      items
    });
    success.value = result.approval; stage.value = 3;
  } catch (cause) { error.value = cause.message || '提交失败，已保留填写信息，请重试'; }
  finally { busy.value = false; }
}
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="发起费用申请" description="选择费用大类并核对明细后提交；日常费用可手工填写，也可用固定 Excel 辅助填表。" />
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="busy && stage === 1" class="ui-loading" role="status">正在处理，请稍候…</div>
    <div v-if="stage === 1" class="ui-workflow">
      <section class="ui-section-card">
        <h2>1. 选择申请来源</h2>
        <div class="ui-subtabs"><button type="button" :class="{active:scope==='DAILY'}" @click="scope='DAILY'">日常费用</button>
          <button type="button" :class="{active:scope==='TASK'}" @click="scope='TASK'">任务费用</button></div>
        <p v-if="scope === 'DAILY'">审批通过后先计入公司费用，暂不归属某个任务。</p>
        <p v-else>先选司机，再从这名司机正式参与的运输任务中选择目标。</p>
        <label class="ui-form-field">费用大类与审批模板
          <select v-model="templateId"><option value="">请选择费用大类</option>
            <option v-for="entry in expenseTemplates" :key="entry.id" :value="entry.id">{{ entry.name }}</option></select>
        </label>
      </section>
      <section v-if="scope === 'TASK'" class="ui-section-card">
        <h2>2. 填写任务费用明细</h2>
        <div class="expense-form-grid">
          <label class="ui-form-field">业务日期 <input v-model="item.businessDate" type="date" required /></label>
          <label class="ui-form-field">司机 <select v-model="item.driverId" required><option value="">请选择司机</option>
            <option v-for="driver in options.drivers" :key="driver.id" :value="driver.id">{{ driver.name }} · {{ driver.phone }}</option></select></label>
          <label v-if="scope === 'TASK'" class="ui-form-field">参与的运输任务 <select v-model="item.targetTaskId" required>
            <option value="">请选择任务</option><option v-for="task in candidates" :key="task.taskId" :value="task.taskId">
              {{ task.taskNo }} · {{ task.loadingPlace }} → {{ task.unloadingPlace }}
            </option></select></label>
          <label class="ui-form-field">公司车辆 <select v-model="item.vehicleId" :disabled="scope==='TASK'" required><option value="">请选择车牌</option>
            <option v-for="vehicle in options.vehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.plateNo }}</option></select></label>
          <label class="ui-form-field">金额（元） <input v-model="item.amount" type="number" min="0.01" step="0.01" required /></label>
          <label class="ui-form-field">明细 <input v-model.trim="item.description" placeholder="可填写费用用途" /></label>
          <label class="ui-form-field">票据号 <input v-model.trim="item.receiptNo" /></label>
          <label class="ui-form-field">发票号 <input v-model.trim="item.invoiceNo" /></label>
          <label class="ui-form-field">站点名称 <input v-model.trim="item.stationName" /></label>
          <label class="ui-form-field">备注 <input v-model.trim="item.remark" /></label>
        </div>
      </section>
      <section v-else class="ui-section-card">
        <div class="expense-heading"><div><h2>2. 当前申请明细</h2><p>手工和 Excel 明细可一起修改、删除；最终只提交下表确认的内容。</p></div>
          <div class="expense-tools"><button type="button" @click="addDailyRow">＋ 手工新增</button><button type="button" @click="downloadTemplate">下载固定 Excel</button>
            <label class="expense-upload">导入 Excel<input type="file" accept=".xlsx" :disabled="busy" @change="chooseFile" /></label></div></div>
        <p v-if="dailyRows.length">共 {{ dailyRows.length }} 条 · Excel 已导入 {{ importedCount }} 条 · 当前金额合计 {{ money(total) }}</p>
        <p v-else class="ui-empty">暂无明细。请手工新增，或下载固定模板填写后导入。</p>
        <div v-if="dailyRows.length" class="expense-table-scroll"><table class="expense-table"><thead><tr>
          <th>来源</th><th>业务日期</th><th>司机</th><th>公司车辆</th><th>金额（元）</th><th>明细</th><th>票据号</th><th>发票号</th><th>站点名称</th><th>备注</th><th>操作</th>
        </tr></thead><tbody><tr v-for="(row,index) in dailyRows" :key="row.localKey">
          <td>{{ row.source }}</td><td><input v-model="row.businessDate" type="date" :aria-label="`第 ${index + 1} 条业务日期`" /></td>
          <td><select v-model="row.driverId" :aria-label="`第 ${index + 1} 条司机`"><option value="">请选择司机</option><option v-for="driver in options.drivers" :key="driver.id" :value="driver.id">{{ driver.name }} · {{ driver.phone }}</option></select></td>
          <td><select v-model="row.vehicleId" :aria-label="`第 ${index + 1} 条公司车辆`"><option value="">请选择车牌</option><option v-for="vehicle in options.vehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.plateNo }}</option></select></td>
          <td><input v-model="row.amount" type="number" min="0.01" step="0.01" :aria-label="`第 ${index + 1} 条金额`" /></td>
          <td><input v-model.trim="row.description" :aria-label="`第 ${index + 1} 条明细`" /></td>
          <td><input v-model.trim="row.receiptNo" :aria-label="`第 ${index + 1} 条票据号`" /></td>
          <td><input v-model.trim="row.invoiceNo" :aria-label="`第 ${index + 1} 条发票号`" /></td>
          <td><input v-model.trim="row.stationName" :aria-label="`第 ${index + 1} 条站点名称`" /></td>
          <td><input v-model.trim="row.remark" :aria-label="`第 ${index + 1} 条备注`" /></td>
          <td><button type="button" @click="removeDailyRow(index)">删除</button><span v-if="expenseRowError(row)" class="error">{{ expenseRowError(row) }}</span></td>
        </tr></tbody></table></div>
      </section>
      <div class="ui-dialog-actions"><button class="primary" type="button" :disabled="busy || !canAdvance" @click="next">核对申请摘要</button></div>
    </div>
    <section v-else-if="stage === 2" class="ui-workflow">
      <div class="ui-section-card"><h2>3. 提交前核对</h2>
        <p>申请来源：{{ scope === 'DAILY' ? '日常费用 · 审批后暂不归任务' : '任务费用' }}</p>
        <p>费用大类：{{ selectedTemplate?.name }}</p>
        <template v-if="scope === 'DAILY'"><p>明细：{{ dailyRows.length }} 条，合计 {{ money(total) }}。所有明细共用一次审批，通过后逐条形成正式费用。</p>
          <div class="expense-summary-scroll"><table class="expense-table"><thead><tr><th>序号</th><th>日期</th><th>司机</th><th>车辆</th><th>金额</th><th>明细</th></tr></thead><tbody>
            <tr v-for="(row,index) in dailyRows" :key="row.localKey"><td>{{ index + 1 }}</td><td>{{ row.businessDate }}</td>
              <td>{{ options.drivers.find((driver) => String(driver.id) === String(row.driverId))?.name }}</td>
              <td>{{ options.vehicles.find((vehicle) => String(vehicle.id) === String(row.vehicleId))?.plateNo }}</td>
              <td>{{ money(row.amount) }}</td><td>{{ row.description || '—' }}</td></tr>
          </tbody></table></div></template>
        <template v-else><p>司机：{{ chosenDriver?.name }} · 车辆：{{ chosenVehicle?.plateNo }}</p>
          <p>任务：{{ chosenTask?.taskNo }}</p><p>日期：{{ item.businessDate }} · 金额：{{ money(total) }}</p>
          <p v-if="item.description">明细：{{ item.description }}</p></template>
        <div class="ui-dialog-actions"><button type="button" :disabled="busy" @click="stage=1">返回修改</button>
          <button class="primary" type="button" :disabled="busy" @click="submit">{{ busy ? '提交中…' : '确认提交审批' }}</button></div>
      </div>
    </section>
    <section v-else class="ui-section-card"><h2>申请已提交</h2><p>审批编号：{{ success?.approval_no }}</p>
      <button class="primary" type="button" @click="navigate('approvals')">查看我的申请</button></section>
  </div>
</template>

<style scoped>
.expense-form-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 1rem; }
.expense-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:1rem; flex-wrap:wrap; }
.expense-tools { display:flex; align-items:center; gap:.5rem; flex-wrap:wrap; }
.expense-upload { display:inline-flex; align-items:center; position:relative; padding:.55rem .8rem; border:1px solid #d6e3df; border-radius:.5rem; cursor:pointer; }
.expense-upload input { position:absolute; inset:0; width:100%; opacity:0; cursor:pointer; }
.expense-table-scroll,.expense-summary-scroll { max-width:100%; overflow-x:auto; }
.expense-table { width:100%; border-collapse:collapse; min-width:980px; }
.expense-table th,.expense-table td { padding:.55rem; border-bottom:1px solid #e3ebe8; text-align:left; vertical-align:top; }
.expense-table th { white-space:nowrap; color:#516963; }
.expense-table input,.expense-table select { box-sizing:border-box; min-width:100px; max-width:180px; width:100%; padding:.45rem; border:1px solid #d6e3df; border-radius:.4rem; }
.expense-table td .error { display:block; min-width:140px; margin-top:.4rem; font-size:.8rem; }
@media (max-width: 900px) { .expense-form-grid { grid-template-columns: 1fr; } }
</style>
