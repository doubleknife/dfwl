<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { chargingApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { showErrorDialog } from '../stores/apiErrorDialog.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import ImportTabs from '../components/ImportTabs.vue';
import SummaryCards from '../components/SummaryCards.vue';
import ImportCompletionSummary from '../components/ImportCompletionSummary.vue';
import EmptyState from '../components/EmptyState.vue';
import PaginationBar from '../components/PaginationBar.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ImportFilePicker from '../components/ImportFilePicker.vue';
import ImportResultFilter from '../components/ImportResultFilter.vue';
import { headersAt, mappingComplete, previewChargingOrders, readChargingWorkbook } from './chargingWorkbook.js';
import { formatImportDateTime } from './importResultView.js';

const props = defineProps({ initialTab: { type: String, default: 'import' } });
const companyPage = computed(() => props.initialTab === 'companies');
const permittedTab = (requested) => requested === 'companies' && !can('PAGE_CHARGING_COMPANIES')
  ? (can('BUTTON_CHARGING_IMPORTS_IMPORT') ? 'import' : 'history')
  : requested === 'import' && !can('BUTTON_CHARGING_IMPORTS_IMPORT') ? 'history' : requested;
const tab = ref(permittedTab(props.initialTab));
const companies = ref([]), history = ref([]), result = ref(null);
const confirmFailure = ref(null);
const completion = computed(() => result.value?.batch ? {
  total: Number(result.value.batch.confirmed_count || 0),
  success: Number(result.value.batch.imported_count || 0), failed: 0, failures: []
} : confirmFailure.value);
const historyPage = ref(1), historyPageSize = ref(20), historyTotal = ref(0);
const busy = ref(false), loading = ref(false), error = ref(''), notice = ref('');
const selectedCompanyId = ref(''), importFile = ref(null), importBook = ref(null), preview = ref(null);
const resultFilter = ref('ALL');
const importParsing = ref(false);
let importParseToken = 0;
const selectedBatch = ref(null);
const detailError = ref('');
const sourcePage = ref(1);
const companyFile = ref(null), companyBook = ref(null), editingId = ref(null);
const editorOpen = ref(false), editorMode = ref(''), editorBaseline = ref('');
const cancelPending = ref(false), pendingMode = ref('');
const companyName = ref('');
const headerRowNo = ref(1), headers = ref([]);
const mapping = ref({ orderNo: null, plateNo: null, chargedAt: null, amount: null });
const fields = [
  { key: 'orderNo', label: '订单号' }, { key: 'plateNo', label: '车牌号' },
  { key: 'chargedAt', label: '充电时间' }, { key: 'amount', label: '入账金额' }
];
const companyColumns = [
  { key: 'company_name', label: '公司' }, { key: 'source_system', label: '来源系统' },
  { key: 'updated_at', label: '最近修改' }, { key: 'actions', label: '操作' }
];
const orderColumns = [
  { key: 'rowNo', label: '原始行号' }, { key: 'orderNo', label: '订单号' },
  { key: 'plateNo', label: '车牌号' }, { key: 'chargedAt', label: '充电时间' },
  { key: 'amount', label: '金额' }, { key: 'status', label: '处理方式' }
];
const selectedCompany = computed(() => companies.value.find((item) => String(item.id) === String(selectedCompanyId.value)));
const ready = computed(() => !!importFile.value && !!preview.value && preview.value.count > 0
  && !preview.value.errors.length && !busy.value && !importParsing.value);
const formatMoney = (value) => `¥ ${Number(value || 0).toFixed(2)}`;
const fromJson = (value, fallback) => {
  try { return typeof value === 'string' ? JSON.parse(value) : value || fallback; }
  catch { return fallback; }
};
const editorSnapshot = () => JSON.stringify({ companyName: companyName.value, headerRowNo: headerRowNo.value, headers: headers.value,
  mapping: mapping.value, file: companyFile.value?.name || '' });
const editorDirty = computed(() => editorSnapshot() !== editorBaseline.value);

async function load() {
  loading.value = true; error.value = '';
  try {
    companies.value = await chargingApi.companies();
    if (can('PAGE_CHARGING_IMPORTS')) await loadHistory();
  } catch { /* API 层已弹窗。 */ }
  finally { loading.value = false; }
}

async function loadHistory() {
  const response = await chargingApi.imports(historyPage.value, historyPageSize.value);
  history.value = response.items;
  historyTotal.value = response.total;
}

async function changeHistoryPage(nextPage) {
  historyPage.value = nextPage;
  error.value = '';
  try { await loadHistory(); }
  catch { /* API 层已弹窗。 */ }
}

function editCompany(item) {
  editorOpen.value = true; editorMode.value = item ? '' : 'sample'; cancelPending.value = false; pendingMode.value = '';
  editingId.value = item?.id ?? null;
  companyName.value = item?.company_name || '';
  headerRowNo.value = Number(item?.header_row_no || 1);
  headers.value = fromJson(item?.headers_json, []);
  mapping.value = fromJson(item?.mapping_json, { orderNo: null, plateNo: null, chargedAt: null, amount: null });
  companyBook.value = null; companyFile.value = null; notice.value = '';
  editorBaseline.value = editorSnapshot();
}

function applyEditorMode(mode) {
  editorMode.value = mode;
  if (mode === 'sample') {
    companyBook.value = null; companyFile.value = null; headerRowNo.value = 1; headers.value = [];
    mapping.value = { orderNo: null, plateNo: null, chargedAt: null, amount: null };
  } else {
    const saved = companies.value.find(item => item.id === editingId.value);
    headerRowNo.value = Number(saved?.header_row_no || 1);
    headers.value = fromJson(saved?.headers_json, []);
    mapping.value = fromJson(saved?.mapping_json, { orderNo: null, plateNo: null, chargedAt: null, amount: null });
    companyBook.value = null; companyFile.value = null;
  }
}
function chooseEditorMode(mode) {
  if (mode === editorMode.value || mode === 'mapping' && !editingId.value) return;
  if (editorMode.value && editorDirty.value) pendingMode.value = mode;
  else applyEditorMode(mode);
}
function cancelEditor() {
  if (editorDirty.value) cancelPending.value = true;
  else discardEditor();
}
function discardEditor() {
  const saved = companies.value.find(item => item.id === editingId.value);
  companyName.value = saved?.company_name || '';
  headerRowNo.value = Number(saved?.header_row_no || 1);
  headers.value = fromJson(saved?.headers_json, []);
  mapping.value = fromJson(saved?.mapping_json, { orderNo: null, plateNo: null, chargedAt: null, amount: null });
  companyBook.value = null; companyFile.value = null; editorOpen.value = false;
  editorMode.value = ''; cancelPending.value = false; pendingMode.value = '';
}

function clearCompanySample() {
  companyFile.value = null; companyBook.value = null; headers.value = [];
  mapping.value = { orderNo: null, plateNo: null, chargedAt: null, amount: null };
}
async function chooseCompanySample(file) {
  if (editorMode.value !== 'sample') return;
  companyFile.value = file;
  companyBook.value = null; headers.value = [];
  mapping.value = { orderNo: null, plateNo: null, chargedAt: null, amount: null };
  if (!companyFile.value) return;
  error.value = '';
  try {
    companyBook.value = await readChargingWorkbook(companyFile.value);
    headerRowNo.value = companyBook.value.headerRowNo;
    headers.value = companyBook.value.headers;
    // A new sample always invalidates all four choices, even when the labels are unchanged.
    mapping.value = { orderNo: null, plateNo: null, chargedAt: null, amount: null };
  } catch (cause) { showErrorDialog('读取失败', cause.message || '样本 Excel 无法读取'); }
}

function correctHeader() {
  if (!companyBook.value) return;
  headers.value = headersAt(companyBook.value.rows, Number(headerRowNo.value));
  mapping.value = { orderNo: null, plateNo: null, chargedAt: null, amount: null };
}

async function saveCompany() {
  if (!editorMode.value || editorMode.value === 'sample' && !companyBook.value
      || !mappingComplete(mapping.value, headers.value) || busy.value) return;
  busy.value = true; error.value = ''; notice.value = '';
  try {
    const body = { companyName: companyName.value.trim(), headerRowNo: Number(headerRowNo.value),
      headers: headers.value, mapping: Object.fromEntries(fields.map(({ key }) => [key, Number(mapping.value[key])])) };
    if (editingId.value) await chargingApi.updateCompany(editingId.value, body);
    else await chargingApi.createCompany(body);
    await load(); discardEditor(); notice.value = '充电公司字段映射已保存并立即生效';
  } catch { /* API 层已弹窗。 */ }
  finally { busy.value = false; }
}

function clearImportFile() {
  importParseToken++;
  importParsing.value = false;
  importFile.value = null;
  resultFilter.value = 'ALL';
  importBook.value = null; preview.value = null; result.value = null; confirmFailure.value = null; error.value = '';
}
async function chooseImport(file) {
  const token = ++importParseToken;
  importFile.value = file;
  resultFilter.value = 'ALL';
  importBook.value = null; preview.value = null; result.value = null; confirmFailure.value = null; error.value = '';
  importParsing.value = true;
  try {
    const book = await readChargingWorkbook(file);
    if (token !== importParseToken || tab.value !== 'import') return;
    importBook.value = book; buildPreview();
  } catch (cause) {
    if (token === importParseToken && tab.value === 'import')
      showErrorDialog('上传失败', cause.message || 'Excel 无法读取');
  } finally {
    if (token === importParseToken) importParsing.value = false;
  }
}
function changeImportCompany() {
  if (!selectedCompany.value) { clearImportFile(); return; }
  preview.value = null; result.value = null; confirmFailure.value = null; error.value = '';
  if (importBook.value) buildPreview();
}

function buildPreview() {
  error.value = ''; preview.value = null; result.value = null; confirmFailure.value = null;
  const company = selectedCompany.value;
  if (!company || !importBook.value) return;
  const expectedHeaders = fromJson(company.headers_json, []);
  const expectedMapping = fromJson(company.mapping_json, {});
  const actualHeaders = headersAt(importBook.value.rows, Number(company.header_row_no));
  if (!mappingComplete(expectedMapping, actualHeaders)
      || Object.values(expectedMapping).some((index) =>
        expectedHeaders.find((item) => item.index === index)?.label
          !== actualHeaders.find((item) => item.index === index)?.label)) {
    showErrorDialog('校验失败', 'Excel 表头与当前公司配置不一致，请先更新模板映射，或选择正确的原始文件');
    return;
  }
  try {
    importBook.value.headerRowNo = Number(company.header_row_no);
    importBook.value.headers = actualHeaders;
    preview.value = previewChargingOrders(importBook.value, expectedMapping);
  } catch (cause) { showErrorDialog('预览失败', cause.message || '订单预览失败'); }
}

async function confirm() {
  if (!ready.value) return;
  const submittedCount = preview.value.count;
  busy.value = true; error.value = ''; confirmFailure.value = null;
  try {
    const company = selectedCompany.value;
    const confirmed = await chargingApi.confirm(importFile.value, {
      companyId: Number(company.id), sheetName: importBook.value.sheetName,
      headerRowNo: importBook.value.headerRowNo, headers: importBook.value.headers,
      mapping: fromJson(company.mapping_json, {}),
      orders: preview.value.orders.map(({ status, ...order }) => order),
      count: preview.value.count, zeroCount: preview.value.zeroCount,
      totalAmount: preview.value.totalAmount
    });
    historyPage.value = 1;
    if (tab.value === 'import') {
      result.value = confirmed;
      importFile.value = null; importBook.value = null; preview.value = null;
    }
    if (can('PAGE_CHARGING_IMPORTS')) {
      try { await loadHistory(); } catch { notice.value = '入账已成功，导入历史暂时无法刷新'; }
    }
  } catch (cause) {
    if (tab.value === 'import' && Number(cause?.status) >= 400 && Number(cause?.status) < 500 && ![401, 403].includes(Number(cause.status))) {
      confirmFailure.value = { total: submittedCount, success: 0, failed: submittedCount,
        failures: [{ reason: `${cause.message || '校验失败'}；本批次未入账` }] };
    }
    // API 层已弹窗；网络或服务异常时不推断是否入账。
  }
  finally { busy.value = false; }
}

async function downloadOriginal(batch) {
  error.value = ''; detailError.value = '';
  try {
    const blob = await chargingApi.originalFile(batch.id);
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = batch.original_filename; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch { /* API 层已弹窗。 */ }
}

async function showBatch(batch) {
  error.value = ''; detailError.value = ''; selectedBatch.value = null;
  try { selectedBatch.value = await chargingApi.importDetail(batch.id); sourcePage.value = 1; }
  catch { /* API 层已弹窗。 */ }
}

watch(tab, () => { selectedCompanyId.value = ''; clearImportFile(); });
watch(() => props.initialTab, (value) => { tab.value = permittedTab(value); });
onMounted(load);
</script>

<template>
  <main class="ui-page-container">
    <PageHeader :title="companyPage ? '充电公司模板配置' : '充电订单导入'"
      :description="companyPage ? '维护当前有效的公司字段映射。上传新样本须重新选择四项字段。' : '核对只读订单预览，人工确认后才保存原文件并进入正式费用台账。'" />
    <p v-if="notice" class="ui-section-card">{{ notice }}</p>
    <ImportTabs v-if="!companyPage && can('BUTTON_CHARGING_IMPORTS_IMPORT') && can('PAGE_CHARGING_IMPORTS')" v-model="tab" label="充电订单导入"
      :items="[{key:'import',label:'订单上传与预览'},{key:'history',label:'导入历史'}]" />

    <section v-if="companyPage" class="ui-section-card">
      <div class="ui-list-heading"><h2>当前有效字段映射</h2><button v-if="can('BUTTON_CHARGING_COMPANIES_MANAGE')" type="button" @click="editCompany(null)">新增公司</button></div>
      <p class="muted">每家公司只保留一份当前配置。修改映射无需上传文件；更换样本后必须重新选择订单号、车牌、时间和金额四列。</p>
      <div class="charging-company-layout" :class="{ editing: editorOpen }"><div class="charging-company-list"><DataTable :columns="companyColumns"
        :rows="companies" :loading="loading" empty-text="尚无充电公司">
        <template #company_name="{row}"><span class="charging-company-name" :title="row.company_name">{{ row.company_name }}</span></template>
        <template #actions="{row}"><button type="button" @click="editCompany(row)">操作</button></template>
      </DataTable></div>
      <div v-if="can('BUTTON_CHARGING_COMPANIES_MANAGE') && editorOpen" class="charging-form">
        <h2>{{ editingId ? '修改公司与映射' : '新增充电公司' }}</h2>
        <p v-if="editingId" class="muted">选择本次修改方式。当前公司只保留一份立即生效的字段映射。</p>
        <div v-if="editingId" class="charging-mode-choice" role="group" aria-label="模板修改方式">
          <button type="button" :class="{ active: editorMode === 'mapping' }" @click="chooseEditorMode('mapping')">仅修改字段映射</button>
          <button type="button" :class="{ active: editorMode === 'sample' }" @click="chooseEditorMode('sample')">上传新模板并重新匹配</button>
        </div>
        <p v-else class="muted">填写公司名称、上传 Excel 样表并完成字段映射；来源系统标识保存时自动生成。</p>
        <p v-if="!editorMode" class="ui-empty">请选择修改方式后填写公司信息。</p>
        <template v-else>
        <div class="ui-form-grid" :class="{ 'charging-creation-fields': !editingId }">
          <label>公司名称<input v-model="companyName" maxlength="128" placeholder="填写正式公司名称" /></label>
          <ImportFilePicker v-if="editorMode === 'sample'" :file="companyFile" :disabled="busy" label="上传 Excel 样本" accept=".xls,.xlsx" @clear="clearCompanySample" @select="chooseCompanySample" />
          <label v-if="editorMode === 'sample' && companyBook">表头所在行<input v-model.number="headerRowNo" type="number" min="1" max="100" @change="correctHeader" /></label>
        </div>
        <p class="muted">{{ editorMode === 'sample' ? '新样本读取第一个工作表；四项映射必须全部从新表头重新选择。' : '使用已保存表头，只调整需要变化的字段；无需上传文件。' }}</p>
        <div v-if="headers.length" class="ui-form-grid">
          <label v-for="field in fields" :key="field.key">{{ field.label }}
            <select v-model.number="mapping[field.key]"><option :value="null">请选择表头</option>
              <option v-for="header in headers" :key="header.index" :value="header.index">第 {{ header.index + 1 }} 列 · {{ header.label }}</option>
            </select>
          </label>
        </div>
        <EmptyState v-else title="尚无表头" description="请上传一份原始 Excel 样本。" />
        </template>
        <div class="ui-dialog-actions"><button type="button" :disabled="busy" @click="cancelEditor">取消</button><button v-if="editorMode" type="button" class="primary" :disabled="busy || editorMode === 'sample' && !companyBook || !companyName.trim() || !mappingComplete(mapping, headers)" @click="saveCompany">{{ busy ? '正在保存…' : '保存当前映射' }}</button></div>
      </div>
      </div>
    </section>

    <section v-else-if="tab==='import'" class="ui-section-card charging-upload-panel">
      <div class="ui-list-heading"><div><h2>上传原始订单</h2><p class="muted">选择公司与文件 → 只读预览 → 人工核对并确认。确认前不会入账或保存原文件。</p></div></div>
      <div class="charging-import-controls">
        <label>充电公司<select v-model="selectedCompanyId" :disabled="busy || importParsing" @change="changeImportCompany"><option value="">请选择公司</option><option v-for="company in companies" :key="company.id" :value="String(company.id)">{{ company.company_name }}</option></select></label>
        <ImportFilePicker :file="importFile" :disabled="!selectedCompany || busy || importParsing" :loading="importParsing" label="原始 Excel 文件" accept=".xls,.xlsx" @clear="clearImportFile" @select="chooseImport" />
      </div>
      <template v-if="preview">
        <div class="ui-list-heading"><h2>只读订单预览</h2><span>第一个工作表：{{ importBook.sheetName }}</span></div>
        <div class="ui-import-result-toolbar"><SummaryCards label="充电订单预览统计" :items="[
          { label: '有效候选订单', value: `${preview.count} 条`, description: '来自原始 Excel' },
          { label: '正金额候选', value: `${preview.count - preview.zeroCount} 条`, description: '正式提交后由服务端复核' },
          { label: '零元不入账', value: `${preview.zeroCount} 条`, description: '保留来源并参与去重' },
          { label: '已存在重复', value: '待服务端核对', description: '确认时按来源与订单号去重' },
          { label: '异常行', value: `${preview.errors.length} 条`, description: '存在异常时禁止提交' },
          { label: '候选金额', value: formatMoney(preview.totalAmount), description: '重复订单数在正式提交时复核' }
        ]" /><ImportResultFilter v-model="resultFilter" /></div>
        <p :class="preview.errors.length ? 'ui-import-validation-hint' : 'muted'">预览不允许修改订单；已存在重复订单须在正式确认时由服务端判定。发现异常请修正原文件或字段映射后重新解析。</p>
        <p v-if="preview.ignored.length" class="muted">已排除 {{ preview.ignored.length }} 行汇总数据；不会遇到空行就停止解析。</p>
        <div v-if="resultFilter !== 'PASS' && preview.errors.length" class="ui-section-card ui-import-validation-hint"><h3>请修正 {{ preview.errors.length }} 条异常行</h3><p v-for="item in preview.errors.slice(0,100)" :key="item.rowNo">第 {{ item.rowNo }} 行：{{ item.message }}</p></div>
        <div v-if="resultFilter !== 'FAIL'" class="charging-table"><DataTable :columns="orderColumns" :rows="preview.orders.slice(0,100)" empty-text="没有有效订单" /></div>
        <p v-if="resultFilter !== 'FAIL' && preview.orders.length > 100" class="muted">表格显示前 100 条；确认时提交全部 {{ preview.count }} 条，后端逐行核验原始文件。</p>
        <p v-if="resultFilter === 'FAIL' && !preview.errors.length" class="muted">没有失败记录。</p>
        <div class="ui-dialog-actions"><span :class="preview.errors.length ? 'ui-import-validation-hint' : 'muted'">{{ preview.errors.length ? `还有 ${preview.errors.length} 条异常行，修正文件后才能确认。` : preview.count ? '核对全部有效候选订单后，再进行正式提交。' : '没有可提交的有效订单。' }}</span><button type="button" class="primary" :disabled="!ready || !can('BUTTON_CHARGING_IMPORTS_IMPORT')" @click="confirm">{{ busy ? '正在正式入账…' : `确认提交 ${preview.count} 条订单` }}</button></div>
      </template>
      <ImportCompletionSummary v-if="completion" :title="confirmFailure ? '充电订单导入失败' : '充电订单导入完成'" :summary="completion" unit="条" total-label="有效订单" success-label="正常入账" :note="result ? `零元不入账 ${result.batch.zero_count} 条，已存在重复 ${result.batch.duplicate_count} 条；入账金额 ${formatMoney(result.batch.imported_amount)}。原始文件已保存，可在导入历史查看。` : '请核对失败原因并修正原文件后重新选择。'" />
      <EmptyState v-if="!preview && !result && !importParsing" title="等待原始订单文件" description="先选择公司，再选择 Excel 自动预览；核对后确认提交。" />
    </section>

    <section v-else class="ui-section-card import-history-card">
      <div class="ui-list-heading"><div><h2>充电订单导入历史</h2><p class="muted">按批次核对有效订单、零元订单、重复订单和原始文件。</p></div><button type="button" @click="load">刷新</button></div>
      <DataTable class="ui-paged-table" :columns="[{key:'company_name',label:'公司'},{key:'original_filename',label:'原始文件'},{key:'created_at',label:'导入时间'},{key:'confirmed_count',label:'有效订单'},{key:'imported_count',label:'入账'},{key:'zero_count',label:'零元不入账'},{key:'duplicate_count',label:'重复'},{key:'imported_amount',label:'入账金额'},{key:'actions',label:'操作'}]"
        :rows="history" :loading="loading" empty-text="暂无充电订单导入记录">
        <template #created_at="{value}">{{ formatImportDateTime(value) }}</template>
        <template #imported_amount="{value}">{{ formatMoney(value) }}</template>
        <template #actions="{row}"><div class="import-row-actions"><button type="button" class="ui-detail-button" @click="showBatch(row)">查看详情</button><button type="button" @click="downloadOriginal(row)">打开原始 Excel</button></div></template>
      </DataTable>
      <PaginationBar :page-no="historyPage" :page-size="historyPageSize" :total="historyTotal" @change="changeHistoryPage" @change-size="(size) => { historyPageSize = size; changeHistoryPage(1); }" />
    </section>
    <DetailDrawer :open="!!selectedBatch" class="charging-import-detail-drawer" title="充电订单导入批次详情" @close="selectedBatch=null">
      <template #subtitle>{{ selectedBatch?.batch?.company_name }} · {{ selectedBatch?.batch?.original_filename }}</template>
      <template v-if="selectedBatch">
        <p>原始文件：{{ selectedBatch.batch.original_filename }}；有效 {{ selectedBatch.batch.confirmed_count }} = 入账 {{ selectedBatch.batch.imported_count }} + 零元不入账 {{ selectedBatch.batch.zero_count }} + 已存在重复 {{ selectedBatch.batch.duplicate_count }}；入账金额 {{ formatMoney(selectedBatch.batch.imported_amount) }}。</p>
        <p v-if="!selectedBatch.orders?.length && selectedBatch.batch.duplicate_count" class="muted">本批次订单均已存在，没有新增来源订单；重复数量按本次正式结果记录。</p>
        <button type="button" @click="downloadOriginal(selectedBatch.batch)">下载本批次原始 Excel</button>
        <div class="charging-table charging-detail-table"><DataTable :columns="[{key:'row_no',label:'原始行号'},{key:'original_order_no',label:'订单号'},{key:'plate_no',label:'车牌'},{key:'charged_at',label:'充电时间'},{key:'amount',label:'金额',numeric:true},{key:'processing_status',label:'处理方式'},{key:'task_no',label:'匹配任务'}]" :rows="(selectedBatch.orders || []).slice((sourcePage - 1) * 50, sourcePage * 50)" empty-text="本批次没有新增来源订单"><template #charged_at="{value}">{{ formatImportDateTime(value) }}</template><template #amount="{value}">{{ formatMoney(value) }}</template><template #task_no="{value}">{{ value || '未归属' }}</template></DataTable></div>
        <PaginationBar :show-size="false" :page-no="sourcePage" :page-size="50" :total="selectedBatch.orders?.length || 0" @change="sourcePage = $event" />
      </template>
    </DetailDrawer>
    <ConfirmDialog :open="cancelPending" title="放弃未保存修改？" message="取消后将丢弃当前修改，恢复已保存的字段配置。" @cancel="cancelPending=false" @confirm="discardEditor" />
    <ConfirmDialog :open="!!pendingMode" title="更换修改方式？" message="更换方式会丢弃本次尚未保存的字段选择和新样本。" @cancel="pendingMode=''" @confirm="applyEditorMode(pendingMode); pendingMode=''" />
  </main>
</template>

<style scoped>
.charging-import-controls { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; align-items:end; width:min(100%,1200px); margin:12px 0 18px; }
.charging-import-controls > label { min-width:0; }
.charging-import-controls :deep(.ui-import-file-picker) { min-width:0; justify-items:stretch; margin:0; }
.charging-import-controls :deep(.ui-import-file-controls) { width:100%; }
.charging-upload-panel .ui-dialog-actions { margin: 5px 0; }
.charging-upload-panel > .ui-empty { padding: 11px 16px; }
.charging-form { margin-top: 24px; border-top: 1px solid var(--line, #dce8e3); padding-top: 18px; }
.charging-table { overflow-x: auto; max-height: 520px; overflow-y: auto; }
.charging-import-detail-drawer :deep(.ui-drawer) { width:min(96vw,1120px); }
.charging-detail-table { overflow-x:hidden; }
.charging-detail-table :deep(.ui-data-table) { min-width:0; table-layout:fixed; }
.charging-detail-table :deep(.ui-data-table th),
.charging-detail-table :deep(.ui-data-table td) { white-space:normal; overflow-wrap:anywhere; padding:10px 8px; }
.charging-form h2 { margin-bottom: 16px; }
.ui-form-grid { margin: 16px 0; }
.charging-mode-choice { display:flex; flex-wrap:wrap; gap:8px; margin:14px 0; }
.charging-mode-choice button.active { color:#135d4d; background:#e5f4ed; border-color:#34856b; font-weight:700; }
.charging-company-layout { min-width:0; }
.charging-company-layout.editing { display:grid; grid-template-columns:minmax(0,2fr) minmax(0,3fr); gap:14px; align-items:start; }
.charging-creation-fields { grid-template-columns:1fr; }
.charging-company-list { min-width:0; overflow-x:auto; }
.charging-company-name { display:block; overflow-wrap:anywhere; }
.charging-company-layout.editing .charging-company-list :deep(.ui-data-table) { min-width:0; table-layout:fixed; }
.charging-company-layout.editing .charging-company-list :deep(.ui-data-table .ui-action-column) { width:76px; white-space:nowrap; }
.charging-company-layout.editing .charging-company-list :deep(.ui-data-table th),
.charging-company-layout.editing .charging-company-list :deep(.ui-data-table td) { padding:10px 8px; white-space:normal; overflow-wrap:anywhere; }
.charging-company-layout.editing .charging-form { min-width:0; margin:0; padding:20px; border:1px solid var(--ui-border); border-radius:10px; background:#f8fbf9; }
.charging-company-layout.editing .charging-form h2 { margin:0 0 6px; }
.charging-company-layout.editing .ui-form-grid { gap:9px; margin:10px 0; }
@media (max-width:900px) { .charging-company-layout.editing { grid-template-columns:1fr; } }
@media (max-width:760px) { .charging-import-controls { grid-template-columns:1fr; } }
</style>
