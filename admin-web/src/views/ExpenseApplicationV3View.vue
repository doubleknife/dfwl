<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { v3ApprovalApi } from '../api/modules.js';
import { navigate } from '../router/routes.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';

const stage = ref(1);
const scope = ref('DAILY');
const mode = ref('SINGLE');
const templateId = ref('');
const templates = ref([]);
const options = ref({ drivers: [], vehicles: [] });
const candidates = ref([]);
const file = ref(null);
const preview = ref(null);
const busy = ref(false);
const error = ref('');
const requestKey = ref(crypto.randomUUID());
const success = ref(null);
const item = reactive({ businessDate: new Date().toLocaleDateString('sv-SE'), driverId: '',
  vehicleId: '', targetTaskId: '', amount: '', description: '', receiptNo: '',
  invoiceNo: '', remark: '', stationName: '' });
const expenseTemplates = computed(() => templates.value.filter((entry) => entry.business_type === 'EXPENSE'));
const selectedTemplate = computed(() => expenseTemplates.value.find((entry) => String(entry.id) === String(templateId.value)));
const total = computed(() => mode.value === 'EXCEL' ? Number(preview.value?.total || 0) : Number(item.amount || 0));
const money = (value) => `¥${Number(value || 0).toFixed(2)}`;
const chosenDriver = computed(() => options.value.drivers.find((row) => String(row.id) === String(item.driverId)));
const chosenVehicle = computed(() => options.value.vehicles.find((row) => String(row.id) === String(item.vehicleId)));
const chosenTask = computed(() => candidates.value.find((row) => String(row.taskId) === String(item.targetTaskId)));
const canAdvance = computed(() => {
  if (!templateId.value) return false;
  if (mode.value === 'EXCEL') return scope.value === 'DAILY' && preview.value && preview.value.errorCount === 0;
  return item.businessDate && item.driverId && item.vehicleId && Number(item.amount) > 0
    && (scope.value === 'DAILY' || item.targetTaskId);
});
function resetIdempotency() { requestKey.value = crypto.randomUUID(); }
watch([scope, mode, templateId], () => { stage.value = 1; preview.value = null; file.value = null; resetIdempotency(); });
watch(scope, (value) => { if (value === 'TASK') mode.value = 'SINGLE'; });
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
  file.value = event.target.files?.[0] || null; preview.value = null; error.value = '';
  if (!file.value) return;
  busy.value = true;
  try { preview.value = await v3ApprovalApi.dailyPreview(file.value); }
  catch (cause) { error.value = cause.message || '预览失败，请检查文件格式'; }
  finally { busy.value = false; }
}
function next() {
  error.value = '';
  if (!canAdvance.value) { error.value = '请先补齐必填信息并修正所有错误行'; return; }
  if (mode.value === 'SINGLE' && (!Number.isFinite(Number(item.amount))
    || Number(item.amount) <= 0 || !/^\d+(\.\d{1,2})?$/.test(String(item.amount)))) {
    error.value = '金额必须大于 0 且最多两位小数'; return;
  }
  stage.value = 2;
}
async function submit() {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try {
    let result;
    if (mode.value === 'EXCEL') result = await v3ApprovalApi.dailySubmit(file.value, Number(templateId.value), requestKey.value);
    else result = await v3ApprovalApi.create({
      templateId: Number(templateId.value), applicationScope: scope.value, requestKey: requestKey.value,
      items: [{ businessDate: item.businessDate, driverId: Number(item.driverId),
        vehicleId: Number(item.vehicleId), targetTaskId: scope.value === 'TASK' ? Number(item.targetTaskId) : null,
        amount: item.amount, description: item.description || null, receiptNo: item.receiptNo || null,
        invoiceNo: item.invoiceNo || null, remark: item.remark || null, stationName: item.stationName || null }]
    });
    success.value = result.approval; stage.value = 3;
  } catch (cause) { error.value = cause.message || '提交失败，已保留填写信息，请重试'; }
  finally { busy.value = false; }
}
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="发起费用申请" description="选择费用归属来源，核对明细后提交审批；日常费用可使用固定 Excel 批量申请。" />
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
        <div v-if="scope === 'DAILY'" class="ui-subtabs"><button type="button" :class="{active:mode==='SINGLE'}" @click="mode='SINGLE'">单笔申请</button>
          <button type="button" :class="{active:mode==='EXCEL'}" @click="mode='EXCEL'">固定 Excel 批量</button></div>
      </section>
      <section v-if="mode === 'SINGLE' || scope === 'TASK'" class="ui-section-card">
        <h2>2. 填写费用明细</h2>
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
        <h2>2. 上传固定模板</h2>
        <p>一个文件只能包含一个费用大类。所有明细共用一次审批，任一错误行都会阻止整批提交。</p>
        <button type="button" @click="downloadTemplate">下载系统固定模板</button>
        <label class="ui-form-field">费用明细 Excel <input type="file" accept=".xlsx" @change="chooseFile" /></label>
        <p v-if="preview">共 {{ preview.rows.length }} 行 · 金额合计 {{ money(preview.total) }} · 错误 {{ preview.errorCount }} 行</p>
        <DataTable v-if="preview" :rows="preview.rows"
          :columns="[{key:'rowNo',label:'行号'},{key:'businessDate',label:'日期'},{key:'plateNo',label:'车牌'},
            {key:'driverPhone',label:'司机'},{key:'amount',label:'金额',numeric:true},
            {key:'description',label:'明细'},{key:'errors',label:'校验'}]">
          <template #errors="{value}"><span :class="{error:value.length}">{{ value.length ? value.join('；') : '通过' }}</span></template>
        </DataTable>
      </section>
      <div class="ui-dialog-actions"><button class="primary" type="button" :disabled="busy || !canAdvance" @click="next">核对申请摘要</button></div>
    </div>
    <section v-else-if="stage === 2" class="ui-workflow">
      <div class="ui-section-card"><h2>3. 提交前核对</h2>
        <p>申请来源：{{ scope === 'DAILY' ? '日常费用 · 审批后暂不归任务' : '任务费用' }}</p>
        <p>费用大类：{{ selectedTemplate?.name }}</p>
        <p v-if="mode === 'EXCEL'">明细：{{ preview?.rows?.length }} 条，合计 {{ money(total) }}；一审通过后逐条形成正式费用。</p>
        <template v-else><p>司机：{{ chosenDriver?.name }} · 车辆：{{ chosenVehicle?.plateNo }}</p>
          <p v-if="scope === 'TASK'">任务：{{ chosenTask?.taskNo }}</p>
          <p>日期：{{ item.businessDate }} · 金额：{{ money(total) }}</p>
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
@media (max-width: 900px) { .expense-form-grid { grid-template-columns: 1fr; } }
</style>
