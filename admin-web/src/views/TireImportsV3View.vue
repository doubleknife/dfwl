<script setup>
import { computed, onMounted, ref } from 'vue';
import { tireApi, attachmentApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { showErrorDialog } from '../stores/apiErrorDialog.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import EmptyState from '../components/EmptyState.vue';
import ImportTabs from '../components/ImportTabs.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ImportFilePicker from '../components/ImportFilePicker.vue';
import ImportResultFilter from '../components/ImportResultFilter.vue';
import SummaryCards from '../components/SummaryCards.vue';
import ImportCompletionSummary from '../components/ImportCompletionSummary.vue';
import StatusTag from '../components/StatusTag.vue';
import { countImportFailures, filterImportRows, formatImportDateTime, summarizeCompletedRows } from './importResultView.js';

const file = ref(null);
const requestKey = ref('');
const preview = ref(null);
const completed = ref(null);
const historyDetail = ref(null);
const historyFile = ref(null);
const detailError = ref('');
const detailPage = ref(1);
const history = ref([]);
const page = ref(1);
const total = ref(0);
const rowPage = ref(1);
const tab = ref('upload');
const busy = ref(false);
const previewing = ref(false);
const loading = ref(false);
const error = ref('');
const resultFilter = ref('ALL');
const previewRows = computed(() => preview.value?.rows || []);
const resultRows = computed(() => completed.value?.rows || previewRows.value);
const failedCount = computed(() => countImportFailures(previewRows.value));
const completion = computed(() => completed.value
  ? summarizeCompletedRows(completed.value.rows || [], 'IMPORTED') : null);
const filteredRows = computed(() => filterImportRows(resultRows.value, resultFilter.value));
const visibleRows = computed(() => filteredRows.value.slice((rowPage.value - 1) * 50, rowPage.value * 50));
const stateName = { PREVIEWED: '待确认', COMPLETED: '全部入库', COMPLETED_WITH_ERRORS: '部分行失败',
  VALID: '校验通过', INVALID: '校验失败', IMPORTED: '入库成功', FAILED: '入库失败', CONFIRMING: '正在处理' };
const columns = [{ key: 'rowNo', label: 'Excel 行号' }, { key: 'arrivedAt', label: '到货时间' },
  { key: 'materialDescription', label: '物料描述' }, { key: 'barcode', label: '条码' },
  { key: 'tireNo', label: '胎号' }, { key: 'status', label: '结果' }, { key: 'message', label: '说明' }];

async function download() {
  busy.value = true; error.value = '';
  try {
    const blob = await tireApi.template();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url; link.download = '轮胎入库固定模板.xlsx'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch { /* API 层已弹窗。 */ }
  finally { busy.value = false; }
}
function clearFile() { file.value = null; requestKey.value = ''; preview.value = null; completed.value = null; resultFilter.value = 'ALL'; rowPage.value = 1; error.value = ''; }
function choose(selected) { file.value = selected; requestKey.value = crypto.randomUUID(); preview.value = null; completed.value = null; resultFilter.value = 'ALL'; error.value = ''; upload(); }
async function upload() {
  if (!file.value || busy.value) return;
  busy.value = true; previewing.value = true; error.value = '';
  try { preview.value = await tireApi.preview(file.value); rowPage.value = 1; }
  catch { /* API 层已弹窗。 */ }
  finally { busy.value = false; previewing.value = false; }
}
async function confirm() {
  if (!preview.value || busy.value) return;
  busy.value = true; error.value = '';
  try {
    completed.value = await tireApi.confirm(file.value, requestKey.value);
    preview.value = null;
    file.value = null;
    resultFilter.value = 'ALL'; rowPage.value = 1;
    await loadHistory(1);
  } catch { /* API 层已弹窗。 */ }
  finally { busy.value = false; }
}
async function loadHistory(next = 1) {
  loading.value = true; error.value = '';
  try {
    const response = await tireApi.imports(next);
    history.value = response.records || []; total.value = Number(response.total || 0); page.value = next;
  } catch { /* API 层已弹窗。 */ }
  finally { loading.value = false; }
}
async function open(item) {
  busy.value = true; error.value = ''; detailError.value = ''; historyDetail.value = null; historyFile.value = null;
  try {
    historyDetail.value = await tireApi.importDetail(item.id);
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
onMounted(() => { if (can('PAGE_TIRE_IMPORTS')) loadHistory(); });
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="轮胎入库" description="只接收系统固定四字段模板。上传后逐行预览，确认时再次校验并保存结果。">
      <template #actions><button class="ui-import-template-button" type="button" :disabled="busy" @click="download">下载固定模板</button></template>
    </PageHeader>
    <ImportTabs v-model="tab" label="轮胎入库" :items="[{key:'upload',label:'上传与预览'},{key:'history',label:'导入历史'}]" @update:model-value="$event === 'history' && loadHistory(1)" />
    <section v-if="tab==='upload'" class="ui-section-card">
      <h2>系统四字段轮胎模板</h2>
      <p class="muted">仅填写到货时间、物料描述、条码、胎号。无需车牌、司机、装胎时间或数据库编号。</p>
      <ImportFilePicker v-if="can('BUTTON_TIRE_IMPORTS_SUBMIT')" :file="file" :disabled="busy" :loading="previewing" label="选择 Excel 文件" accept=".xlsx" @clear="clearFile" @select="choose" />
      <p v-else class="muted">当前账号只能查看轮胎入库记录。</p>
      <template v-if="preview || completed">
        <div class="ui-list-heading">
          <h2>{{ preview ? '逐行预览' : '正式入库结果' }}</h2>
          <button v-if="preview && can('BUTTON_TIRE_IMPORTS_SUBMIT')" type="button" class="primary" :disabled="busy" @click="confirm">确认入库</button>
        </div>
        <ImportCompletionSummary v-if="completion" title="轮胎入库完成" :summary="completion" note="逐行结果已保存；失败行的具体原因见下方，也可重新选择修正后的文件。" />
        <div class="ui-import-result-toolbar"><SummaryCards v-if="preview" label="轮胎入库预览统计" :items="[{ label: '总计', value: `${previewRows.length} 行` }, { label: '通过', value: `${previewRows.length - failedCount} 行` }, { label: '失败', value: `${failedCount} 行` }]" /><ImportResultFilter v-model="resultFilter" @update:model-value="rowPage = 1" /></div>
        <p v-if="preview && (preview.rows || []).some((row) => row.status === 'INVALID' || row.status === 'FAILED')" class="ui-import-validation-hint">请修正 Excel 后重新选择文件，逐行失败原因见下表。</p>
        <DataTable :columns="columns" :rows="visibleRows" row-key="rowNo" :empty-text="resultFilter === 'FAIL' ? '没有失败记录' : resultFilter === 'PASS' ? '没有通过记录' : '暂无导入行'">
          <template #status="{value}"><StatusTag :value="stateName[value] || '待处理'" /></template>
          <template #message="{value}">{{ value || '等待确认' }}</template>
        </DataTable>
        <PaginationBar :page-no="rowPage" :page-size="50" :total="filteredRows.length" @change="rowPage=$event" />
      </template>
      <EmptyState v-else-if="!previewing" title="尚未选择文件" description="请先下载系统固定模板并填写，选择 Excel 后自动校验。" />
    </section>
    <section v-else class="ui-section-card import-history-card">
      <div class="ui-list-heading"><div><h2>轮胎入库历史</h2><p class="muted">选择一批查看每条轮胎的入库结果。</p></div><button type="button" :disabled="loading" @click="loadHistory(page)">刷新</button></div>
      <DataTable :columns="[{key:'createdAt',label:'上传时间'},{key:'status',label:'状态'},{key:'confirmedAt',label:'确认时间'},{key:'actions',label:'操作'}]"
        :rows="history" :loading="loading" empty-text="暂无轮胎入库记录">
        <template #createdAt="{value}">{{ formatImportDateTime(value) }}</template>
        <template #status="{value}"><StatusTag :value="stateName[value] || '处理中'" /></template>
        <template #confirmedAt="{value}">{{ formatImportDateTime(value, '尚未确认') }}</template>
        <template #actions="{row}"><button type="button" @click="open(row)">查看详情</button></template>
      </DataTable>
      <PaginationBar :page-no="page" :page-size="20" :total="total" @change="loadHistory" />
    </section>
    <DetailDrawer :open="!!historyDetail" class="tire-import-detail-drawer" title="轮胎入库批次详情" @close="historyDetail = null">
      <template #subtitle>上传于 {{ formatImportDateTime(historyDetail?.createdAt) }} · <StatusTag :value="stateName[historyDetail?.status] || '处理中'" /></template>
      <template v-if="historyDetail">
        <div class="import-detail-summary"><strong>批次状态：<StatusTag :value="stateName[historyDetail.status] || '处理中'" /></strong><span>原始 {{ historyDetail.rows?.length || 0 }} 行</span><span>成功 {{ (historyDetail.rows || []).filter(row => row.status === 'IMPORTED').length }} 行</span><span>待确认 {{ (historyDetail.rows || []).filter(row => row.status === 'VALID').length }} 行</span><span>失败 {{ (historyDetail.rows || []).filter(row => row.status === 'FAILED' || row.status === 'INVALID').length }} 行</span></div>
        <p>原始文件：{{ historyFile?.originalFilename || '本批次原始文件记录不存在' }} <button type="button" :disabled="!historyFile" @click="downloadOriginal">下载原始 Excel</button></p>
        <div class="tire-import-detail-table">
          <DataTable :columns="columns" :rows="(historyDetail.rows || []).slice((detailPage - 1) * 50, detailPage * 50)" row-key="rowNo" empty-text="本批次没有明细行">
            <template #arrivedAt="{value}"><span class="tire-import-ellipsis" :title="value || '—'">{{ value || '—' }}</span></template>
            <template #materialDescription="{value}"><span class="tire-import-ellipsis" :title="value || '—'">{{ value || '—' }}</span></template>
            <template #barcode="{value}"><span class="tire-import-ellipsis" :title="value || '—'">{{ value || '—' }}</span></template>
            <template #tireNo="{value}"><span class="tire-import-ellipsis" :title="value || '—'">{{ value || '—' }}</span></template>
            <template #status="{value}"><StatusTag :value="stateName[value] || '待处理'" /></template>
            <template #message="{value}"><span class="tire-import-ellipsis" :title="value || '—'">{{ value || '—' }}</span></template>
          </DataTable>
        </div>
        <PaginationBar :page-no="detailPage" :page-size="50" :total="historyDetail.rows?.length || 0" @change="detailPage = $event" />
      </template>
    </DetailDrawer>
  </main>
</template>

<style scoped>
.import-detail-summary { display:flex; flex-wrap:wrap; gap:8px 16px; margin-bottom:12px; }
.tire-import-detail-drawer :deep(.ui-drawer) { width: min(100%, 1040px); }
.tire-import-detail-drawer :deep(.ui-drawer-body) { overflow-x: hidden; }
.tire-import-detail-drawer :deep(.ui-drawer-body > p) { overflow-wrap: anywhere; }
.tire-import-detail-table :deep(.ui-table-wrap) { overflow-x: hidden; }
.tire-import-detail-table :deep(.ui-data-table) { width: 100%; min-width: 0; table-layout: fixed; }
.tire-import-detail-table :deep(.ui-data-table th),
.tire-import-detail-table :deep(.ui-data-table td) { overflow: hidden; text-overflow: ellipsis; padding: 10px 8px; }
.tire-import-detail-table :deep(.ui-data-table th:nth-child(1)) { width: 7%; }
.tire-import-detail-table :deep(.ui-data-table th:nth-child(2)) { width: 14%; }
.tire-import-detail-table :deep(.ui-data-table th:nth-child(3)) { width: 19%; }
.tire-import-detail-table :deep(.ui-data-table th:nth-child(4)) { width: 12%; }
.tire-import-detail-table :deep(.ui-data-table th:nth-child(5)) { width: 12%; }
.tire-import-detail-table :deep(.ui-data-table th:nth-child(6)) { width: 11%; }
.tire-import-detail-table :deep(.ui-data-table th:nth-child(7)) { width: 25%; }
.tire-import-detail-table :deep(.ui-data-table td:last-child) { position: static; }
.tire-import-ellipsis { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
