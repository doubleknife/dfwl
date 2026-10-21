<script setup>
import { onMounted, ref } from 'vue';
import { v3ApprovalApi, v3ExpenseLedgerApi } from '../api/modules.js';
import { navigate } from '../router/routes.js';
import { permissions } from '../stores/auth.js';
import { overviewCardAccess } from '../router/pagePermissionModel.js';
import PageHeader from '../components/PageHeader.vue';
import ErrorState from '../components/ErrorState.vue';
import EmptyState from '../components/EmptyState.vue';

const loading = ref(false);
const error = ref('');
const approvalCount = ref(null);
const attributionCount = ref(null);
const canApprove = () => overviewCardAccess(permissions()).approvals;
const canAttribute = () => overviewCardAccess(permissions()).attribution;
function requiredCount(value) {
  if (value === null || value === undefined || !Number.isFinite(Number(value)))
    throw new Error('待办统计暂不可用，请刷新后重试');
  return Number(value);
}

async function load() {
  loading.value = true;
  error.value = '';
  approvalCount.value = null;
  attributionCount.value = null;
  try {
    const [approvals, expenses] = await Promise.all([
      canApprove() ? v3ApprovalApi.list('TODO', 1) : null,
      canAttribute() ? v3ExpenseLedgerApi.pendingAttributionSummary() : null
    ]);
    if (approvals) approvalCount.value = requiredCount(approvals.total);
    if (expenses) attributionCount.value = requiredCount(expenses.entry_count);
  } catch (cause) {
    error.value = cause.message || '待办加载失败，请重试';
  } finally {
    loading.value = false;
  }
}
onMounted(() => load());
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="待办概览" description="分别进入审批中心和费用管理处理当前有权办理的事项。">
      <template #actions><button type="button" :disabled="loading" @click="load">刷新</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load" />
    <p v-if="loading" class="ui-loading">正在加载待办…</p>
    <section v-else-if="!error" class="todo-overview-grid">
      <button v-if="canApprove()" type="button" class="ui-todo-card" @click="navigate('approvals-todo')">
        <span>我的待审批</span><strong>{{ approvalCount }}</strong><small>进入审批中心查看并处理 →</small>
      </button>
      <button v-if="canAttribute()" type="button" class="ui-todo-card" @click="navigate('expense-unassigned')">
        <span>待归属费用</span><strong>{{ attributionCount }}</strong><small>进入费用管理处理归属 →</small>
      </button>
    </section>
    <EmptyState v-if="!loading && !error && !canApprove() && !canAttribute()" title="暂无可用待办入口" description="当前账号没有审批处理或费用归属权限。" />
  </main>
</template>

<style scoped>
.todo-overview-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; width:min(100%,980px); }
.todo-overview-grid .ui-todo-card { width:100%; cursor:pointer; }
.todo-overview-grid .ui-todo-card small { justify-self:end; text-align:right; }
.todo-overview-grid .ui-todo-card:focus-visible { outline:3px solid var(--ui-primary); outline-offset:3px; }
@media (max-width:640px) { .todo-overview-grid { grid-template-columns:1fr; } }
</style>
