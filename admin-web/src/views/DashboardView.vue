<script setup>
import { computed, onMounted, reactive } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { dashboardApi } from '../api/modules.js';
import { useRequest } from '../composables/useRequest.js';

const { loading, error, run } = useRequest();
const state = reactive({ summary: {}, profit: [], routes: [], expenses: [], approvals: [] });

const metricCards = computed(() => [
  ['本月收入', state.summary.monthIncome ?? state.summary.incomeAmount ?? 0],
  ['本月线路费用', state.summary.monthRouteExpense ?? state.summary.routeExpense ?? 0],
  ['本月日常费用', state.summary.monthDailyExpense ?? state.summary.dailyExpense ?? 0],
  ['本月线路利润', state.summary.monthRouteProfit ?? state.summary.routeProfit ?? 0],
  ['本月总利润', state.summary.monthCompanyTotalProfit ?? state.summary.companyTotalProfit ?? state.summary.totalProfit ?? 0],
  ['本月趟次', state.summary.monthRouteCount ?? state.summary.routeCount ?? 0],
  ['年度收入', state.summary.yearIncome ?? 0],
  ['年度总利润', state.summary.yearCompanyTotalProfit ?? 0]
]);

const pendingExpenses = computed(() => state.expenses.filter((item) => item.status === 'PENDING_ATTRIBUTION'));
const unpublishedRoutes = computed(() => state.routes.filter((item) => item.status === 'UNPUBLISHED'));
const pendingApprovals = computed(() => state.approvals.filter((item) => ['PENDING', 'IN_PROGRESS'].includes(item.status)));

async function load() {
  await run(async () => {
    const [summary, profit, routes, expenses, approvals] = await Promise.all([
      dashboardApi.summary(),
      dashboardApi.profit(),
      dashboardApi.routes(),
      dashboardApi.expenses(),
      dashboardApi.approvals()
    ]);
    state.summary = summary || {};
    state.profit = profit || [];
    state.routes = routes?.records || routes || [];
    state.expenses = expenses?.records || expenses || [];
    state.approvals = approvals?.records || approvals || [];
  });
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="metric-grid">
      <article v-for="[label, value] in metricCards" :key="label" class="metric-card">
        <span>{{ label }}</span>
        <strong>{{ value }}</strong>
      </article>
    </div>
    <PanelSection title="状态提示">
      <div class="status-row">
        <StatusBadge :value="`待审批 ${pendingApprovals.length}`" tone="warning" />
        <StatusBadge :value="`未发布线路 ${unpublishedRoutes.length}`" tone="info" />
        <StatusBadge :value="`待确认费用 ${pendingExpenses.length}`" tone="danger" />
        <button type="button" :disabled="loading" @click="load">刷新</button>
      </div>
    </PanelSection>
    <PanelSection title="月度趋势">
      <DataTable
        :columns="[
          { key: 'month', label: '月份' },
          { key: 'incomeAmount', label: '收入' },
          { key: 'expenseAmount', label: '支出' },
          { key: 'profitAmount', label: '利润' }
        ]"
        :rows="state.summary.monthlyTrend || []"
      />
    </PanelSection>
    <PanelSection title="实时线路利润">
      <DataTable
        :columns="[
          { key: 'routeNo', label: '线路' },
          { key: 'customerName', label: '客户' },
          { key: 'driverName', label: '司机' },
          { key: 'incomeAmount', label: '收入' },
          { key: 'routeExpense', label: '线路费用' },
          { key: 'driverSalary', label: '工资' },
          { key: 'profitAmount', label: '利润' }
        ]"
        :rows="state.profit"
      />
    </PanelSection>
  </div>
</template>
