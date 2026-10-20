<script setup>
import { computed, onMounted, ref } from 'vue';
import { dispatchApi, routeTemplateApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import ErrorState from '../components/ErrorState.vue';
import EmptyState from '../components/EmptyState.vue';
import FormDialog from '../components/FormDialog.vue';
import EntityPicker from '../components/EntityPicker.vue';
import { appendDispatchRows, dispatchBody, updateSelectedAmounts } from './dispatchDraft.js';

const today = new Date();
const businessDate = ref(`${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`);
const templates = ref([]);
const drivers = ref([]);
const vehicles = ref([]);
const templateOptionVersion = ref(0);
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
const templateSearchText = (item) => `${item.stableCode} ${item.name} ${item.customerName} ${item.loadingPlace} ${item.unloadingPlace}`;
function loadTemplateChoices(page = 1, query = '') {
  const term = query.trim().toLowerCase();
  const matches = templates.value.filter((item) => item.enabled && templateSearchText(item).toLowerCase().includes(term));
  return Promise.resolve({ records: matches.slice((page - 1) * 100, page * 100), total: matches.length });
}
const driverOptions = computed(() => drivers.value.filter((item) => item.status === 1 &&
  `${item.name} ${item.phone} ${item.currentVehiclePlateNo || ''}`
    .toLowerCase().includes(driverSearch.value.trim().toLowerCase())));
const chosenTemplate = computed(() => templates.value.find((item) => item.stableCode === selectedTemplateCode.value));
const totalSalary = computed(() => rows.value.reduce((total, item) => total + Number(item.initialSalary || 0), 0));
const invalidRows = computed(() => preview.value?.rows?.filter((item) => item.errors?.length) || []);
function trailerOf(driverId) {
  const driver = drivers.value.find((item) => String(item.id) === String(driverId));
  if (!driver?.currentVehicleId) return '';
  return vehicles.value.find((item) => String(item.id) === String(driver.currentVehicleId))?.currentTrailerPlateNo || '';
}

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
    const [templateList, first, firstVehicles] = await Promise.all([routeTemplateApi.list(), dispatchApi.driverOptions(1, 100),
      dispatchApi.vehicleOptions(1, 100)]);
    templates.value = templateList || [];
    templateOptionVersion.value++;
    const all = [...(first.records || [])];
    for (let page = 2; all.length < Number(first.total || 0); page++) {
      const next = await dispatchApi.driverOptions(page, 100);
      all.push(...(next.records || []));
      if (!next.records?.length) break;
    }
    drivers.value = all;
    const vehicleRows = [...(firstVehicles.records || [])];
    for (let page = 2; vehicleRows.length < Number(firstVehicles.total || 0); page++) {
      const next = await dispatchApi.vehicleOptions(page, 100);
      vehicleRows.push(...(next.records || []));
      if (!next.records?.length) break;
    }
    vehicles.value = vehicleRows;
  } catch (cause) { error.value = `调度候选资料加载失败：${cause.message || '请重试'}`; }
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
      <div class="ui-list-heading"><div><h2>1. 选择日期、线路和司机</h2><p class="muted">选择已有司机后加入待发布表格；同一司机可重复加入。</p></div></div>
      <div class="dispatch-template-panel"><div class="dispatch-top">
        <label>业务日期<input v-model="businessDate" type="date" :disabled="busy" @change="changed" /></label>
        <label>线路模板<EntityPicker :key="templateOptionVersion" v-model="selectedTemplateCode" remote-search :load-page="loadTemplateChoices"
          :option-value="(item) => item.stableCode" :search-text-of="templateSearchText"
          :label-of="(item) => `${item.name} · ${item.customerName} · ${item.loadingPlace} → ${item.unloadingPlace}`"
          placeholder="请选择已启用模板，可搜索编号、客户或装卸货地" :disabled="loading" /></label>
      </div>
      <div v-if="chosenTemplate" class="dispatch-summary">
        <strong>{{ chosenTemplate.name }} · {{ chosenTemplate.customerName }}</strong>
        <span>{{ chosenTemplate.productName || '货物以模板为准' }} · {{ chosenTemplate.loadingPlace }} → {{ chosenTemplate.unloadingPlace }}</span>
        <div class="dispatch-defaults"><span>默认单价 <strong>{{ money(chosenTemplate.defaultUnitPrice) }}</strong></span><span>默认信息费 <strong>{{ money(chosenTemplate.defaultInformationFee) }}</strong></span><span>默认工资 <strong>{{ money(chosenTemplate.defaultDriverSalary) }}</strong></span></div>
      </div></div>
      <div class="dispatch-drivers-panel"><div class="dispatch-driver-toolbar">
        <label class="dispatch-driver-search">搜索已有司机<input v-model.trim="driverSearch" type="search" placeholder="姓名、手机号或车牌" :disabled="loading" /></label>
        <p class="muted">勾选已有司机；可重复加入，外协车牌在任务行填写。</p>
        <button type="button" class="primary" :disabled="!chosenTemplate || !selectedDrivers.length || busy" @click="addRows">加入待发布任务（{{ selectedDrivers.length }} 行）</button>
      </div>
      <div class="dispatch-driver-list" role="group" aria-label="批量选择司机">
        <label v-for="driver in driverOptions" :key="driver.id" :class="{ selected: selectedDrivers.includes(String(driver.id)) }" :title="`${driver.name} · ${driver.driverType === 'OUTSOURCED' ? '外协司机' : '自有司机'}`"><input v-model="selectedDrivers" type="checkbox" :value="String(driver.id)" />
          <span class="dispatch-driver-name">{{ driver.name }}</span><span class="dispatch-driver-type">{{ driver.driverType === 'OUTSOURCED' ? '外协' : '自有' }}</span>
          <small>{{ driver.driverType === 'OUTSOURCED' ? '每行填写本次车牌' : (driver.currentVehiclePlateNo || '当前无绑定车辆') }}<template v-if="trailerOf(driver.id)"> · 车挂 {{ trailerOf(driver.id) }}</template></small>
        </label>
        <p v-if="!loading && !driverOptions.length" class="muted">没有匹配的有效司机，请调整搜索词。</p>
      </div></div>
    </section>

    <section class="ui-section-card dispatch-workspace">
      <div class="dispatch-workspace-head">
      <div class="ui-list-heading"><div><h2>2. 待发布任务</h2><p class="muted">共 {{ rows.length }} 行；同一司机可加入多行，每行独立成任务。</p></div></div>
      <div class="dispatch-footer"><span>待发布 <strong>{{ rows.length }}</strong> 趟 · 初始工资合计 <strong>{{ money(totalSalary) }}</strong></span>
        <button v-if="can('BUTTON_DISPATCH_PUBLISH')" type="button" class="primary" :disabled="busy || !rows.length" @click="previewBatch">{{ busy ? '正在校验…' : '预览并确认发布' }}</button></div>
      </div>
      <div v-if="rows.length" class="dispatch-bulk">
        <label>批量修改<select v-model="bulkField"><option value="actualUnitPrice">实际含税单价</option><option value="actualInformationFee">实际信息费</option><option value="initialSalary">司机工资</option></select></label>
        <label>金额（元）<input v-model="bulkValue" type="number" min="0" step="0.01" /></label>
        <button type="button" :disabled="!can('BUTTON_DISPATCH_PUBLISH') || busy" @click="applyBulk">应用到已勾选 {{ selectedRows.length }} 行</button>
      </div>
      <div v-if="rows.length" class="dispatch-table-wrap">
        <table class="ui-data-table dispatch-table"><thead><tr><th scope="col">选择</th><th scope="col">线路与司机</th><th scope="col">本次车牌</th>
          <th scope="col">实际单价</th><th scope="col">信息费</th><th scope="col">工资</th><th scope="col">备注</th><th scope="col">校验</th><th scope="col">操作</th></tr></thead>
          <tbody><tr v-for="(row, index) in rows" :key="row.localKey">
            <td><input v-model="selectedRows" type="checkbox" :value="row.localKey" :aria-label="`选择第${index + 1}行`" /></td>
            <td><strong>第 {{ index + 1 }} 行 · {{ row.templateName }}</strong><small>{{ row.customerName }} · {{ row.loadingPlace }} → {{ row.unloadingPlace }}</small><small>{{ row.driverName }} · {{ row.driverType === 'OUTSOURCED' ? '外协司机' : '自有司机' }}</small></td>
            <td><label v-if="row.driverType === 'OUTSOURCED'">外协车牌<input v-model.trim="row.outsourcedPlateNo" placeholder="填写本次真实车牌" @input="changed" /></label><span v-else>{{ row.vehiclePlate || '无合法当前车辆' }}<small v-if="trailerOf(row.driverId)">车挂 {{ trailerOf(row.driverId) }}</small></span></td>
            <td><label>元<input v-model="row.actualUnitPrice" type="number" min="0" step="0.01" @input="changed" /></label></td>
            <td><label>元<input v-model="row.actualInformationFee" type="number" min="0" step="0.01" @input="changed" /></label></td>
            <td><label>元<input v-model="row.initialSalary" type="number" min="0" step="0.01" @input="changed" /></label></td>
            <td><input v-model.trim="row.remark" placeholder="选填" maxlength="500" @input="changed" /></td>
            <td><span v-if="row.errors.length" class="dispatch-row-error">{{ row.errors.join('；') }}</span><span v-else class="muted">待正式校验</span></td>
            <td><button type="button" :aria-label="`删除第${index + 1}行`" @click="removeRow(row.localKey)">删除</button></td>
          </tr></tbody></table>
      </div>
      <EmptyState v-else title="尚无待发布任务" description="先选择线路模板和已有司机，再加入工作台。" />
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
.dispatch-picker { display: grid; grid-template-columns: minmax(340px, .85fr) minmax(0, 1.15fr); gap: 12px 16px; margin-bottom: 12px; padding: 14px 18px; }
.dispatch-picker .ui-list-heading { margin: 0 0 2px; }
.dispatch-picker > .ui-list-heading { grid-column:1 / -1; }
.dispatch-template-panel, .dispatch-drivers-panel { display:grid; align-content:start; gap:9px; min-width:0; }
.dispatch-top { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 10px; align-items:end; }
.dispatch-top label:last-child { grid-column:1 / -1; }
.dispatch-top label, .dispatch-driver-search, .dispatch-bulk label { display: grid; gap: 6px; font-weight: 600; }
.dispatch-summary { display: grid; grid-template-columns: 1fr; gap: 4px; align-items: center; margin: 0; padding: 8px 11px; background: var(--ui-surface-muted, #f5faf8); border: 1px solid var(--ui-border); border-left: 4px solid var(--ui-primary, #087563); border-radius: 8px; }
.dispatch-summary > strong, .dispatch-summary > span { overflow-wrap: anywhere; }
.dispatch-defaults { display: flex; flex-wrap: wrap; justify-content: start; gap: 5px; }
.dispatch-defaults span { padding: 5px 7px; border-radius: 6px; background: #fff; border: 1px solid var(--ui-border); white-space: nowrap; font-size: 12px; }
.dispatch-driver-toolbar { display: flex; gap: 10px; align-items: end; }
.dispatch-driver-toolbar p { display: none; }
.dispatch-driver-toolbar .primary { flex: none; min-height: 38px; }
.dispatch-driver-search { flex: 1; min-width: 170px; }
.dispatch-driver-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; max-height: 158px; overflow-y: auto; margin: 0; padding: 2px; }
.dispatch-driver-list label { display: grid; grid-template-columns: 18px minmax(0, 1fr) auto; gap: 3px 8px; align-items: center; min-width: 0; min-height: 53px; padding: 8px 10px; border: 1px solid var(--ui-border); border-radius: 8px; cursor: pointer; background:#fff; }
.dispatch-driver-list label:hover, .dispatch-driver-list label.selected { border-color: var(--ui-primary, #087563); background: var(--ui-surface-muted, #eef7f2); }
.dispatch-driver-list .dispatch-driver-name { min-width:0; overflow-wrap:anywhere; line-height: 1.35; font-weight: 700; }
.dispatch-driver-list small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dispatch-driver-list .dispatch-driver-type { padding: 1px 5px; border-radius: 4px; background: #eef5f0; color: #27604b; font-size: 11px; }
.dispatch-driver-list small { grid-column: 2 / 4; color: var(--ui-muted); }
.dispatch-bulk { display: flex; flex-wrap: wrap; align-items: end; gap: 8px; margin: 7px 0 10px; }
.dispatch-workspace { padding-top:12px; }
.dispatch-workspace-head { display:flex; justify-content:space-between; align-items:center; gap:12px; position:sticky; top:0; z-index:4; padding:6px 0 9px; background:#fff; border-bottom:1px solid var(--ui-border); }
.dispatch-workspace-head .ui-list-heading { margin:0; }
.dispatch-workspace-head .ui-list-heading p { margin:2px 0 0; }
.dispatch-bulk label { min-width: 150px; }
.dispatch-table-wrap { max-width: 100%; overflow-x: auto; border: 1px solid var(--ui-border); border-radius: 8px; }
.dispatch-table { min-width: 1260px; }
.dispatch-table td { vertical-align: top; }
.dispatch-table td:nth-child(2) { min-width: 235px; }
.dispatch-table td:nth-child(2) small { display: block; margin-top: 4px; color: var(--ui-muted); }
.dispatch-table input { min-width: 90px; width: 100%; box-sizing: border-box; }
.dispatch-table input[type=checkbox] { min-width: 0; width: auto; }
.dispatch-row-error { display:block; max-width:220px; color: #a3342f; line-height:1.45; white-space:normal; }
.dispatch-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0; padding: 6px 7px 6px 12px; background: #f4faf6; border: 1px solid var(--ui-border); border-radius: 8px; white-space:nowrap; color:#426153; font-size:12px; }
.dispatch-footer strong { color:#174f41; font-size:14px; font-variant-numeric:tabular-nums; }
.dispatch-footer .primary { min-height:38px; }
@media (max-width: 1150px) { .dispatch-workspace-head { flex-wrap:wrap; } }
@media (max-width: 1150px) { .dispatch-picker { grid-template-columns:1fr; } .dispatch-driver-list { grid-template-columns:repeat(3,minmax(0,1fr)); } }
@media (max-width: 900px) { .dispatch-top { grid-template-columns: 1fr 1fr; } .dispatch-top label:last-child { grid-column:1 / -1; } .dispatch-driver-toolbar { flex-wrap: wrap; } .dispatch-footer { flex-wrap: wrap; } .dispatch-driver-list { grid-template-columns:repeat(2,minmax(0,1fr)); } }
</style>
