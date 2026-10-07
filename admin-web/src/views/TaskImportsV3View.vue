<script setup>
import { computed, onMounted, ref } from 'vue';
import { taskImportApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import ErrorState from '../components/ErrorState.vue';
import EmptyState from '../components/EmptyState.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';

const props = defineProps({ initialTab: { type: String, default: 'upload' } });
const selectedFile = ref(null);
const fileInput = ref(null);
const requestKey = ref('');
const current = ref(null);
const rowPage = ref(1);
const visibleRows = computed(() => (current.value?.rows || []).slice((rowPage.value - 1) * 50, rowPage.value * 50));
const history = ref([]);
const historyPage = ref(1);
const historyTotal = ref(0);
const busy = ref(false);
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

function chooseFile(event) {
  selectedFile.value = event.target.files?.[0] || null;
  requestKey.value = crypto.randomUUID();
  current.value = null;
  rowPage.value = 1;
  error.value = '';
}
async function downloadTemplate() {
  busy.value = true; error.value = '';
  try {
    const blob = await taskImportApi.template();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url; link.download = '运输任务固定模板.xlsx'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) { error.value = cause.message || '模板下载失败'; }
  finally { busy.value = false; }
}
async function previewFile() {
  if (!selectedFile.value || busy.value) return;
  busy.value = true; error.value = '';
  try {
    current.value = await taskImportApi.preview(selectedFile.value, requestKey.value);
    rowPage.value = 1;
  } catch (cause) { error.value = cause.message || '文件预览失败，请使用系统固定模板'; }
  finally { busy.value = false; }
}
async function confirmImport() {
  if (!current.value || busy.value || !can('task:import:submit')) return;
  busy.value = true; error.value = '';
  try {
    current.value = await taskImportApi.confirm(current.value.id);
    selectedFile.value = null;
    if (fileInput.value) fileInput.value.value = '';
    await loadHistory(1);
  } catch (cause) { error.value = cause.message || '正式确认失败；请刷新导入记录检查状态'; }
  finally { busy.value = false; }
}
async function loadHistory(next = 1) {
  loading.value = true; error.value = '';
  try {
    const page = await taskImportApi.history(next, 20);
    history.value = page.records || [];
    historyPage.value = next;
    historyTotal.value = Number(page.total || 0);
  } catch (cause) { error.value = cause.message || '导入历史加载失败'; }
  finally { loading.value = false; }
}
async function showHistory(item) {
  busy.value = true; error.value = '';
  try { current.value = await taskImportApi.detail(item.id); rowPage.value = 1; tab.value = 'upload'; }
  catch (cause) { error.value = cause.message || '导入详情加载失败'; }
  finally { busy.value = false; }
}
const money = (value) => value === '' || value == null ? '模板默认' : `¥${Number(value).toFixed(2)}`;
onMounted(() => { if (can('task:import:view')) loadHistory(); });
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="任务导入" description="只接收系统固定 Excel；预览逐行核对，确认时服务端再次校验，逐行保存结果。">
      <template #actions><button type="button" :disabled="busy" @click="downloadTemplate">下载固定模板</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="tab === 'history' ? loadHistory(historyPage) : current ? showHistory(current) : previewFile()" />
    <div class="ui-tabs" role="tablist" aria-label="任务导入">
      <button type="button" :aria-selected="tab === 'upload'" @click="tab = 'upload'">上传与预览</button>
      <button type="button" :aria-selected="tab === 'history'" @click="tab = 'history'; loadHistory(1)">导入历史</button>
    </div>
    <section v-if="tab === 'upload'" class="ui-section-card">
      <h2>上传系统固定任务模板</h2>
      <p class="muted">一行一趟运输任务。填写业务日期、模板编号和已建档司机手机号；金额留空时使用模板当前默认值。请勿填写数据库 ID。</p>
      <div v-if="can('task:import:submit')" class="import-upload">
        <label>选择 Excel 文件<input ref="fileInput" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" :disabled="busy" @change="chooseFile" /></label>
        <button type="button" class="primary" :disabled="!selectedFile || busy" @click="previewFile">{{ busy ? '正在处理…' : '上传并预览' }}</button>
      </div>
      <p v-else class="muted">当前账号只能查看导入记录，没有提交权限。</p>
      <template v-if="current">
        <div class="ui-list-heading"><div><h2>{{ current.status === 'PREVIEWED' ? '逐行预览' : '正式导入结果' }}</h2>
          <p class="muted">共 {{ current.rows?.length || 0 }} 行；状态：{{ stateName[current.status] || '处理中' }}。</p></div>
          <button v-if="current.status === 'PREVIEWED' && can('task:import:submit')" type="button" class="primary" :disabled="busy" @click="confirmImport">{{ busy ? '正在正式校验…' : '确认导入有效行' }}</button>
        </div>
        <p v-if="current.status === 'PREVIEWED'" class="muted">无效行会记录失败原因；确认时所有行都重新校验，有效行逐行生成任务。若需修改 Excel，请重新上传修正后的文件。</p>
        <DataTable :columns="columns" :rows="visibleRows" row-key="rowNo" empty-text="暂无导入行">
          <template #amounts="{ row }">单价 {{ money(row.actualUnitPrice) }}<br />信息费 {{ money(row.actualInformationFee) }}<br />工资 {{ money(row.initialSalary) }}</template>
          <template #status="{ row }"><strong :class="row.status === 'INVALID' || row.status === 'FAILED' ? 'import-error' : ''">{{ stateName[row.status] || row.status }}</strong></template>
          <template #taskNo="{ row }">{{ row.taskNo || row.message || '待确认' }}</template>
        </DataTable>
        <PaginationBar :page-no="rowPage" :page-size="50" :total="current.rows?.length || 0" @change="rowPage = $event" />
      </template>
      <EmptyState v-else title="尚未上传任务文件" description="先下载系统模板并填写，再选择真实 Excel 文件预览。" />
    </section>
    <section v-else class="ui-section-card">
      <div class="ui-list-heading"><h2>历史导入</h2><button type="button" :disabled="loading" @click="loadHistory(historyPage)">刷新</button></div>
      <DataTable :columns="historyColumns" :rows="history" :loading="loading" empty-text="暂无任务导入记录">
        <template #status="{ row }">{{ stateName[row.status] || row.status }}</template>
        <template #confirmedAt="{ row }">{{ row.confirmedAt || '尚未确认' }}</template>
        <template #actions="{ row }"><button type="button" @click="showHistory(row)">逐行结果</button></template>
      </DataTable>
      <PaginationBar :page-no="historyPage" :page-size="20" :total="historyTotal" @change="loadHistory" />
    </section>
  </main>
</template>

<style scoped>
.import-upload { display: flex; align-items: end; flex-wrap: wrap; gap: 16px; margin: 18px 0; }
.import-upload label { display: grid; gap: 8px; font-weight: 600; }
.import-error { color: #a3342f; }
</style>
