import { computed, reactive } from 'vue';
import Master from '../views/MasterDataUi2View.vue';
import Tasks from '../views/TransportTasksV3View.vue';
import Dispatch from '../views/DispatchWorkbenchV3View.vue';
import TaskImports from '../views/TaskImportsV3View.vue';
import Salaries from '../views/SalaryLedgerV3View.vue';
import Monthly from '../views/MonthlyOperationsV3View.vue';
import Approvals from '../views/ApprovalCenterV3View.vue';
import ExpenseApply from '../views/ExpenseApplicationV3View.vue';
import System from '../views/SystemUi1View.vue';
import Expenses from '../views/ExpenseLedgerV3View.vue';
import Finance from '../views/FinanceReportsV3View.vue';
import Settlement from '../views/SettlementV3View.vue';
import Dashboard from '../views/DashboardV3View.vue';
import Tires from '../views/TiresUi2View.vue';
import TireImports from '../views/TireImportsV3View.vue';
import Charging from '../views/ChargingImportsV3View.vue';
import { canAny } from '../stores/auth.js';

// All entries are permission-protected leaves; a group without leaves is hidden.
export const routes = [
  { key: 'dashboard', label: '经营看板', permission: 'report:company:view', component: Dashboard },
  { key: 'dispatch', label: '调度工作台', permission: 'dispatch:view', component: Dispatch },
  { key: 'routes', label: '运输任务', permission: 'task:view', component: Tasks },
  { key: 'templates', label: '线路模板', permission: 'template:view', component: Tasks, props: { initialSection: 'templates' } },
  { key: 'task-imports', label: '任务导入', permission: 'task:import:view', component: TaskImports },
  { key: 'dispatch-internal', label: '自有车辆月度台账', permission: 'dispatch:ledger:view', component: Monthly, props: { scope: 'INTERNAL' } },
  { key: 'dispatch-outsourced', label: '外协车辆月度台账', permission: 'dispatch:ledger:view', component: Monthly, props: { scope: 'OUTSOURCED' } },
  { key: 'expense-apply', label: '费用申请', permission: 'approval:create', component: ExpenseApply },
  { key: 'expense-ledger', label: '费用台账', permission: 'expense:ledger:view', component: Expenses },
  { key: 'expense-attribution', label: '费用归属', permission: 'expense:ledger:view', component: Expenses, props: { focus: 'ATTRIBUTION' } },
  { key: 'charging-companies', label: '充电公司模板', permission: 'charging:company:manage', component: Charging, props: { initialTab: 'companies' } },
  { key: 'charging-imports', label: '充电订单导入', permission: 'charging:import', component: Charging, props: { initialTab: 'import' } },
  { key: 'charging-history', label: '充电导入历史', permission: 'charging:view', component: Charging, props: { initialTab: 'history' } },
  { key: 'salaries', label: '工资台账', permission: 'salary:view', component: Salaries },
  { key: 'salary-transfers', label: '转派工资核对', permission: 'salary:view', component: Salaries, props: { initialSituation: 'TRANSFER' } },
  { key: 'attendance', label: '司机出勤', permission: 'attendance:view', component: Monthly, props: { scope: 'ATTENDANCE' } },
  { key: 'approvals-todo', label: '待我处理', permission: ['approval:process', 'approval:return'], component: Approvals, props: { initialScope: 'TODO' } },
  { key: 'approvals-mine', label: '我的申请', permission: 'approval:create', component: Approvals, props: { initialScope: 'MINE' } },
  { key: 'approvals-history', label: '审批记录', permission: 'approval:history:view', component: Approvals, props: { initialScope: 'HISTORY' } },
  { key: 'approvals-flows', label: '流程与模板配置', permission: 'approval:flow:manage', component: Approvals, props: { initialTab: 'flows' } },
  { key: 'drivers', label: '司机', permission: 'driver:list', component: Master, props: { initialTab: 'drivers' } },
  { key: 'vehicles', label: '车辆', permission: 'vehicle:list', component: Master, props: { initialTab: 'vehicles' } },
  { key: 'trailers', label: '车挂', permission: 'trailer:list', component: Master, props: { initialTab: 'trailers' } },
  { key: 'binding', label: '绑定关系', permission: 'vehicle:bindingHistory', component: Master, props: { initialTab: 'binding' } },
  { key: 'products', label: '货物', permission: 'template:view', component: Master, props: { initialTab: 'products' } },
  { key: 'tires', label: '轮胎库存', permission: 'tire:view', component: Tires },
  { key: 'tire-imports', label: '轮胎入库', permission: 'tire:import', component: TireImports },
  { key: 'finance-tasks', label: '任务经营', permission: 'report:task:view', component: Finance, props: { scope: 'TASK' } },
  { key: 'finance-templates', label: '线路模板经营', permission: 'report:template:view', component: Finance, props: { scope: 'TEMPLATE' } },
  { key: 'finance-company', label: '公司经营', permission: 'report:company:view', component: Finance, props: { scope: 'COMPANY' } },
  { key: 'finance-vehicles', label: '车辆支出', permission: 'expense:ledger:view', component: Expenses, props: { focus: 'VEHICLE' } },
  { key: 'finance-drivers', label: '司机支出', permission: 'expense:ledger:view', component: Expenses, props: { focus: 'DRIVER' } },
  { key: 'finance-energy', label: '能源费用', permission: 'expense:ledger:view', component: Expenses, props: { focus: 'ENERGY' } },
  { key: 'settlements', label: '月度结算', permission: 'settlement:view', component: Settlement },
  { key: 'users', label: '用户', permission: 'user:manage', component: System, props: { initialTab: 'users' } },
  { key: 'roles', label: '角色与权限', permission: 'role:manage', component: System, props: { initialTab: 'roles' } },
  { key: 'audit', label: '操作日志', permission: 'audit:view', component: System, props: { initialTab: 'audits' } },
  { key: 'import-history', label: '导入历史', permission: 'task:import:view', component: TaskImports, props: { initialTab: 'history' } }
];

export const navigation = [
  { key: 'workbench', label: '工作台', children: ['dashboard'] },
  { key: 'transport', label: '运输调度', children: ['dispatch', 'routes', 'templates', 'task-imports', { label: '调度中心', children: ['dispatch-internal', 'dispatch-outsourced'] }] },
  { key: 'expense-domain', label: '费用管理', children: ['expense-apply', 'expense-ledger', 'expense-attribution', 'charging-companies', 'charging-imports', 'charging-history'] },
  { key: 'wages', label: '工资管理', children: ['salaries', 'salary-transfers', 'attendance'] },
  { key: 'approvals', label: '审批中心', children: ['approvals-todo', 'approvals-mine', 'approvals-history', 'approvals-flows'] },
  { key: 'master', label: '车辆与基础资料', children: ['drivers', 'vehicles', 'trailers', 'binding', 'products', 'tires', 'tire-imports'] },
  { key: 'finance', label: '经营分析', children: ['finance-tasks', 'finance-templates', 'finance-company', 'finance-vehicles', 'finance-drivers', 'finance-energy', 'settlements'] },
  { key: 'system', label: '系统管理', children: ['users', 'roles', 'audit', 'import-history'] }
];

const fromHash = () => window.location.hash.startsWith('#/') ? window.location.hash.slice(2) : '';
export const routerStore = reactive({ current: fromHash() || localStorage.getItem('admin.currentRoute') || 'dashboard' });
export const visibleRoutes = computed(() => routes.filter((route) => canAny(route.permission)));
export const activeRoute = computed(() => visibleRoutes.value.find((route) => route.key === routerStore.current) || visibleRoutes.value[0] || null);
function permitted(children) {
  return children.map((item) => {
    if (typeof item === 'string') return visibleRoutes.value.find((route) => route.key === item) || null;
    const nested = permitted(item.children);
    return nested.length ? { ...item, children: nested } : null;
  }).filter(Boolean);
}
export const visibleNavigation = computed(() => navigation.map((item) => ({ ...item, children: permitted(item.children) })).filter((item) => item.children.length));
export function navigationTrail(key) {
  for (const group of visibleNavigation.value) for (const item of group.children) {
    if (item.key === key) return [group.label, item.label];
    if (item.children) {
      const leaf = item.children.find((child) => child.key === key);
      if (leaf) return [group.label, item.label, leaf.label];
    }
  }
  return [];
}
export function navigate(key) {
  if (!visibleRoutes.value.some((route) => route.key === key)) return;
  routerStore.current = key;
  localStorage.setItem('admin.currentRoute', key);
  if (window.location.hash !== `#/${key}`) window.location.hash = `/${key}`;
}
window.addEventListener('hashchange', () => { const key = fromHash(); if (key) routerStore.current = key; });
