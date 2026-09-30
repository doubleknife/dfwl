<script setup>
import { computed, onMounted, ref } from 'vue';
import { dispatchApi, masterApi, routeTemplateApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import ErrorState from '../components/ErrorState.vue';
import EmptyState from '../components/EmptyState.vue';
import FormDialog from '../components/FormDialog.vue';
import { appendDispatchRows, dispatchBody, updateSelectedAmounts } from './dispatchDraft.js';

const today = new Date();
const businessDate = ref(`${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`);
const templates = ref([]);
const drivers = ref([]);
const templateSearch = ref('');
const driverSearch = ref('');
const selectedTemplateCode = ref('');
const selectedDrivers = ref([]);
const rows = ref([]);
const selectedRows = ref([]);
const bulkField = ref('actualUnitPrice');
const bulkValue = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const preview = ref(null);
const confirming = ref(false);
const published = ref(null);
const requestKey = ref('');
let sequence = 0;
const money = (value) => `¥${Number(value ?? 0).toFixed(2)}`;
const templateOptions = computed(() => templates.value.filter((item) => item.enabled &&
  `${item.stableCode} ${item.name} ${item.customerName} ${item.loadingPlace} ${item.unloadingPlace}`
    .toLowerCase().includes(templateSearch.value.trim().toLowerCase())));
const driverOptions = computed(() => drivers.value.filter((item) => item.status === 1 &&
  `${item.name} ${item.phone} ${item.currentVehiclePlateNo || ''}`
    .toLowerCase().includes(driverSearch.value.trim().toLowerCase())));
const chosenTemplate = computed(() => templates.value.find((item) => item.stableCode === selectedTemplateCode.value));
const totalSalary = computed(() => rows.value.reduce((total, item) => total + Number(item.initialSalary || 0), 0));
const invalidRows = computed(() => preview.value?.rows?.filter((item) => item.errors?.length) || []);

function changed() {
  preview.value = null;
  confirming.value = false;
  requestKey.value = '';
  published.value = null;
  for (const row of rows.value) row.errors = [];
}

async function loadOptions() {
  loading.value = true; error.value = '';
  try {
    const [templateList, first] = await Promise.all([routeTemplateApi.list(), masterApi.drivers(1, 100)]);
    templates.value = templateList || [];
    const all = [...(first.records || [])];
    for (let page = 2; all.length < Number(first.total || 0); page++) {
      const next = await masterApi.drivers(page, 100);
      all.push(...(next.records || []));
      if (!next.records?.length) break;
    }
    drivers.value = all;
  } catch (cause) { error.value = cause.message || '调度资料加载失败'; }
  finally { loading.value = false; }
}

function addRows() {
  if (!chosenTemplate.value || !selectedDrivers.value.length) return;
  const chosen = selectedDrivers.value.map((id) => drivers.value.find((item) => String(item.id) === String(id)))
    .filter(Boolean);
  rows.value = appendDispatchRows(rows.value, chosenTemplate.value, chosen, sequence + 1);
  sequence += chosen.length;
  selectedDrivers.value = [];
  changed();
}
function removeRow(key) {
  rows.value = rows.value.filter((row) => row.localKey !== key);
  selectedRows.value = selectedRows.value.filter((item) => item !== key);
  changed();
}
function applyBulk() {
  if (!selectedRows.value.length || !/^\d+(\.\d{1,2})?$/.test(String(bulkValue.value))) {
    error.value = '请勾选待修改行，并输入非负且最多两位小数的金额'; return;
  }
  rows.value = updateSelectedAmounts(rows.value, selectedRows.value, bulkField.value, bulkValue.value);
  error.value = ''; changed();
}
function body() {
  return dispatchBody(businessDate.value, rows.value);
}
async function previewBatch() {
  if (!rows.value.length || !businessDate.value || busy.value) return;
  busy.value = true; error.value = ''; published.value = null;
  try {
    preview.value = await dispatchApi.preview(body());
    for (let i = 0; i < rows.value.length; i++) rows.value[i].errors = preview.value.rows?.[i]?.errors || [];
    if (preview.value.valid) {
      if (!requestKey.value) requestKey.value = crypto.randomUUID();
      confirming.value = true;
    } else error.value = `有 ${invalidRows.value.length} 行未通过校验，请按行修正后重新预览`;
  } catch (cause) { error.value = cause.message || '调度预览失败'; }
  finally { busy.value = false; }
}
async function confirmBatch() {
  if (!preview.value?.valid || busy.value) return;
  busy.value = true; error.value = '';
  try {
    published.value = await dispatchApi.confirm({ previewId: preview.value.previewId,
      requestKey: requestKey.value, batch: body() });
    confirming.value = false;
    rows.value = []; selectedRows.value = []; selectedDrivers.value = [];
    preview.value = null;
  } catch (cause) {
    error.value = cause.message || '正式发布失败；本批未发布，请检查资料并重试';
    confirming.value = false;
  } finally { busy.value = false; }
}
onMounted(loadOptions);
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="调度工作台" description="一行生成一趟真实运输任务；整批正式校验，任一行失败则全部不发布。">
      <template #actions><button type="button" :disabled="loading" @click="loadOptions">刷新资料</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="loadOptions" />
    <section v-if="published" class="ui-section-card" role="status">
      <h2>整批发布成功 · {{ published.rows?.length || 0 }} 趟</h2>
      <p>每行均生成独立运输任务；网络重试使用同一确认请求，不会重复创建。</p>
      <ul><li v-for="item in published.rows" :key="item.rowNo">第 {{ item.rowNo }} 行：{{ item.taskNo }}</li></ul>
    </section>
    <section class="ui-section-card dispatch-picker">
      <h2>1. 选择日期、线路和司机</h2>
      <div class="dispatch-top">
        <label>业务日期<input v-model="businessDate" type="date" :disabled="busy" @change="changed" /></label>
        <label>搜索线路模板<input v-model.trim="templateSearch" type="search" placeholder="模板编号、客户或装卸货地" :disabled="loading" /></label>
        <label>线路模板<select v-model="selectedTemplateCode" :disabled="loading"><option value="">请选择已启用模板</option>
          <option v-for="item in templateOptions" :key="item.id" :value="item.stableCode">{{ item.name }} · {{ item.customerName }} · {{ item.loadingPlace }} → {{ item.unloadingPlace }}</option>
        </select></label>
      </div>
      <div v-if="chosenTemplate" class="dispatch-summary">
        <strong>{{ chosenTemplate.customerName }} · {{ chosenTemplate.loadingPlace }} → {{ chosenTemplate.unloadingPlace }}</strong>
        <span>默认单价 {{ money(chosenTemplate.defaultUnitPrice) }} / 信息费 {{ money(chosenTemplate.defaultInformationFee) }} / 工资 {{ money(chosenTemplate.defaultDriverSalary) }}</span>
      </div>
      <label class="dispatch-driver-search">搜索已有司机<input v-model.trim="driverSearch" type="search" placeholder="姓名、手机号或车牌" :disabled="loading" /></label>
      <div class="dispatch-driver-list" role="group" aria-label="批量选择司机">
        <label v-for="driver in driverOptions" :key="driver.id"><input v-model="selectedDrivers" type="checkbox" :value="String(driver.id)" />
          <span>{{ driver.name }} · {{ driver.driverType === 'OUTSOURCED' ? '外协司机' : '自有司机' }}</span>
          <small>{{ driver.driverType === 'OUTSOURCED' ? '每行填写本次车牌' : (driver.currentVehiclePlateNo || '当前无绑定车辆') }}</small>
        </label>
        <p v-if="!loading && !driverOptions.length" class="muted">没有匹配的有效司机，请调整搜索词。</p>
      </div>
      <button type="button" class="primary" :disabled="!chosenTemplate || !selectedDrivers.length || busy" @click="addRows">加入待发布任务（{{ selectedDrivers.length }} 行）</button>
    </section>

    <section class="ui-section-card">
      <div class="ui-list-heading"><div><h2>2. 待发布任务</h2><p class="muted">共 {{ rows.length }} 行；同一司机可加入多行，每行独立成任务。</p></div></div>
      <div v-if="rows.length" class="dispatch-bulk">
        <label>批量修改<select v-model="bulkField"><option value="actualUnitPrice">实际含税单价</option><option value="actualInformationFee">实际信息费</option><option value="initialSalary">司机工资</option></select></label>
        <label>金额（元）<input v-model="bulkValue" type="number" min="0" step="0.01" /></label>
        <button type="button" :disabled="!can('dispatch:batch') || busy" @click="applyBulk">应用到已勾选 {{ selectedRows.length }} 行</button>
      </div>
      <div v-if="rows.length" class="dispatch-table-wrap">
        <table class="ui-data-table dispatch-table"><thead><tr><th scope="col">选择</th><th scope="col">线路与司机</th><th scope="col">本次车牌</th>
          <th scope="col">实际单价</th><th scope="col">信息费</th><th scope="col">工资</th><th scope="col">备注</th><th scope="col">校验</th><th scope="col">操作</th></tr></thead>
          <tbody><tr v-for="(row, index) in rows" :key="row.localKey">
            <td><input v-model="selectedRows" type="checkbox" :value="row.localKey" :aria-label="`选择第${index + 1}行`" /></td>
            <td><strong>{{ row.templateName }}</strong><small>{{ row.customerName }} · {{ row.loadingPlace }} → {{ row.unloadingPlace }}</small><small>{{ row.driverName }} · {{ row.driverType === 'OUTSOURCED' ? '外协司机' : '自有司机' }}</small></td>
            <td><label v-if="row.driverType === 'OUTSOURCED'">外协车牌<input v-model.trim="row.outsourcedPlateNo" placeholder="填写本次真实车牌" @input="changed" /></label><span v-else>{{ row.vehiclePlate || '无合法当前车辆' }}</span></td>
            <td><label>元<input v-model="row.actualUnitPrice" type="number" min="0" step="0.01" @input="changed" /></label></td>
            <td><label>元<input v-model="row.actualInformationFee" type="number" min="0" step="0.01" @input="changed" /></label></td>
            <td><label>元<input v-model="row.initialSalary" type="number" min="0" step="0.01" @input="changed" /></label></td>
            <td><input v-model.trim="row.remark" placeholder="选填" maxlength="500" @input="changed" /></td>
            <td><span v-if="row.errors.length" class="dispatch-row-error">{{ row.errors.join('；') }}</span><span v-else class="muted">待正式校验</span></td>
            <td><button type="button" :aria-label="`删除第${index + 1}行`" @click="removeRow(row.localKey)">删除</button></td>
          </tr></tbody></table>
      </div>
      <EmptyState v-else title="尚无待发布任务" description="先选择线路模板和已有司机，再加入工作台。" />
      <div class="dispatch-footer"><span>共 {{ rows.length }} 趟 · 初始工资合计 {{ money(totalSalary) }}</span>
        <button v-if="can('dispatch:batch')" type="button" class="primary" :disabled="busy || !rows.length" @click="previewBatch">{{ busy ? '正在校验…' : '预览并确认发布' }}</button></div>
    </section>
    <FormDialog :open="confirming" title="确认整批发布" :busy="busy" @close="confirming = false">
      <p>业务日期 {{ businessDate }}，共 {{ rows.length }} 趟，初始工资合计 {{ money(totalSalary) }}。</p>
      <p>确认后服务端会重新检查全部行；若任何一行失效，则整批零发布。</p>
      <ol><li v-for="(row, index) in rows" :key="row.localKey">第 {{ index + 1 }} 行：{{ row.templateName }} · {{ row.driverName }} · {{ row.driverType === 'OUTSOURCED' ? row.outsourcedPlateNo : row.vehiclePlate }} · 单价 {{ money(row.actualUnitPrice) }} / 信息费 {{ money(row.actualInformationFee) }} / 工资 {{ money(row.initialSalary) }}</li></ol>
      <div class="ui-dialog-actions"><button type="button" :disabled="busy" @click="confirming = false">返回修改</button><button type="button" class="primary" :disabled="busy" @click="confirmBatch">{{ busy ? '发布中…' : '确认整批发布' }}</button></div>
    </FormDialog>
  </main>
</template>

<style scoped>
.dispatch-picker { margin-bottom: 18px; }
.dispatch-top { display: grid; grid-template-columns: 190px minmax(200px, 1fr) minmax(300px, 1.7fr); gap: 12px; }
.dispatch-top label, .dispatch-driver-search, .dispatch-bulk label { display: grid; gap: 6px; font-weight: 600; }
.dispatch-summary { display: grid; gap: 4px; margin: 16px 0; padding: 14px; background: var(--ui-surface-muted, #f5faf8); border-radius: 8px; }
.dispatch-driver-search { max-width: 400px; margin-top: 14px; }
.dispatch-driver-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(225px, 1fr)); gap: 8px; max-height: 230px; overflow-y: auto; margin: 12px 0; }
.dispatch-driver-list label { display: grid; grid-template-columns: auto 1fr; gap: 2px 8px; align-items: center; padding: 10px; border: 1px solid var(--ui-border); border-radius: 8px; }
.dispatch-driver-list small { grid-column: 2; color: var(--ui-muted); }
.dispatch-bulk { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 16px 0; }
.dispatch-bulk label { min-width: 150px; }
.dispatch-table-wrap { max-width: 100%; overflow-x: auto; border: 1px solid var(--ui-border); border-radius: 8px; }
.dispatch-table { min-width: 1260px; }
.dispatch-table td { vertical-align: top; }
.dispatch-table td:nth-child(2) { min-width: 220px; }
.dispatch-table td:nth-child(2) small { display: block; margin-top: 4px; color: var(--ui-muted); }
.dispatch-table input { min-width: 90px; width: 100%; box-sizing: border-box; }
.dispatch-table input[type=checkbox] { min-width: 0; width: auto; }
.dispatch-row-error { color: #a3342f; }
.dispatch-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 16px; }
@media (max-width: 900px) { .dispatch-top { grid-template-columns: 1fr; } .dispatch-footer { flex-wrap: wrap; } }
</style>
