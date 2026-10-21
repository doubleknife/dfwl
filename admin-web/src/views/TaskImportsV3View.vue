<script setup>
import { computed, onMounted, ref } from 'vue';
import { taskImportApi, attachmentApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { showErrorDialog } from '../stores/apiErrorDialog.js';
import PageHeader from '../components/PageHeader.vue';
import EmptyState from '../components/EmptyState.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ImportTabs from '../components/ImportTabs.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ImportFilePicker from '../components/ImportFilePicker.vue';
import ImportResultFilter from '../components/ImportResultFilter.vue';
import SummaryCards from '../components/SummaryCards.vue';
import ImportCompletionSummary from '../components/ImportCompletionSummary.vue';
import StatusTag from '../components/StatusTag.vue';
import { countImportFailures, filterImportRows, formatImportDateTime, summarizeCompletedRows } from './importResultView.js';

const props = defineProps({ initialTab: { type: String, default: 'upload' } });
const selectedFile = ref(null);
const requestKey = ref('');
const current = ref(null);
const completed = ref(null);
const historyDetail = ref(null);
const historyFile = ref(null);
const detailError = ref('');
const detailPage = ref(1);
const rowPage = ref(1);
const resultFilter = ref('ALL');
const previewRows = computed(() => current.value?.rows || []);
const resultRows = computed(() => completed.value?.rows || previewRows.value);
const failedCount = computed(() => countImportFailures(previewRows.value));
const completion = computed(() => completed.value
  ? summarizeCompletedRows(completed.value.rows || [], 'PUBLISHED') : null);
const filteredRows = computed(() => filterImportRows(resultRows.value, resultFilter.value));
const visibleRows = computed(() => filteredRows.value.slice((rowPage.value - 1) * 50, rowPage.value * 50));
const history = ref([]);
const historyPage = ref(1); const historyPageSize = ref(20);
const historyTotal = ref(0);
const busy = ref(false);
const previewing = ref(false);
const loading = ref(false);
const error = ref('');
const tab = ref(props.initialTab);
const stateName = { PREVIEWED: '待确认', CONFIRMING: '正在发布', COMPLETED: '全部发布',
  COMPLETED_WITH_ERRORS: '部分行失败', VALID: '预览通过', INVALID: '预览不通过',
  PUBLISHED: '发布成功', FAILED: '发布失败' };
const columns = [{ key: 'rowNo', label: 'Excel 行号' }, { key: 'businessDate', label: '业务日期' },
  { key: 'templateStableCode', label: '线路模板编号' }, { key: 'driverPhone', label: '司机手机号' },
  { key: 'outsourcedPlateNo', label: '本次外协车牌' }, { key: 'amounts', label: '任务金额' },
  { key: 'status', label: '结果' }, { key: 'taskNo', label: '任务号 / 原因' }];
const historyColumns = [{ key: 'createdAt', label: '上传时间' }, { key: 'status', label: '状态' },
  { key: 'confirmedAt', label: '确认时间' }, { key: 'actions', label: '操作' }];

function clearFile() {
  selectedFile.value = null;
  resultFilter.value = 'ALL';
  requestKey.value = '';
  current.value = null;
  completed.value = null;
  rowPage.value = 1;
  error.value = '';
}
function chooseFile(file) {
  selectedFile.value = file;
  resultFilter.value = 'ALL';
  requestKey.value = crypto.randomUUID();
  current.value = null;
  completed.value = null;
  rowPage.value = 1;
  error.value = '';
  previewFile();
}
async function downloadTemplate() {
  busy.value = true; error.value = '';
  try {
    const blob = await taskImportApi.template();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url; link.download = '运输任务固定模板.xlsx'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch { /* API 层已弹窗。 */ }
  finally { busy.value = false; }
}
async function previewFile() {
  if (!selectedFile.value || busy.value) return;
  busy.value = true; previewing.value = true; error.value = '';
  try {
    current.value = await taskImportApi.preview(selectedFile.value, requestKey.value);
    rowPage.value = 1;
  } catch { /* API 层已弹窗。 */ }
  finally { busy.value = false; previewing.value = false; }
}
async function confirmImport() {
  if (!current.value || busy.value || !can('BUTTON_TASK_IMPORTS_SUBMIT')) return;
  busy.value = true; error.value = '';
  try {
    completed.value = await taskImportApi.confirm(selectedFile.value, requestKey.value);
    current.value = null;
    selectedFile.value = null;
    resultFilter.value = 'ALL'; rowPage.value = 1;
    await loadHistory(1);
  } catch { /* API 层已弹窗。 */ }
  finally { busy.value = false; }
}
async function loadHistory(next = 1) {
  loading.value = true; error.value = '';
  try {
    const page = await taskImportApi.history(next, historyPageSize.value);
    history.value = page.records || [];
    historyPage.value = next;
    historyTotal.value = Number(page.total || 0);
  } catch { /* API 层已弹窗。 */ }
  finally { loading.value = false; }
}
async function showHistory(item) {
  busy.value = true; error.value = ''; detailError.value = ''; historyDetail.value = null; historyFile.value = null;
  try {
    historyDetail.value = await taskImportApi.detail(item.id);
    historyFile.value = (await attachmentApi.list('IMPORT', item.id, 'IMPORT_FILE'))[0] || null;
    detailPage.value = 1;
  }
  catch (cause) {
    historyDetail.value = null; historyFile.value = null;
    // API 层已弹窗。
  }
  finally { busy.value = false; }
}
async function downloadOriginal() {
  if (!historyFile.value) { showErrorDialog('下载失败', '本批次原始文件不存在'); return; }
  try {
    const blob = await attachmentApi.download(historyFile.value.id);
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = historyFile.value.originalFilename; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch { /* API 层已弹窗。 */ }
}
const money = (value) => value === '' || value == null ? '模板默认' : `¥${Number(value).toFixed(2)}`;
onMounted(() => { if (can('PAGE_TASK_IMPORTS')) loadHistory(); });
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="任务导入" description="只接收系统固定 Excel；预览逐行核对，确认时服务端再次校验，逐行保存结果。">
      <template #actions><button class="ui-import-template-button" type="button" :disabled="busy" @click="downloadTemplate">下载固定模板</button></template>
    </PageHeader>
    <ImportTabs v-model="tab" label="任务导入" :items="[{key:'upload',label:'上传与预览'},{key:'history',label:'导入历史'}]" @update:model-value="$event === 'history' && loadHistory(1)" />
    <section v-if="tab === 'upload'" class="ui-section-card">
      <h2>上传系统固定任务模板</h2>
      <p class="muted">一行一趟运输任务。填写业务日期、模板编号和已建档司机手机号；金额留空时使用模板当前默认值。请勿填写数据库 ID。</p>
      <ImportFilePicker v-if="can('BUTTON_TASK_IMPORTS_SUBMIT')" :file="selectedFile" :disabled="busy" :loading="previewing" label="选择 Excel 文件" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" @clear="clearFile" @select="chooseFile" />
      <p v-else class="muted">当前账号只能查看导入记录，没有提交权限。</p>
      <template v-if="current || completed">
        <div class="ui-list-heading"><div><h2>{{ current ? '逐行预览' : '正式导入结果' }}</h2>
          <p class="muted">共 {{ resultRows.length }} 行；状态：<StatusTag :value="stateName[(current || completed).status] || '处理中'" />。</p></div>
          <button v-if="current && can('BUTTON_TASK_IMPORTS_SUBMIT')" type="button" class="primary" :disabled="busy" @click="confirmImport">{{ busy ? '正在正式校验…' : '确认导入有效行' }}</button>
        </div>
        <ImportCompletionSummary v-if="completion" title="任务导入完成" :summary="completion" note="有效行已逐行发布；失败行的具体原因见下方，也可重新选择修正后的文件。" />
        <div class="ui-import-result-toolbar"><SummaryCards v-if="current" label="任务导入预览统计" :items="[{ label: '总计', value: `${previewRows.length} 行` }, { label: '通过', value: `${previewRows.length - failedCount} 行` }, { label: '失败', value: `${failedCount} 行` }]" /><ImportResultFilter v-model="resultFilter" @update:model-value="rowPage = 1" /></div>
        <p v-if="current" :class="(current.rows || []).some((row) => row.status === 'INVALID') ? 'ui-import-validation-hint' : 'muted'">无效行会记录失败原因；确认时所有行都重新校验，有效行逐行生成任务。若需修改 Excel，请重新上传修正后的文件。</p>
        <DataTable :columns="columns" :rows="visibleRows" row-key="rowNo" :empty-text="resultFilter === 'FAIL' ? '没有失败记录' : resultFilter === 'PASS' ? '没有通过记录' : '暂无导入行'">
          <template #amounts="{ row }">单价 {{ money(row.actualUnitPrice) }}<br />信息费 {{ money(row.actualInformationFee) }}<br />工资 {{ money(row.initialSalary) }}</template>
          <template #status="{ row }"><StatusTag :value="stateName[row.status] || row.status" /></template>
          <template #taskNo="{ row }">{{ row.taskNo || row.message || '待确认' }}</template>
        </DataTable>
        <PaginationBar :show-size="false" :page-no="rowPage" :page-size="50" :total="filteredRows.length" @change="rowPage = $event" />
      </template>
      <EmptyState v-else-if="!previewing" title="尚未选择任务文件" description="先下载系统模板并填写，选择 Excel 后自动校验。" />
    </section>
    <section v-else class="ui-section-card import-history-card">
      <div class="ui-list-heading"><div><h2>任务导入历史</h2><p class="muted">选择一批查看逐行校验与发布结果。</p></div><button type="button" :disabled="loading" @click="loadHistory(historyPage)">刷新</button></div>
      <DataTable class="ui-paged-table" :columns="historyColumns" :rows="history" :loading="loading" empty-text="暂无任务导入记录">
        <template #createdAt="{ row }">{{ formatImportDateTime(row.createdAt) }}</template>
        <template #status="{ row }"><StatusTag :value="stateName[row.status] || row.status" /></template>
        <template #confirmedAt="{ row }">{{ formatImportDateTime(row.confirmedAt, '尚未确认') }}</template>
        <template #actions="{ row }"><button type="button" class="ui-detail-button" @click="showHistory(row)">查看详情</button></template>
      </DataTable>
      <PaginationBar :page-no="historyPage" :page-size="historyPageSize" :total="historyTotal" @change="loadHistory" @change-size="(size) => { historyPageSize = size; loadHistory(1); }" />
    </section>
    <DetailDrawer :open="!!historyDetail" class="task-import-detail-drawer" title="任务导入批次详情" @close="historyDetail = null">
      <template #subtitle>上传于 {{ formatImportDateTime(historyDetail?.createdAt) }} · <StatusTag :value="stateName[historyDetail?.status] || '处理中'" /></template>
      <template v-if="historyDetail">
        <div class="import-detail-summary"><strong>批次状态：<StatusTag :value="stateName[historyDetail.status] || '处理中'" /></strong><span>原始 {{ historyDetail.rows?.length || 0 }} 行</span><span>成功 {{ (historyDetail.rows || []).filter(row => row.status === 'PUBLISHED').length }} 行</span><span>待确认 {{ (historyDetail.rows || []).filter(row => row.status === 'VALID').length }} 行</span><span>失败 {{ (historyDetail.rows || []).filter(row => row.status === 'FAILED' || row.status === 'INVALID').length }} 行</span></div>
        <p>原始文件：{{ historyFile?.originalFilename || '本批次原始文件记录不存在' }} <button type="button" :disabled="!historyFile" @click="downloadOriginal">下载原始 Excel</button></p>
        <div class="task-import-detail-table">
          <DataTable :columns="columns" :rows="(historyDetail.rows || []).slice((detailPage - 1) * 50, detailPage * 50)" row-key="rowNo" empty-text="本批次没有明细行">
            <template #amounts="{ row }">单价 {{ money(row.actualUnitPrice) }}<br />信息费 {{ money(row.actualInformationFee) }}<br />工资 {{ money(row.initialSalary) }}</template>
            <template #status="{ row }"><StatusTag :value="stateName[row.status] || '待处理'" /></template>
            <template #taskNo="{ row }">{{ row.taskNo || row.message || '—' }}</template>
          </DataTable>
        </div>
        <PaginationBar :show-size="false" :page-no="detailPage" :page-size="50" :total="historyDetail.rows?.length || 0" @change="detailPage = $event" />
      </template>
    </DetailDrawer>
  </main>
</template>

<style scoped>
.import-error { color: #a3342f; }
.import-detail-summary { display:flex; flex-wrap:wrap; gap:8px 16px; margin-bottom:12px; }
.task-import-detail-drawer :deep(.ui-drawer) { width: min(calc(100vw - 24px), 1240px); }
.task-import-detail-drawer :deep(.ui-drawer-body > p) { overflow-wrap: anywhere; }
.task-import-detail-table :deep(.ui-data-table) { min-width: 1100px; }
.task-import-detail-table :deep(.ui-data-table td:last-child) { position: static; }
.task-import-detail-table :deep(.ui-data-table [data-column="amounts"]) { min-width: 150px; }
.task-import-detail-table :deep(.ui-data-table [data-column="taskNo"]) { min-width: 240px; }
</style>
