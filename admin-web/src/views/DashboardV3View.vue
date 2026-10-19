<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { v3FinanceApi } from '../api/modules.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import ErrorState from '../components/ErrorState.vue';
import SummaryCards from '../components/SummaryCards.vue';
import FormSection from '../components/FormSection.vue';
import EmptyState from '../components/EmptyState.vue';

const today = new Date();
const filters = reactive({ month: `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}` });
const stats = ref(null); const loading = ref(false); const error = ref('');
const money = (v) => `¥ ${Number(v || 0).toFixed(2)}`;
const businessSummary = computed(() => stats.value ? [
  { label: '已完成任务', value: stats.value.completedTaskCount },
  { label: '运输收入', value: money(stats.value.incomeTotal) },
  { label: '正式费用净额', value: money(stats.value.expenseEntryTotal) },
  { label: '参与司机工资', value: money(stats.value.salaryTotal) },
  { label: '总支出', value: money(stats.value.totalExpense) },
  { label: '利润', value: money(stats.value.profitTotal) }
] : []);
const noBusinessFacts = computed(() => stats.value && Number(stats.value.completedTaskCount || 0) === 0
  && Number(stats.value.incomeTotal || 0) === 0 && Number(stats.value.expenseEntryTotal || 0) === 0);
async function load() {
  loading.value = true; error.value = '';
  try {
    stats.value = await v3FinanceApi.dashboard(filters.month);
  }
  catch (cause) { error.value = cause.message || '看板加载失败'; }
  finally { loading.value = false; }
}
onMounted(load);
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="经营概览" description="按正式经营事实查看本月进展；待处理事项在待办概览查看。"><template #actions><button type="button" @click="load">刷新</button></template></PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load" />
    <FilterBar><label>经营月份<input v-model="filters.month" type="month" /></label><button type="button" @click="load">查看</button></FilterBar>
    <p v-if="loading" class="ui-loading">正在加载看板…</p>
    <template v-else-if="stats">
      <EmptyState v-if="noBusinessFacts" title="本月尚无经营事实" description="已完成任务、收入和正式费用均暂无记录；下方显示后端返回的经营指标。" />
      <SummaryCards label="本月经营" class="dashboard-main-summary" :items="businessSummary" />
      <FormSection title="经营口径"><p>日常费用即使尚未归属运输任务，也计入公司总支出；归属变化不重复增加公司费用。</p></FormSection>
    </template>
  </main>
</template>

<style scoped>
.dashboard-main-summary { grid-template-columns:repeat(3,minmax(0,1fr)); }
@media (max-width: 640px) { .dashboard-main-summary { grid-template-columns:1fr; } }
</style>
