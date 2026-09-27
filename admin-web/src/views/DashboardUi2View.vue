<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { dashboardApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { navigate } from '../router/routes.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import ErrorState from '../components/ErrorState.vue';
import { dashboardMetrics, money } from './ui2DataModel.js';

const state = reactive({ summary: {}, profit: [], routes: [], expenses: [], approvals: [] });
const loading = ref(false); const error = ref('');
const month = computed(() => state.summary.month || {});
const year = computed(() => state.summary.year || {});
const primary = computed(() => dashboardMetrics(state.summary));
const todos = computed(() => [
  { label: '待审批', count: state.approvals.filter(row => ['PENDING','IN_PROGRESS'].includes(row.status)).length, route: 'approvals', permission: 'approval:process' },
  { label: '未发布线路', count: state.routes.filter(row => row.status === 'UNPUBLISHED').length, route: 'routes', permission: 'route:list' },
  { label: '待归属费用', count: state.expenses.filter(row => row.status === 'PENDING_ATTRIBUTION').length, route: 'expenses', permission: 'expense:list' }
].filter(item => can(item.permission)));
async function load() {
  loading.value = true; error.value = '';
  try {
    const [summary, profit, routes, expenses, approvals] = await Promise.all([
      dashboardApi.summary(), can('report:profit') ? dashboardApi.profit() : [],
      can('route:list') ? dashboardApi.routes() : {records:[]},
      can('expense:list') ? dashboardApi.expenses() : {records:[]},
      can('approval:history:view') || can('approval:process') ? dashboardApi.approvals() : {records:[]}
    ]);
    state.summary = summary || {}; state.profit = profit || [];
    state.routes = routes.records || []; state.expenses = expenses.records || []; state.approvals = approvals.records || [];
  } catch (cause) { error.value = cause.message || '看板读取失败'; }
  finally { loading.value = false; }
}
onMounted(load);
</script>
<template>
  <div class="ui-page-container">
    <PageHeader title="首页看板" description="先看本月经营与待办，再查看趋势和实时线路。" /><ErrorState v-if="error" :message="error" @retry="load" />
    <div v-if="loading" class="ui-loading">正在读取经营概况…</div>
    <template v-else-if="!error"><div class="ui-metric-grid"><article v-for="item in primary" :key="item.label" class="ui-metric"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></article></div>
      <section class="ui-section-card"><div class="ui-list-heading"><h2>需要处理</h2><button type="button" @click="load">刷新</button></div><div class="ui-todo-grid"><button v-for="item in todos" :key="item.route" type="button" @click="navigate(item.route)"><span>{{ item.label }}</span><strong>{{ item.count }}</strong><small>查看 →</small></button><p v-if="!todos.length" class="muted">当前账号没有可查看的待办。</p></div><p class="muted">数字来自近期记录预览；进入对应页面查看完整列表。</p></section>
      <section class="ui-section-card"><h2>月度趋势</h2><DataTable :columns="[{key:'monthLabel',label:'月份'},{key:'incomeAmount',label:'收入',numeric:true},{key:'expenseAmount',label:'支出',numeric:true},{key:'profitAmount',label:'利润',numeric:true}]" :rows="state.summary.monthlyTrend || []" empty-text="暂无月度经营数据"><template #incomeAmount="{value}">{{ money(value) }}</template><template #expenseAmount="{value}">{{ money(value) }}</template><template #profitAmount="{value}">{{ money(value) }}</template></DataTable></section>
      <section v-if="can('report:profit')" class="ui-section-card"><div class="ui-list-heading"><h2>实时线路利润</h2><button type="button" @click="navigate('reports')">查看经营报表</button></div><DataTable :columns="[{key:'routeNo',label:'线路'},{key:'customerName',label:'客户'},{key:'driverName',label:'司机'},{key:'incomeAmount',label:'收入',numeric:true},{key:'routeExpense',label:'费用',numeric:true},{key:'driverSalary',label:'工资',numeric:true},{key:'profitAmount',label:'利润',numeric:true}]" :rows="state.profit.slice(0, 8)" empty-text="暂无已完成线路"><template #incomeAmount="{value}">{{ money(value) }}</template><template #routeExpense="{value}">{{ money(value) }}</template><template #driverSalary="{value}">{{ money(value) }}</template><template #profitAmount="{value}">{{ money(value) }}</template></DataTable></section>
      <p class="ui-secondary-stats">年度收入 {{ money(year.incomeAmount) }} · 年度利润 {{ money(year.companyTotalProfit) }}</p>
    </template>
  </div>
</template>
