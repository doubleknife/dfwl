<script setup>
import { ref } from 'vue';
import { settlementApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { displayValue } from '../utils/displayLabels.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import StatusTag from '../components/StatusTag.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ErrorState from '../components/ErrorState.vue';
import { money, settlementFactSummary } from './ui2DataModel.js';

const month = ref(new Date().toISOString().slice(0, 7));
const versions = ref([]); const selected = ref(null); const details = ref([]); const diffs = ref([]);
const activeTab = ref('details'); const page = ref(1); const loading = ref(false); const error = ref(''); const notice = ref(''); const generating = ref(false);
async function load() {
  if (!month.value) { error.value = '请选择月结月份'; return; }
  loading.value = true; error.value = '';
  try { versions.value = await settlementApi.versions(month.value) || []; selected.value = null; details.value = []; diffs.value = []; }
  catch (cause) { error.value = cause.message || '月结版本读取失败'; }
  finally { loading.value = false; }
}
async function open(row) {
  loading.value = true; error.value = '';
  try { const [items, changes] = await Promise.all([settlementApi.details(row.id), settlementApi.diff(row.id)]); selected.value = row; details.value = items || []; diffs.value = changes || []; activeTab.value = 'details'; page.value = 1; }
  catch (cause) { error.value = cause.message || '月结详情读取失败'; }
  finally { loading.value = false; }
}
async function generate() {
  generating.value = false; loading.value = true; error.value = '';
  try { await settlementApi.generate(month.value); notice.value = `${month.value} 的新版本已生成；历史版本不变。`; await load(); }
  catch (cause) { error.value = cause.message || '月结生成失败'; }
  finally { loading.value = false; }
}
</script>
<template>
  <div class="ui-page-container"><PageHeader title="月度结算" description="按月份查看只读历史版本；新版本反映当前事实，不改写旧版本。" /><ErrorState v-if="error" :message="error" @retry="selected ? open(selected) : load()" /><p v-if="notice" role="status" class="ui-notice">{{ notice }}</p>
    <section v-if="!selected" class="ui-section-card"><div class="ui-list-heading"><h2>月结版本</h2><button v-if="can('settlement:generate')" class="primary" type="button" :disabled="loading || !month" @click="generating=true">生成新版本</button></div><div class="ui-filter-inline"><label>结算月份<input v-model="month" type="month" /></label><button type="button" :disabled="loading" @click="load">查询</button></div><DataTable :columns="[{key:'versionNo',label:'版本'},{key:'totalIncome',label:'收入',numeric:true},{key:'routeExpense',label:'线路费用',numeric:true},{key:'dailyExpense',label:'日常费用',numeric:true},{key:'driverSalary',label:'工资',numeric:true},{key:'totalProfit',label:'利润',numeric:true},{key:'generatedByPhone',label:'生成人'},{key:'generatedAt',label:'生成时间'},{key:'actions',label:'操作'}]" :rows="versions" :loading="loading" empty-text="请选择月份查询月结版本"><template #versionNo="{value}">V{{ value }}</template><template #totalIncome="{value}">{{ money(value) }}</template><template #routeExpense="{value}">{{ money(value) }}</template><template #dailyExpense="{value}">{{ money(value) }}</template><template #driverSalary="{value}">{{ money(value) }}</template><template #totalProfit="{value}">{{ money(value) }}</template><template #actions="{row}"><button type="button" @click="open(row)">查看详情</button></template></DataTable></section>
    <section v-else class="ui-workflow"><button type="button" @click="selected=null">← 返回版本列表</button><div class="ui-section-card"><div class="ui-list-heading"><div><h2>{{ selected.yearMonth }} · V{{ selected.versionNo }}</h2><p class="muted">生成于 {{ selected.generatedAt }} · 只读历史快照</p></div><StatusTag value="COMPLETED" /></div><div class="ui-summary-row"><span>收入 <strong>{{ money(selected.totalIncome) }}</strong></span><span>费用与工资 <strong>{{ money(Number(selected.routeExpense || 0)+Number(selected.dailyExpense || 0)+Number(selected.driverSalary || 0)) }}</strong></span><span>利润 <strong>{{ money(selected.totalProfit) }}</strong></span></div></div><div class="ui-section-card"><div class="ui-subtabs"><button type="button" :class="{active:activeTab==='details'}" @click="activeTab='details';page=1">版本明细</button><button type="button" :class="{active:activeTab==='diff'}" @click="activeTab='diff';page=1">与前版差异</button></div><DataTable v-if="activeTab==='details'" :columns="[{key:'factType',label:'事实类型'},{key:'snapshotJson',label:'业务摘要'},{key:'incomeAmount',label:'收入',numeric:true},{key:'expenseAmount',label:'支出',numeric:true},{key:'profitAmount',label:'利润',numeric:true},{key:'advanced',label:'高级信息'}]" :rows="details.slice((page-1)*20,page*20)" empty-text="该版本没有明细"><template #factType="{value}">{{ displayValue('factType',value) }}</template><template #snapshotJson="{value,row}">{{ settlementFactSummary(row.factType, value) }}</template><template #incomeAmount="{value}">{{ money(value) }}</template><template #expenseAmount="{value}">{{ money(value) }}</template><template #profitAmount="{value}">{{ money(value) }}</template><template #advanced="{row}"><details class="ui-advanced"><summary>原始快照</summary><pre>{{ row.snapshotJson }}</pre></details></template></DataTable><DataTable v-else :columns="[{key:'changeType',label:'变化'},{key:'factType',label:'事实类型'},{key:'businessReason',label:'原因'},{key:'incomeDelta',label:'收入影响',numeric:true},{key:'expenseDelta',label:'支出影响',numeric:true},{key:'profitDelta',label:'利润影响',numeric:true},{key:'advanced',label:'高级信息'}]" :rows="diffs.slice((page-1)*20,page*20)" empty-text="与上一版本没有差异"><template #changeType="{value}">{{ displayValue('changeType',value) }}</template><template #factType="{value}">{{ displayValue('factType',value) }}</template><template #incomeDelta="{value}">{{ money(value) }}</template><template #expenseDelta="{value}">{{ money(value) }}</template><template #profitDelta="{value}">{{ money(value) }}</template><template #advanced="{row}"><details class="ui-advanced"><summary>前后快照</summary><pre>变更前：{{ row.beforeSnapshotJson }}
变更后：{{ row.afterSnapshotJson }}</pre></details></template></DataTable><PaginationBar :page-no="page" :page-size="20" :total="activeTab==='details' ? details.length : diffs.length" @change="page=$event" /></div></section>
    <ConfirmDialog :open="generating" title="生成月结新版本" :message="`确认生成 ${month} 的新版本？此操作会记录当前业务事实，已生成的历史版本保持不变。`" :busy="loading" @cancel="generating=false" @confirm="generate" />
  </div>
</template>
