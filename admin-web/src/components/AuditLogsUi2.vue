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
const modules = { ROUTE: '运输', TASK: '运输任务', TEMPLATE: '线路模板', DISPATCH: '调度', APPROVAL: '审批', EXPENSE: '费用', TIRE: '轮胎', SALARY: '工资', DRIVER: '司机', MASTER: '基础资料', SYSTEM: '系统', IMPORT: '导入', SETTLEMENT: '月结' };
const operations = {
  CREATE: '新增', UPDATE: '修改', DELETE: '删除', EDIT: '人工维护',
  APPROVE: '同意当前审批步骤', RETURN_APPLICANT: '打回申请人', RETURN_NODE: '打回指定步骤', RETURN_STEP: '打回指定步骤',
  REVERSE: '费用冲销', ATTRIBUTION: '调整费用归属', POST_ITEMS: '审批通过后落费用',
  REPLACE_PERMISSIONS: '修改角色权限', CHANGE_TYPE: '修改司机类型',
  NEW_VERSION: '创建审批流程新版本', PUBLISH: '发布审批流程', RESUBMIT: '修改后重新提交',
  PREVIEW: '预览导入', IMPORT: '正式入库', IMPORT_PUBLISH: '导入并发布任务', CONFIRM: '确认调度批次',
  ENABLE: '启用线路模板', DISABLE: '停用线路模板', APPROVAL_CLAIM: '审批通过后申领轮胎',
  TASK_AMOUNT_ADJUST: '调整任务金额', GENERATE: '生成月结版本'
};
const businessNames = {
  EXPENSE: '费用', APPROVAL: '审批', INSTANCE: '审批实例', FLOW: '审批流程', TEMPLATE: '审批模板',
  ROLE: '角色', DRIVER: '司机', TIRE: '轮胎', TIRE_REQUEST: '换胎申请', TRANSPORT_TASK: '运输任务',
  TRANSPORT_PARTICIPANT: '任务参与司机', ROUTE_TEMPLATE: '线路模板', DISPATCH_BATCH: '调度批次',
  SETTLEMENT_VERSION: '月结版本', expense_entry: '费用单', transport_task: '运输任务',
  transport_participant: '任务参与司机', tire_request: '换胎申请', sys_user: '用户'
};
const fieldNames = { status: '状态', tireNo: '胎号', tire_no: '胎号', barcode: '条码',
  materialDescription: '物料描述', material_description: '物料描述', arrivedAt: '到货时间', arrived_at: '到货时间',
  amount: '金额', salary: '工资', plateNo: '车牌', reason: '原因', driverName: '司机', taskNo: '任务号' };
function moduleName(value) { return modules[String(value || '').toUpperCase()] || (value ? '其他业务模块' : '—'); }
function businessName(value) {
  if (businessNames[value]) return businessNames[value];
  const label = displayValue('businessType', value);
  return label && label !== value ? label : (value ? '其他业务对象' : '—');
}
function operationName(value) { return operations[value] || (value ? '其他业务操作' : '—'); }
function displayTime(value) { return value ? String(value).replace('T', ' ').replace(/\.\d+$/, '') : '—'; }
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
function fieldName(value) { return fieldNames[value] || '业务字段'; }
function short(value) {
  if (value == null) return '—';
  if (typeof value === 'object') return Array.isArray(value) ? `共 ${value.length} 项` : '业务资料已变化';
  const raw = String(value);
  const mapped = displayValue('status', raw);
  const text = mapped === raw && /^[A-Z][A-Z0-9_]*$/.test(raw) ? '业务状态已变化' : mapped;
  return text.length > 140 ? `${text.slice(0, 140)}…` : text;
}
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
    <div class="ui-list-heading"><h2>日志记录</h2><span class="muted">只读 · 保留完整操作记录</span></div>
    <FilterBar><label>模块<select v-model="filters.module"><option value="">全部模块</option><option v-for="(name, code) in modules" :key="code" :value="code">{{ name }}</option></select></label><label>业务类型<select v-model="filters.businessType"><option value="">全部业务</option><option v-for="(name, code) in businessNames" :key="code" :value="code">{{ name }}</option></select></label><label>开始时间<input v-model="filters.startTime" type="datetime-local" /></label><label>结束时间<input v-model="filters.endTime" type="datetime-local" /></label><button class="primary" type="button" @click="load(1)">查询</button></FilterBar>
    <DataTable v-if="!error" :columns="[{key:'operationTime',label:'时间'},{key:'module',label:'模块'},{key:'businessType',label:'业务对象'},{key:'operationType',label:'操作'},{key:'operatorPhone',label:'操作人'},{key:'reason',label:'原因'},{key:'actions',label:'操作'}]" :rows="rows" :loading="loading" empty-text="没有符合条件的操作日志">
      <template #operationTime="{value}">{{ displayTime(value) }}</template><template #module="{value}">{{ moduleName(value) }}</template><template #businessType="{value}">{{ businessName(value) }}</template><template #operationType="{value}">{{ operationName(value) }}</template><template #operatorPhone="{value}">{{ value || '账号已移除' }}</template><template #reason="{value}">{{ value || '—' }}</template><template #actions="{row}"><button type="button" @click="selected=row">查看变化</button></template>
    </DataTable><PaginationBar v-if="!error" :page-no="page" :page-size="20" :total="total" @change="load" />
    <DetailDrawer :open="!!selected" title="操作记录" @close="selected=null"><template v-if="selected"><dl class="ui-detail-grid"><dt>业务</dt><dd>{{ businessName(selected.businessType) }} · {{ moduleName(selected.module) }}</dd><dt>操作</dt><dd>{{ operationName(selected.operationType) }}</dd><dt>操作时间</dt><dd>{{ selected.operationTime }}</dd><dt>操作人</dt><dd>{{ selected.operatorPhone || '账号已移除' }}</dd><dt>原因</dt><dd>{{ selected.reason || '未填写' }}</dd><dt>原始动作代码</dt><dd><code>{{ selected.operationType || '—' }}</code></dd><dt>原始业务类型</dt><dd><code>{{ selected.businessType || '—' }}</code></dd></dl><h3>前后变化</h3><div v-if="!changedFields(selected).length" class="ui-empty">无可展示的字段变化</div><div v-else class="ui-audit-changes"><div v-for="item in changedFields(selected)" :key="item.key"><strong>{{ fieldName(item.key) }}</strong><p>{{ short(item.before) }} → {{ short(item.after) }}</p></div></div></template></DetailDrawer>
  </section>
</template>
