<script setup>
import { onMounted, reactive, ref } from 'vue';
import { importApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { displayValue } from '../utils/displayLabels.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import StatusTag from '../components/StatusTag.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ErrorState from '../components/ErrorState.vue';

const tab = ref(can('import:preview') ? 'new' : 'history');
const step = ref(1);
const form = reactive({ businessType: 'ROUTE', file: null, attachmentId: null });
const task = ref(null);
const history = ref([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const commitConfirm = ref(false);
const labels = { ROUTE: '线路', TIRE: '轮胎', EXPENSE: '费用', SALARY: '工资' };

async function loadHistory(next = 1) {
  if (!can('import:history')) return;
  loading.value = true; error.value = '';
  try { const result = await importApi.history(next, 20); history.value = result.records || []; total.value = Number(result.total || 0); page.value = next; }
  catch (cause) { error.value = cause.message || '导入历史加载失败'; }
  finally { loading.value = false; }
}
function changeType(value) { form.businessType = value; form.file = null; form.attachmentId = null; task.value = null; step.value = 1; }
function chooseFile(event) { form.file = event.target.files?.[0] || null; form.attachmentId = null; task.value = null; step.value = 1; }
async function preview() {
  if (!form.file) { error.value = '请先选择导入文件'; return; }
  loading.value = true; error.value = '';
  try {
    const body = new FormData();
    body.append('file', form.file); body.append('ownerType', 'IMPORT'); body.append('ownerId', '0'); body.append('purpose', 'IMPORT_FILE');
    const attachment = await importApi.uploadFile(body);
    form.attachmentId = attachment.id;
    task.value = await importApi.preview({ businessType: form.businessType, templateId: null, originalFileId: form.attachmentId });
    step.value = 2;
    await loadHistory(page.value);
  } catch (cause) { error.value = cause.message || '文件上传或预览失败，请重新选择文件'; }
  finally { loading.value = false; }
}
async function commit() {
  if (!task.value || !can('import:commit')) return;
  loading.value = true; error.value = '';
  try { task.value = await importApi.commit(task.value.id); commitConfirm.value = false; step.value = 3; await loadHistory(1); }
  catch (cause) { error.value = cause.message || '提交失败'; }
  finally { loading.value = false; }
}
async function exportFailures(row) {
  error.value = '';
  try {
    const blob = await importApi.failures(row.id);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a'); link.href = url; link.download = `import-failures-${row.batchNo || row.id}.csv`; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) { error.value = cause.message || '失败结果下载失败'; }
}
onMounted(() => { if (can('import:history')) loadHistory(); });
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="导入中心" description="先上传文件并校验，再确认逐行提交。历史任务可独立查看。" />
    <ErrorState v-if="error" :message="error" @retry="tab === 'history' ? loadHistory(page) : error = ''" />
    <div class="ui-subtabs"><button v-if="can('import:preview')" type="button" :class="{active:tab==='new'}" @click="tab='new'">新建导入</button><button v-if="can('import:history')" type="button" :class="{active:tab==='history'}" @click="tab='history'; loadHistory(page)">历史任务</button></div>
    <section v-if="tab === 'new' && can('import:preview')" class="ui-section-card">
      <div class="ui-step-line"><strong :class="{active:step===1}">① 选择文件</strong><strong :class="{active:step===2}">② 校验预览</strong><strong :class="{active:step===3}">③ 提交结果</strong></div>
      <template v-if="step === 1"><h2>选择业务和文件</h2><div class="ui-form-grid"><label class="ui-form-field">导入业务<select :value="form.businessType" @change="changeType($event.target.value)"><option v-for="(label,key) in labels" :key="key" :value="key">{{ label }}</option></select></label><label class="ui-form-field">Excel 或 CSV 文件<input type="file" accept=".xlsx,.xls,.csv" @change="chooseFile" /></label></div><p class="muted">文件上传后由服务端解析并校验，您无需填写附件编号或模板编号。</p><button class="primary" type="button" :disabled="!form.file || loading" @click="preview">{{ loading ? '正在上传并校验…' : '上传并预览' }}</button></template>
      <template v-else-if="step === 2 && task"><div class="ui-list-heading"><div><h2>校验预览</h2><p>{{ labels[task.businessType] }} · {{ form.file?.name || task.batchNo }}</p></div><button type="button" @click="step=1; task=null; form.file=null; form.attachmentId=null">重新选文件</button></div><div class="ui-summary-row"><span>总行数 <strong>{{ task.totalCount }}</strong></span><span>通过 <strong>{{ task.successCount }}</strong></span><span>失败 <strong>{{ task.failureCount }}</strong></span></div><DataTable :columns="[{key:'rowNo',label:'行号'},{key:'previewStatus',label:'校验'},{key:'previewErrorMessage',label:'原因'},{key:'businessUniqueKey',label:'业务标识'}]" :rows="task.rows || []" row-key="rowNo" empty-text="没有预览行"><template #previewStatus="{value}"><StatusTag :value="value" /></template><template #previewErrorMessage="{value}">{{ value || '—' }}</template></DataTable><div class="ui-dialog-actions"><button v-if="can('import:failure:export') && task.failureCount" type="button" @click="exportFailures(task)">下载失败明细</button><button v-if="can('import:commit')" class="primary" type="button" :disabled="loading || !task.successCount" @click="commitConfirm=true">确认提交 {{ task.successCount }} 行</button></div></template>
      <template v-else-if="task"><h2>导入已提交</h2><p>批次 {{ task.batchNo }}：成功 {{ task.successCount }} 行，未发布 {{ task.unpublishedCount }} 行，失败 {{ task.failureCount }} 行。</p><button v-if="can('import:history')" class="primary" type="button" @click="tab='history'; loadHistory(1)">查看历史任务</button></template>
    </section>
    <section v-if="tab === 'history' && can('import:history')" class="ui-section-card"><div class="ui-list-heading"><h2>导入历史</h2><button type="button" @click="loadHistory(page)">刷新</button></div><DataTable v-if="!error" :columns="[{key:'batchNo',label:'批次'},{key:'businessType',label:'业务'},{key:'status',label:'状态'},{key:'totalCount',label:'总行数',numeric:true},{key:'successCount',label:'成功',numeric:true},{key:'failureCount',label:'失败',numeric:true},{key:'uploadedAt',label:'上传时间'},{key:'actions',label:'操作'}]" :rows="history" :loading="loading"><template #businessType="{value}">{{ displayValue('businessType',value) }}</template><template #status="{value}"><StatusTag :value="value" /></template><template #actions="{row}"><button v-if="can('import:failure:export') && row.failureCount" type="button" @click="exportFailures(row)">下载失败明细</button><span v-else>—</span></template></DataTable><PaginationBar v-if="!error" :page-no="page" :page-size="20" :total="total" @change="loadHistory" /></section>
    <ConfirmDialog :open="commitConfirm" title="确认提交导入" :message="`确认提交 ${labels[task?.businessType]}批次 ${task?.batchNo || ''} 的 ${task?.successCount || 0} 条通过校验的记录？提交后将逐行落库，失败行保留错误记录。`" :busy="loading" @cancel="commitConfirm=false" @confirm="commit" />
  </div>
</template>
