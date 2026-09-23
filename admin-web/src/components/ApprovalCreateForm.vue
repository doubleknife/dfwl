<script setup>
import { computed, onMounted, ref } from 'vue';
import { approvalApi, approvalTemplateApi, attachmentApi, masterApi, routeApi } from '../api/modules.js';
import { authStore } from '../stores/auth.js';
import {
  allowedAttributions, expensePayload, isEnergy, loadAllPages, mapExcelRows, newItem, visibleTemplates
} from '../views/approvalApplicationModel.js';

const emit = defineEmits(['submitted', 'cancel']);
const templates = ref([]);
const selectedId = ref('');
const attribution = ref('');
const routeId = ref('');
const routes = ref([]);
const vehicles = ref([]);
const drivers = ref([]);
const items = ref([]);
const evidence = ref([]);
const busy = ref(false);
const loading = ref(false);
const error = ref('');
const notice = ref('');
const excelInput = ref(null);
let serial = 0;

const selected = computed(() => templates.value.find((item) => String(item.templateId) === selectedId.value));
const attributions = computed(() => allowedAttributions(selected.value));
const canManual = computed(() => selected.value?.allowedInputModes?.includes('SINGLE'));
const canExcel = computed(() => selected.value?.allowedInputModes?.includes('EXCEL'));

function nextItemNo() { return `I${String(++serial).padStart(6, '0')}`; }
function report(caught) {
  error.value = `${caught.message || '操作失败'}${caught.requestId ? `（requestId: ${caught.requestId}）` : ''}`;
}
async function loadAvailable() {
  loading.value = true;
  error.value = '';
  try { templates.value = visibleTemplates(await approvalTemplateApi.available()); }
  catch (caught) { report(caught); }
  finally { loading.value = false; }
}
async function selectTemplate() {
  items.value = [];
  evidence.value = [];
  routeId.value = '';
  attribution.value = attributions.value.length === 1 ? attributions.value[0] : '';
  serial = 0;
  error.value = '';
  notice.value = '';
  if (excelInput.value) excelInput.value.value = '';
  if (selected.value?.category !== 'EXPENSE') return;
  loading.value = true;
  try {
    [vehicles.value, drivers.value] = await Promise.all([
      loadAllPages(masterApi.vehicles), loadAllPages(masterApi.drivers)
    ]);
  } catch (caught) { report(caught); }
  finally { loading.value = false; }
  if (attribution.value === 'ROUTE') await loadRoutes();
}
async function loadRoutes() {
  if (attribution.value !== 'ROUTE') { routeId.value = ''; return; }
  loading.value = true;
  try {
    routes.value = await loadAllPages(routeApi.list);
  } catch (caught) { report(caught); }
  finally { loading.value = false; }
}
function addItem() {
  if (!canManual.value || !attribution.value || attribution.value === 'ROUTE' && !routeId.value) return;
  items.value.push(newItem(nextItemNo()));
}
async function importExcel(event) {
  const file = event.target.files?.[0];
  if (!file || !canExcel.value) return;
  error.value = '';
  try {
    if (!attribution.value || attribution.value === 'ROUTE' && !routeId.value) throw new Error('请先选择费用归属和线路');
    if (!/\.xlsx$/i.test(file.name)) throw new Error('请选择 .xlsx 文件');
    // Only the selected File is parsed here; it is never sent to an API.
    const { readSheet } = await import('read-excel-file/browser');
    const rows = await readSheet(file);
    items.value.push(...mapExcelRows(rows, vehicles.value, drivers.value, nextItemNo));
    notice.value = `已在浏览器本地解析 ${rows.length - 1} 行，可继续编辑；Excel 原文件不会上传。`;
  } catch (caught) { report(caught); }
  finally { event.target.value = ''; }
}
async function uploadImage(event) {
  const files = Array.from(event.target.files || []);
  if (!files.length) return;
  error.value = '';
  loading.value = true;
  try {
    for (const file of files) {
      if (!file.type.startsWith('image/')) throw new Error('申请依据只接受图片');
      const body = new FormData();
      body.append('ownerType', 'APPROVAL_UPLOAD');
      body.append('ownerId', String(authStore.user?.id));
      body.append('purpose', 'APPROVAL_APPLICATION');
      body.append('file', file);
      const uploaded = await attachmentApi.upload(body);
      evidence.value.push({ id: uploaded.id, name: file.name });
    }
  } catch (caught) { report(caught); }
  finally { loading.value = false; event.target.value = ''; }
}
async function submit() {
  if (busy.value) return;
  error.value = '';
  busy.value = true;
  try {
    const body = expensePayload(selected.value, attribution.value, routeId.value, items.value,
      evidence.value.map((file) => file.id));
    const created = await approvalApi.create(body);
    const createdId = created?.id || created?.instanceId;
    selectedId.value = '';
    attribution.value = '';
    routeId.value = '';
    items.value = [];
    evidence.value = [];
    if (excelInput.value) excelInput.value.value = '';
    notice.value = `审批申请已提交${createdId ? `（实例 #${createdId}）` : ''}。`;
    emit('submitted');
  } catch (caught) { report(caught); }
  finally { busy.value = false; }
}

onMounted(loadAvailable);
</script>

<template>
  <div class="approval-create">
    <div class="row-actions"><button type="button" :disabled="loading || busy" @click="loadAvailable">刷新可发起模板</button><button type="button" @click="emit('cancel')">返回审批列表</button></div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="!loading && !templates.length" class="muted">当前没有可发起的审批模板，请联系管理员配置并发布模板。</p>
    <label v-else>审批模板
      <select v-model="selectedId" aria-label="审批模板" :disabled="busy" @change="selectTemplate">
        <option value="">请选择模板</option>
        <option v-for="template in templates" :key="template.templateId" :value="String(template.templateId)">{{ template.flowName }} · {{ template.category }} · V{{ template.versionNo }}</option>
      </select>
    </label>
    <template v-if="selected?.category === 'OTHER'">
      <p class="muted">当前 OTHER 契约只定义自由形式 businessSnapshot，没有可供填写的正式业务字段；暂不能在管理端构成有意义的申请。请先明确 OTHER 申请字段契约。</p>
    </template>
    <template v-if="selected?.category === 'EXPENSE'">
      <p>费用类型：{{ selected.expenseType }}；录入方式：{{ selected.allowedInputModes.join(' + ') }}</p>
      <label>费用归属
        <select v-model="attribution" aria-label="费用归属" :disabled="attributions.length === 1 || busy" @change="loadRoutes">
          <option value="">请选择归属</option>
          <option v-for="type in attributions" :key="type" :value="type">{{ type === 'ROUTE' ? '线路费用' : '日常费用' }}</option>
        </select>
      </label>
      <label v-if="attribution === 'ROUTE'">关联线路
        <select v-model="routeId" aria-label="关联线路" :disabled="busy">
          <option value="">请选择线路</option>
          <option v-for="route in routes" :key="route.id" :value="String(route.id)">{{ route.routeNo || `线路 #${route.id}` }} · {{ route.status }}</option>
        </select>
      </label>
      <template v-if="attribution && (attribution === 'DAILY' || routeId)">
        <div class="row-actions">
          <button v-if="canManual" type="button" :disabled="busy" @click="addItem">新增手工明细</button>
          <label v-if="canExcel" class="approval-file-label">导入本地 Excel（车辆 / 司机 / 金额 / 日期）
            <input ref="excelInput" type="file" accept=".xlsx" aria-label="导入费用 Excel" :disabled="busy || loading" @change="importExcel" />
          </label>
        </div>
        <p v-if="canExcel" class="muted">Excel 仅在浏览器解析，不上传文件；导入行可以修改或删除。{{ canManual ? '可以继续新增手工明细。' : '当前模板不能从空白新增手工明细。' }}</p>
        <p v-if="!items.length" class="muted">尚无费用明细。</p>
        <div v-for="item in items" :key="item.itemNo" class="approval-item">
          <div class="row-actions"><strong>明细 {{ item.itemNo }}</strong><button type="button" :disabled="busy" @click="items = items.filter(row => row.itemNo !== item.itemNo)">删除明细</button></div>
          <div class="approval-grid">
            <label>车辆<select v-model="item.vehicleId" :aria-label="`${item.itemNo} 车辆`"><option value="">请选择车辆</option><option v-for="vehicle in vehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.plateNo }}</option></select></label>
            <label>司机（可选）<select v-model="item.driverId" :aria-label="`${item.itemNo} 司机`"><option value="">无</option><option v-for="driver in drivers" :key="driver.id" :value="driver.id">{{ driver.name }} · {{ driver.phone }}</option></select></label>
            <label>金额<input v-model.trim="item.amount" type="text" inputmode="decimal" :aria-label="`${item.itemNo} 金额`" /></label>
            <label>业务日期<input v-model="item.businessDate" type="date" :aria-label="`${item.itemNo} 日期`" /></label>
          </div>
          <label>备注（可选）<input v-model="item.remark" :aria-label="`${item.itemNo} 备注`" maxlength="1000" /></label>
          <div v-if="isEnergy(selected.expenseType)" class="approval-grid">
            <label>能源类型<select v-model="item.energyType" :aria-label="`${item.itemNo} 能源类型`"><option value="">请选择</option><option>ELECTRIC</option><option>GAS</option><option>TEMP_ELECTRIC</option></select></label>
            <label>站点 ID（可选）<input v-model.trim="item.stationId" type="text" inputmode="numeric" :aria-label="`${item.itemNo} 站点 ID`" /></label>
            <label>订单号<input v-model.trim="item.orderNo" :aria-label="`${item.itemNo} 订单号`" /></label>
            <label>开始时间<input v-model="item.startTime" type="datetime-local" step="1" :aria-label="`${item.itemNo} 开始时间`" /></label>
            <label>数量<input v-model.trim="item.quantity" type="text" inputmode="decimal" :aria-label="`${item.itemNo} 数量`" /></label>
          </div>
        </div>
      </template>
      <div v-if="selected.evidenceRequirement !== 'NONE'" class="approval-evidence">
        <label>申请图片依据（{{ selected.evidenceRequirement === 'REQUIRED' ? '必填' : '选填' }}）
          <input type="file" accept="image/*" multiple aria-label="上传申请图片依据" :disabled="busy || loading" @change="uploadImage" />
        </label>
        <div v-for="file in evidence" :key="file.id" class="row-actions"><span>{{ file.name }}</span><button type="button" :disabled="busy" @click="evidence = evidence.filter(row => row.id !== file.id)">移除</button></div>
      </div>
      <button type="button" class="primary" :disabled="busy || loading" @click="submit">{{ busy ? '提交中…' : '提交审批申请' }}</button>
    </template>
  </div>
</template>

<style scoped>
.approval-create { display: grid; gap: 14px; }
.approval-create label { display: grid; gap: 5px; }
.approval-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; }
.approval-item { display: grid; gap: 10px; border: 1px solid #d9e1e8; border-radius: 8px; padding: 14px; }
.approval-file-label { min-width: 240px; }
.approval-evidence { display: grid; gap: 8px; }
</style>
