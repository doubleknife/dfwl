<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { chargingApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import ErrorState from '../components/ErrorState.vue';
import EmptyState from '../components/EmptyState.vue';
import { headersAt, mappingComplete, previewChargingOrders, readChargingWorkbook } from './chargingWorkbook.js';

const props = defineProps({ initialTab: { type: String, default: 'import' } });
const tab = ref(props.initialTab);
const companies = ref([]), history = ref([]), result = ref(null);
const historyPage = ref(1), historyTotal = ref(0);
const busy = ref(false), loading = ref(false), error = ref(''), notice = ref('');
const selectedCompanyId = ref(''), importFile = ref(null), importBook = ref(null), preview = ref(null);
const selectedBatch = ref(null);
const companyFile = ref(null), companyBook = ref(null), editingId = ref(null);
const companyName = ref(''), sourceSystem = ref(''), orderNoType = ref('ORDER_NO');
const headerRowNo = ref(1), headers = ref([]);
const mapping = ref({ orderNo: null, plateNo: null, chargedAt: null, amount: null });
const freshSample = ref(false);
const fields = [
  { key: 'orderNo', label: '订单号' }, { key: 'plateNo', label: '车牌号' },
  { key: 'chargedAt', label: '充电时间' }, { key: 'amount', label: '入账金额' }
];
const orderColumns = [
  { key: 'rowNo', label: '原始行号' }, { key: 'orderNo', label: '订单号' },
  { key: 'plateNo', label: '车牌号' }, { key: 'chargedAt', label: '充电时间' },
  { key: 'amount', label: '金额' }, { key: 'status', label: '处理方式' }
];
const selectedCompany = computed(() => companies.value.find((item) => String(item.id) === String(selectedCompanyId.value)));
const ready = computed(() => !!importFile.value && !!preview.value && preview.value.count > 0
  && !preview.value.errors.length && !busy.value);
const formatMoney = (value) => `¥ ${Number(value || 0).toFixed(2)}`;
const fromJson = (value, fallback) => {
  try { return typeof value === 'string' ? JSON.parse(value) : value || fallback; }
  catch { return fallback; }
};

async function load() {
  loading.value = true; error.value = '';
  try {
    companies.value = await chargingApi.companies();
    if (can('charging:view')) await loadHistory();
  } catch (cause) { error.value = cause.message || '充电公司资料加载失败'; }
  finally { loading.value = false; }
}

async function loadHistory() {
  const response = await chargingApi.imports(historyPage.value);
  history.value = response.items;
  historyTotal.value = response.total;
}

async function changeHistoryPage(nextPage) {
  historyPage.value = nextPage;
  error.value = '';
  try { await loadHistory(); }
  catch (cause) { error.value = cause.message || '导入历史加载失败'; }
}

function editCompany(item) {
  editingId.value = item?.id ?? null;
  companyName.value = item?.company_name || '';
  sourceSystem.value = item?.source_system || '';
  orderNoType.value = item?.order_no_type || 'ORDER_NO';
  headerRowNo.value = Number(item?.header_row_no || 1);
  headers.value = fromJson(item?.headers_json, []);
  mapping.value = fromJson(item?.mapping_json, { orderNo: null, plateNo: null, chargedAt: null, amount: null });
  freshSample.value = false; companyBook.value = null; companyFile.value = null; notice.value = '';
}

async function chooseCompanySample(event) {
  companyFile.value = event.target.files?.[0] || null;
  if (!companyFile.value) return;
  error.value = '';
  try {
    companyBook.value = await readChargingWorkbook(companyFile.value);
    headerRowNo.value = companyBook.value.headerRowNo;
    headers.value = companyBook.value.headers;
    // A new sample always invalidates all four choices, even when the labels are unchanged.
    mapping.value = { orderNo: null, plateNo: null, chargedAt: null, amount: null };
    freshSample.value = true;
  } catch (cause) { error.value = cause.message || '样本 Excel 无法读取'; }
}

function correctHeader() {
  if (!companyBook.value) return;
  headers.value = headersAt(companyBook.value.rows, Number(headerRowNo.value));
  mapping.value = { orderNo: null, plateNo: null, chargedAt: null, amount: null };
}

async function saveCompany() {
  if (!mappingComplete(mapping.value, headers.value) || busy.value) return;
  busy.value = true; error.value = ''; notice.value = '';
  try {
    const body = { companyName: companyName.value.trim(), sourceSystem: sourceSystem.value.trim(),
      orderNoType: orderNoType.value.trim(), headerRowNo: Number(headerRowNo.value),
      headers: headers.value, mapping: Object.fromEntries(fields.map(({ key }) => [key, Number(mapping.value[key])])) };
    if (editingId.value) await chargingApi.updateCompany(editingId.value, body);
    else await chargingApi.createCompany(body);
    await load(); editCompany(null); notice.value = '充电公司字段映射已保存并立即生效';
  } catch (cause) { error.value = cause.message || '保存失败'; }
  finally { busy.value = false; }
}

async function chooseImport(event) {
  importFile.value = event.target.files?.[0] || null;
  importBook.value = null; preview.value = null; result.value = null; error.value = '';
  if (!importFile.value) return;
  try { importBook.value = await readChargingWorkbook(importFile.value); }
  catch (cause) { error.value = cause.message || 'Excel 无法读取'; }
}

function buildPreview() {
  error.value = ''; preview.value = null; result.value = null;
  const company = selectedCompany.value;
  if (!company || !importBook.value) return;
  const expectedHeaders = fromJson(company.headers_json, []);
  const expectedMapping = fromJson(company.mapping_json, {});
  const actualHeaders = headersAt(importBook.value.rows, Number(company.header_row_no));
  if (!mappingComplete(expectedMapping, actualHeaders)
      || Object.values(expectedMapping).some((index) =>
        expectedHeaders.find((item) => item.index === index)?.label
          !== actualHeaders.find((item) => item.index === index)?.label)) {
    error.value = 'Excel 表头与当前公司配置不一致，请先更新模板映射，或选择正确的原始文件';
    return;
  }
  try {
    importBook.value.headerRowNo = Number(company.header_row_no);
    importBook.value.headers = actualHeaders;
    preview.value = previewChargingOrders(importBook.value, expectedMapping);
  } catch (cause) { error.value = cause.message || '订单预览失败'; }
}

async function confirm() {
  if (!ready.value) return;
  busy.value = true; error.value = '';
  try {
    const company = selectedCompany.value;
    result.value = await chargingApi.confirm(importFile.value, {
      companyId: Number(company.id), sheetName: importBook.value.sheetName,
      headerRowNo: importBook.value.headerRowNo, headers: importBook.value.headers,
      mapping: fromJson(company.mapping_json, {}),
      orders: preview.value.orders.map(({ status, ...order }) => order),
      count: preview.value.count, zeroCount: preview.value.zeroCount,
      totalAmount: preview.value.totalAmount
    });
    historyPage.value = 1;
    importFile.value = null; importBook.value = null; preview.value = null;
    if (can('charging:view')) {
      try { await loadHistory(); } catch { notice.value = '入账已成功，导入历史暂时无法刷新'; }
    }
  } catch (cause) { error.value = cause.message || '正式导入失败，未确认入账'; }
  finally { busy.value = false; }
}

async function downloadOriginal(batch) {
  error.value = '';
  try {
    const blob = await chargingApi.originalFile(batch.id);
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = batch.original_filename; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) { error.value = cause.message || '原始文件打开失败'; }
}

async function showBatch(batch) {
  error.value = '';
  try { selectedBatch.value = await chargingApi.importDetail(batch.id); }
  catch (cause) { error.value = cause.message || '订单明细加载失败'; }
}

watch(() => props.initialTab, (value) => { tab.value = value; });
onMounted(load);
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="充电公司订单" description="先维护公司字段映射，再核对只读订单预览。只有确认提交才保存原文件并进入正式费用台账。" />
    <ErrorState v-if="error" :message="error" @retry="load" />
    <p v-if="notice" class="ui-section-card">{{ notice }}</p>
    <div class="ui-subtabs">
      <button type="button" :class="{active:tab==='companies'}" @click="tab='companies'">充电公司模板</button>
      <button type="button" :class="{active:tab==='import'}" @click="tab='import'">订单导入</button>
      <button v-if="can('charging:view')" type="button" :class="{active:tab==='history'}" @click="tab='history'">导入历史</button>
    </div>

    <section v-if="tab==='companies'" class="ui-section-card">
      <div class="ui-list-heading"><h2>当前有效字段映射</h2><button v-if="can('charging:company:manage')" type="button" @click="editCompany(null)">新增公司</button></div>
      <DataTable :columns="[{key:'company_name',label:'公司'},{key:'source_system',label:'来源系统'},{key:'updated_at',label:'最近修改'},{key:'actions',label:'操作'}]"
        :rows="companies" :loading="loading" empty-text="尚无充电公司">
        <template #actions="{row}"><button type="button" @click="editCompany(row)">修改映射</button></template>
      </DataTable>
      <div v-if="can('charging:company:manage')" class="charging-form">
        <h2>{{ editingId ? '修改公司与映射' : '新增充电公司' }}</h2>
        <div class="ui-form-grid">
          <label>公司名称<input v-model="companyName" maxlength="128" placeholder="填写正式公司名称" /></label>
          <label>来源系统标识<input v-model="sourceSystem" :disabled="!!editingId" maxlength="64" placeholder="稳定英文编号，如 HONGXI" /></label>
          <label>订单编号类型<input v-model="orderNoType" maxlength="64" placeholder="ORDER_NO" /></label>
          <label>上传 Excel 样本<input type="file" accept=".xls,.xlsx" @change="chooseCompanySample" /></label>
          <label v-if="companyBook">表头所在行<input v-model.number="headerRowNo" type="number" min="1" max="100" @change="correctHeader" /></label>
        </div>
        <p class="muted">上传新样本会清空四项映射，必须重新选择。只修改已有映射时无需上传文件。</p>
        <div v-if="headers.length" class="ui-form-grid">
          <label v-for="field in fields" :key="field.key">{{ field.label }}
            <select v-model.number="mapping[field.key]"><option :value="null">请选择表头</option>
              <option v-for="header in headers" :key="header.index" :value="header.index">第 {{ header.index + 1 }} 列 · {{ header.label }}</option>
            </select>
          </label>
        </div>
        <EmptyState v-else title="尚无表头" description="请上传一份原始 Excel 样本。" />
        <div class="ui-dialog-actions"><button type="button" class="primary" :disabled="busy || !companyName.trim() || !sourceSystem.trim() || !mappingComplete(mapping, headers)" @click="saveCompany">{{ busy ? '正在保存…' : '保存当前映射' }}</button></div>
      </div>
    </section>

    <section v-else-if="tab==='import'" class="ui-section-card">
      <h2>原始订单文件与只读预览</h2>
      <p class="muted">Excel 只作为订单来源；预览不可编辑。异常行必须在原始文件或公司映射中修正，再重新预览。</p>
      <div class="ui-form-grid">
        <label>充电公司<select v-model="selectedCompanyId" @change="preview=null;result=null"><option value="">请选择公司</option><option v-for="company in companies" :key="company.id" :value="String(company.id)">{{ company.company_name }}</option></select></label>
        <label>原始 Excel 文件<input type="file" accept=".xls,.xlsx" @change="chooseImport" /></label>
      </div>
      <div class="ui-dialog-actions"><button type="button" :disabled="!selectedCompany || !importBook || busy" @click="buildPreview">解析并预览</button></div>
      <template v-if="preview">
        <div class="ui-list-heading"><h2>订单预览 · {{ preview.count }} 条 · {{ formatMoney(preview.totalAmount) }}</h2><span>第一个工作表：{{ importBook.sheetName }}</span></div>
        <p>待入账 {{ preview.count - preview.zeroCount }} 条；零元不入账 {{ preview.zeroCount }} 条。零元订单保留原始订单与文件追溯，并参与重复检查。</p>
        <p v-if="preview.ignored.length" class="muted">已排除 {{ preview.ignored.length }} 行汇总数据；不会遇到空行就停止解析。</p>
        <div v-if="preview.errors.length" class="ui-section-card"><h3>请修正 {{ preview.errors.length }} 条异常行</h3><p v-for="item in preview.errors.slice(0,100)" :key="item.rowNo">第 {{ item.rowNo }} 行：{{ item.message }}</p></div>
        <div class="charging-table"><DataTable :columns="orderColumns" :rows="preview.orders.slice(0,100)" empty-text="没有有效订单" /></div>
        <p v-if="preview.orders.length > 100" class="muted">表格显示前 100 条；确认时提交全部 {{ preview.count }} 条，后端逐行核验原始文件。</p>
        <div class="ui-dialog-actions"><button type="button" class="primary" :disabled="!ready || !can('charging:import')" @click="confirm">{{ busy ? '正在正式入账…' : `确认提交 ${preview.count} 条订单` }}</button></div>
      </template>
      <div v-if="result" class="ui-section-card"><h2>导入结果</h2><p>有效订单 {{ result.batch.confirmed_count }} 条 = 正常入账 {{ result.batch.imported_count }} 条 + 零元不入账 {{ result.batch.zero_count }} 条 + 已存在重复 {{ result.batch.duplicate_count }} 条；入账金额 {{ formatMoney(result.batch.imported_amount) }}。</p><p>原始文件已保存，可在导入历史查看。</p></div>
      <EmptyState v-if="!preview && !result" title="等待原始订单文件" description="选择公司和 Excel，预览核对后再确认提交。" />
    </section>

    <section v-else class="ui-section-card">
      <div class="ui-list-heading"><h2>充电订单导入历史</h2><button type="button" @click="load">刷新</button></div>
      <DataTable :columns="[{key:'company_name',label:'公司'},{key:'original_filename',label:'原始文件'},{key:'created_at',label:'导入时间'},{key:'confirmed_count',label:'有效订单'},{key:'imported_count',label:'入账'},{key:'zero_count',label:'零元不入账'},{key:'duplicate_count',label:'重复'},{key:'imported_amount',label:'入账金额'},{key:'actions',label:'操作'}]"
        :rows="history" :loading="loading" empty-text="暂无充电订单导入记录">
        <template #imported_amount="{value}">{{ formatMoney(value) }}</template>
        <template #actions="{row}"><button type="button" @click="showBatch(row)">查看订单</button> <button type="button" @click="downloadOriginal(row)">打开原始 Excel</button></template>
      </DataTable>
      <div class="ui-dialog-actions"><span>共 {{ historyTotal }} 批 · 第 {{ historyPage }} 页</span>
        <button type="button" :disabled="historyPage <= 1" @click="changeHistoryPage(historyPage - 1)">上一页</button>
        <button type="button" :disabled="historyPage * 20 >= historyTotal" @click="changeHistoryPage(historyPage + 1)">下一页</button>
      </div>
      <section v-if="selectedBatch" class="ui-section-card">
        <div class="ui-list-heading"><h2>{{ selectedBatch.batch.company_name }} · 来源订单</h2><button type="button" @click="selectedBatch=null">收起</button></div>
        <p>原始文件：{{ selectedBatch.batch.original_filename }}；有效 {{ selectedBatch.batch.confirmed_count }} = 入账 {{ selectedBatch.batch.imported_count }} + 零元不入账 {{ selectedBatch.batch.zero_count }} + 已存在重复 {{ selectedBatch.batch.duplicate_count }}。</p>
        <div class="charging-table"><DataTable :columns="[{key:'row_no',label:'原始行号'},{key:'original_order_no',label:'订单号'},{key:'plate_no',label:'车牌'},{key:'charged_at',label:'充电时间'},{key:'amount',label:'金额'},{key:'processing_status',label:'处理方式'},{key:'task_no',label:'匹配任务'}]" :rows="selectedBatch.orders" empty-text="本批次没有新增来源订单" /></div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.charging-form { margin-top: 24px; border-top: 1px solid var(--line, #dce8e3); padding-top: 18px; }
.charging-table { overflow-x: auto; max-height: 520px; overflow-y: auto; }
.charging-form h2 { margin-bottom: 16px; }
.ui-form-grid { margin: 16px 0; }
</style>
