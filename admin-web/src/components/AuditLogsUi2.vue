<script setup>
import { onMounted, reactive, ref } from 'vue';
import { systemApi } from '../api/modules.js';
import { displayValue } from '../utils/displayLabels.js';
import FilterBar from './FilterBar.vue';
import DataTable from './DataTable.vue';
import PaginationBar from './PaginationBar.vue';
import DetailDrawer from './DetailDrawer.vue';
import ErrorState from './ErrorState.vue';

const filters = reactive({ module: '', businessType: '', startTime: '', endTime: '' });
const rows = ref([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const selected = ref(null);
const modules = { ROUTE: '线路', APPROVAL: '审批', EXPENSE: '费用', TIRE: '轮胎', SALARY: '工资', MASTER: '基础资料', SYSTEM: '系统', IMPORT: '导入' };
const operations = { CREATE: '新增', UPDATE: '修改', DELETE: '删除', APPROVE: '审批通过', RETURN_APPLICANT: '打回申请人', RETURN_NODE: '打回节点', REVERSE: '冲销' };
const businessNames = { expense_entry: '费用单', route_task: '线路', tire_request: '换胎申请', driver_salary_entry: '司机工资', sys_user: '用户' };
function moduleName(value) { return modules[String(value || '').toUpperCase()] || value || '—'; }
function businessName(value) { return businessNames[value] || displayValue('businessType', value); }
function operationName(value) { return operations[value] || value || '—'; }
function parseJson(value) { try { return value ? JSON.parse(value) : null; } catch { return null; } }
function changedFields(row) {
  const before = parseJson(row.beforeJson);
  const after = parseJson(row.afterJson);
  if (!before && !after) return [];
  const left = before && typeof before === 'object' ? before : {};
  const right = after && typeof after === 'object' ? after : {};
  return [...new Set([...Object.keys(left), ...Object.keys(right)])].filter((key) => JSON.stringify(left[key]) !== JSON.stringify(right[key])).slice(0, 20)
    .map((key) => ({ key, before: left[key], after: right[key] }));
}
function short(value) { if (value == null) return '—'; const text = typeof value === 'object' ? JSON.stringify(value) : String(value); return text.length > 140 ? `${text.slice(0, 140)}…` : text; }
async function load(next = 1) {
  loading.value = true; error.value = '';
  try {
    const result = await systemApi.auditLogs({ ...filters, pageNo: next, pageSize: 20 });
    rows.value = result.records || []; total.value = Number(result.total || 0); page.value = next;
  } catch (cause) { error.value = cause.message || '日志加载失败'; }
  finally { loading.value = false; }
}
onMounted(() => load());
</script>

<template>
  <section class="ui-page-container">
    <ErrorState v-if="error" :message="error" @retry="load(page)" />
    <div class="ui-list-heading"><h2>操作日志</h2><span class="muted">保留完整操作记录</span></div>
    <FilterBar><label>模块<select v-model="filters.module"><option value="">全部模块</option><option v-for="(name, code) in modules" :key="code" :value="code">{{ name }}</option></select></label><label>业务类型<select v-model="filters.businessType"><option value="">全部业务</option><option v-for="(name, code) in businessNames" :key="code" :value="code">{{ name }}</option></select></label><label>开始时间<input v-model="filters.startTime" type="datetime-local" /></label><label>结束时间<input v-model="filters.endTime" type="datetime-local" /></label><button class="primary" type="button" @click="load(1)">查询</button></FilterBar>
    <DataTable v-if="!error" :columns="[{key:'operationTime',label:'时间'},{key:'module',label:'模块'},{key:'businessType',label:'业务'},{key:'operationType',label:'操作'},{key:'operatorPhone',label:'操作人'},{key:'reason',label:'原因'},{key:'actions',label:'操作'}]" :rows="rows" :loading="loading" empty-text="没有符合条件的操作日志">
      <template #module="{value}">{{ moduleName(value) }}</template><template #businessType="{value}">{{ businessName(value) }}</template><template #operationType="{value}">{{ operationName(value) }}</template><template #operatorPhone="{value}">{{ value || '账号已移除' }}</template><template #reason="{value}">{{ value || '—' }}</template><template #actions="{row}"><button type="button" @click="selected=row">查看变化</button></template>
    </DataTable><PaginationBar v-if="!error" :page-no="page" :page-size="20" :total="total" @change="load" />
    <DetailDrawer :open="!!selected" title="操作记录" @close="selected=null"><template v-if="selected"><dl class="ui-detail-grid"><dt>业务</dt><dd>{{ businessName(selected.businessType) }} · {{ moduleName(selected.module) }}</dd><dt>操作</dt><dd>{{ operationName(selected.operationType) }}</dd><dt>操作时间</dt><dd>{{ selected.operationTime }}</dd><dt>操作人</dt><dd>{{ selected.operatorPhone || '账号已移除' }} <small class="muted">（编号 {{ selected.operatorId }}）</small></dd><dt>原因</dt><dd>{{ selected.reason || '未填写' }}</dd></dl><h3>前后变化</h3><div v-if="!changedFields(selected).length" class="ui-empty">无可展示的字段变化</div><div v-else class="ui-audit-changes"><div v-for="item in changedFields(selected)" :key="item.key"><strong>{{ item.key }}</strong><p>{{ short(item.before) }} → {{ short(item.after) }}</p></div></div><details class="ui-advanced"><summary>高级信息：原始记录</summary><p>业务对象编号 {{ selected.businessId ?? '—' }} · 日志编号 {{ selected.id }}</p><h4>变更前</h4><pre>{{ selected.beforeJson || '—' }}</pre><h4>变更后</h4><pre>{{ selected.afterJson || '—' }}</pre></details></template></DetailDrawer>
  </section>
</template>
