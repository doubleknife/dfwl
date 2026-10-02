<script setup>
import { onMounted, reactive, ref } from 'vue';
import { v3FinanceApi } from '../api/modules.js';
import { navigate } from '../router/routes.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import ErrorState from '../components/ErrorState.vue';

const today = new Date();
const filters = reactive({ month: `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}` });
const stats = ref(null); const loading = ref(false); const error = ref('');
const money = (v) => `¥ ${Number(v || 0).toFixed(2)}`;
async function load() {
  loading.value = true; error.value = '';
  try { stats.value = await v3FinanceApi.dashboard(filters.month); }
  catch (cause) { error.value = cause.message || '看板加载失败'; }
  finally { loading.value = false; }
}
onMounted(load);
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="首页看板" description="按正式经营事实查看本月进展和当前待处理事项。"><template #actions><button type="button" @click="load">刷新</button></template></PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load" />
    <FilterBar><label>经营月份<input v-model="filters.month" type="month" /></label><button type="button" @click="load">查看</button></FilterBar>
    <p v-if="loading" class="ui-loading">正在加载看板…</p>
    <template v-else-if="stats">
      <section class="v3-summary" aria-label="本月经营"><div><small>已完成任务</small><strong>{{ stats.completedTaskCount }}</strong></div><div><small>运输收入</small><strong>{{ money(stats.incomeTotal) }}</strong></div><div><small>总支出</small><strong>{{ money(stats.totalExpense) }}</strong></div><div><small>利润</small><strong>{{ money(stats.profitTotal) }}</strong></div></section>
      <section class="ui-section"><h2>需要处理</h2><div class="v3-summary"><div><small>待处理审批</small><strong>{{ stats.pendingApprovalCount }}</strong></div><div><small>待发布任务</small><strong>{{ stats.unpublishedTaskCount }}</strong></div><div><small>未归属任务的正式费用</small><strong>{{ stats.unassignedExpenseCount }} 笔</strong></div></div>
        <div class="ui-page-actions"><button v-if="can('approval:process')" type="button" @click="navigate('approvals')">进入审批中心</button><button v-if="can('dispatch:view')" type="button" @click="navigate('dispatch')">进入调度工作台</button><button v-if="can('expense:ledger:view')" type="button" @click="navigate('expense-ledger')">查看费用台账</button></div></section>
      <section class="ui-section"><h2>经营口径</h2><p>日常费用即使尚未归属运输任务，也计入公司总支出；归属变化不重复增加公司费用。</p></section>
    </template>
  </main>
</template>
