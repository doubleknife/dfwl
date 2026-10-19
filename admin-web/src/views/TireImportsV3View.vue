<script setup>
import { computed, onMounted, ref } from 'vue';
import { tireApi, attachmentApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import EmptyState from '../components/EmptyState.vue';
import ImportTabs from '../components/ImportTabs.vue';
import DetailDrawer from '../components/DetailDrawer.vue';

const file = ref(null);
const input = ref(null);
const preview = ref(null);
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
const loading = ref(false);
const error = ref('');
const visibleRows = computed(() => (preview.value?.rows || []).slice((rowPage.value - 1) * 50, rowPage.value * 50));
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
  } catch (cause) { error.value = cause.message || '模板下载失败'; }
  finally { busy.value = false; }
}
function choose(event) { file.value = event.target.files?.[0] || null; preview.value = null; error.value = ''; }
async function upload() {
  if (!file.value || busy.value) return;
  busy.value = true; error.value = '';
  try { preview.value = await tireApi.preview(file.value); rowPage.value = 1; }
  catch (cause) { error.value = cause.message || '预览失败，请使用系统固定模板'; }
  finally { busy.value = false; }
}
async function confirm() {
  if (!preview.value || busy.value) return;
  busy.value = true; error.value = '';
  try {
    preview.value = await tireApi.confirm(preview.value.id);
    file.value = null; if (input.value) input.value.value = '';
    await loadHistory(1);
  } catch (cause) { error.value = cause.message || '确认失败，请查看历史结果后重试'; }
  finally { busy.value = false; }
}
async function loadHistory(next = 1) {
  loading.value = true; error.value = '';
  try {
    const response = await tireApi.imports(next);
    history.value = response.records || []; total.value = Number(response.total || 0); page.value = next;
  } catch (cause) { error.value = cause.message || '导入历史加载失败'; }
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
    error.value = cause.message || '导入结果加载失败';
  }
  finally { busy.value = false; }
}
async function downloadOriginal() {
  if (!historyFile.value) { detailError.value = '本批次原始文件不存在'; return; }
  try {
    const blob = await attachmentApi.download(historyFile.value.id);
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = historyFile.value.originalFilename; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) { detailError.value = cause.message || '原始文件下载失败，请检查权限或文件是否存在'; }
}
onMounted(() => { if (can('PAGE_TIRE_IMPORTS')) loadHistory(); });
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="轮胎入库" description="只接收系统固定四字段模板。上传后逐行预览，确认时再次校验并保存结果。">
      <template #actions><button type="button" :disabled="busy" @click="download">下载固定模板</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="tab === 'history' ? loadHistory(page) : preview?.id ? open(preview) : upload()" />
    <ImportTabs v-model="tab" label="轮胎入库" :items="[{key:'upload',label:'上传与预览'},{key:'history',label:'导入历史'}]" @update:model-value="$event === 'history' && loadHistory(1)" />
    <section v-if="tab==='upload'" class="ui-section-card">
      <h2>系统四字段轮胎模板</h2>
      <p class="muted">仅填写到货时间、物料描述、条码、胎号。无需车牌、司机、装胎时间或数据库编号。</p>
      <div v-if="can('BUTTON_TIRE_IMPORTS_SUBMIT')" class="ui-import-actions">
        <label>选择 Excel 文件<input ref="input" type="file" accept=".xlsx" :disabled="busy" @change="choose" /></label>
        <button type="button" class="primary" :disabled="!file || busy" @click="upload">{{ busy ? '正在校验…' : '上传并预览' }}</button>
      </div>
      <p v-else class="muted">当前账号只能查看轮胎入库记录。</p>
      <template v-if="preview">
        <div class="ui-list-heading">
          <h2>{{ preview.status === 'PREVIEWED' ? '逐行预览' : '入库结果' }}</h2>
          <button v-if="preview.status === 'PREVIEWED' && can('BUTTON_TIRE_IMPORTS_SUBMIT')" type="button" class="primary" :disabled="busy" @click="confirm">确认入库</button>
        </div>
        <DataTable :columns="columns" :rows="visibleRows" row-key="rowNo" empty-text="暂无导入行">
          <template #status="{value}">{{ stateName[value] || '待处理' }}</template>
          <template #message="{value}">{{ value || '等待确认' }}</template>
        </DataTable>
        <PaginationBar :page-no="rowPage" :page-size="50" :total="preview.rows?.length || 0" @change="rowPage=$event" />
      </template>
      <EmptyState v-else title="尚未上传文件" description="请先下载系统固定模板并填写，再上传真实 Excel 文件。" />
    </section>
    <section v-else class="ui-section-card import-history-card">
      <div class="ui-list-heading"><div><h2>轮胎入库历史</h2><p class="muted">选择一批查看每条轮胎的入库结果。</p></div><button type="button" :disabled="loading" @click="loadHistory(page)">刷新</button></div>
      <DataTable :columns="[{key:'createdAt',label:'上传时间'},{key:'status',label:'状态'},{key:'confirmedAt',label:'确认时间'},{key:'actions',label:'操作'}]"
        :rows="history" :loading="loading" empty-text="暂无轮胎入库记录">
        <template #status="{value}">{{ stateName[value] || '处理中' }}</template>
        <template #confirmedAt="{value}">{{ value || '尚未确认' }}</template>
        <template #actions="{row}"><button type="button" @click="open(row)">查看详情</button></template>
      </DataTable>
      <PaginationBar :page-no="page" :page-size="20" :total="total" @change="loadHistory" />
    </section>
    <DetailDrawer :open="!!historyDetail" title="轮胎入库批次详情" @close="historyDetail = null">
      <template #subtitle>上传于 {{ historyDetail?.createdAt }} · {{ stateName[historyDetail?.status] || '处理中' }}</template>
      <template v-if="historyDetail">
        <p role="alert" v-if="detailError" class="error">{{ detailError }}</p>
        <div class="import-detail-summary"><strong>批次状态：{{ stateName[historyDetail.status] || '处理中' }}</strong><span>原始 {{ historyDetail.rows?.length || 0 }} 行</span><span>成功 {{ (historyDetail.rows || []).filter(row => row.status === 'IMPORTED').length }} 行</span><span>待确认 {{ (historyDetail.rows || []).filter(row => row.status === 'VALID').length }} 行</span><span>失败 {{ (historyDetail.rows || []).filter(row => row.status === 'FAILED' || row.status === 'INVALID').length }} 行</span></div>
        <p>原始文件：{{ historyFile?.originalFilename || '本批次原始文件记录不存在' }} <button type="button" :disabled="!historyFile" @click="downloadOriginal">下载原始 Excel</button></p>
        <DataTable :columns="columns" :rows="(historyDetail.rows || []).slice((detailPage - 1) * 50, detailPage * 50)" row-key="rowNo" empty-text="本批次没有明细行">
          <template #status="{value}">{{ stateName[value] || '待处理' }}</template>
          <template #message="{value}">{{ value || '—' }}</template>
        </DataTable>
        <PaginationBar :page-no="detailPage" :page-size="50" :total="historyDetail.rows?.length || 0" @change="detailPage = $event" />
      </template>
    </DetailDrawer>
  </main>
</template>

<style scoped>
.ui-import-actions { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; margin: 10px 0; }
.ui-import-actions label { display: grid; gap: 7px; font-weight: 600; }
.import-detail-summary { display:flex; flex-wrap:wrap; gap:8px 16px; margin-bottom:12px; }
</style>
