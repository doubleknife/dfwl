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
import MyTodos from '../views/MyTodosV3View.vue';
import Tires from '../views/TiresUi2View.vue';
import TireImports from '../views/TireImportsV3View.vue';
import Charging from '../views/ChargingImportsV3View.vue';
import { permissions } from '../stores/auth.js';
import { navigation, filterNavigation, findNavigationTrail } from './navigationModel.js';
import { hasRouteAccess, todoOverviewPermissions } from './pagePermissionModel.js';

// All entries are permission-protected leaves; a group without leaves is hidden.
export const routes = [
  { key: 'dashboard', label: '经营概览', permission: 'PAGE_DASHBOARD', component: Dashboard },
  { key: 'my-todos', label: '待办概览', permission: todoOverviewPermissions, component: MyTodos },
  { key: 'dispatch', label: '批量调度', permission: 'PAGE_DISPATCH', component: Dispatch },
  { key: 'routes', label: '运输任务', permission: 'PAGE_ROUTES', component: Tasks },
  { key: 'templates', label: '线路模板', permission: 'PAGE_TEMPLATES', component: Tasks, props: { initialSection: 'templates' } },
  { key: 'task-imports', label: '任务导入', permission: 'PAGE_TASK_IMPORTS', component: TaskImports },
  { key: 'dispatch-internal', label: '自有车辆', permission: 'PAGE_DISPATCH_INTERNAL', component: Monthly, props: { scope: 'INTERNAL' } },
  { key: 'dispatch-outsourced', label: '外协车辆', permission: 'PAGE_DISPATCH_OUTSOURCED', component: Monthly, props: { scope: 'OUTSOURCED' } },
  { key: 'expense-apply', label: '费用申请', permission: 'PAGE_EXPENSE_APPLY', component: ExpenseApply },
  { key: 'expense-ledger', label: '费用台账', permission: 'PAGE_EXPENSE_LEDGER', component: Expenses },
  { key: 'expense-unassigned', label: '待归属费用', permission: 'PAGE_EXPENSE_UNASSIGNED', component: Expenses, props: { focus: 'UNASSIGNED' } },
  { key: 'charging-companies', label: '充电公司模板配置', permission: 'PAGE_CHARGING_COMPANIES', component: Charging, props: { initialTab: 'companies' } },
  { key: 'charging-imports', label: '充电订单导入', permission: 'PAGE_CHARGING_IMPORTS', component: Charging, props: { initialTab: 'import' } },
  { key: 'salaries', label: '工资台账', permission: 'PAGE_SALARIES', component: Salaries },
  { key: 'salary-transfers', label: '转派工资核对', permission: 'PAGE_SALARY_TRANSFERS', component: Salaries, props: { initialSituation: 'TRANSFER' } },
  { key: 'attendance', label: '司机出勤', permission: 'PAGE_ATTENDANCE', component: Monthly, props: { scope: 'ATTENDANCE' } },
  { key: 'approvals-todo', label: '我的待办', permission: 'PAGE_APPROVALS_TODO', component: Approvals, props: { initialScope: 'TODO', autoOpenFirst: true } },
  { key: 'approvals-mine', label: '我的申请', permission: 'PAGE_APPROVALS_MINE', component: Approvals, props: { initialScope: 'MINE' } },
  { key: 'approvals-history', label: '审批记录', permission: 'PAGE_APPROVALS_HISTORY', component: Approvals, props: { initialScope: 'HISTORY' } },
  { key: 'approvals-flows', label: '流程与模板配置', permission: 'PAGE_APPROVALS_FLOWS', component: Approvals, props: { initialTab: 'flows' } },
  { key: 'drivers', label: '司机管理', permission: 'PAGE_DRIVERS', component: Master, props: { initialTab: 'drivers' } },
  { key: 'vehicles', label: '车辆管理', permission: 'PAGE_VEHICLES', component: Master, props: { initialTab: 'vehicles' } },
  { key: 'trailers', label: '车挂管理', permission: 'PAGE_TRAILERS', component: Master, props: { initialTab: 'trailers' } },
  { key: 'binding', label: '绑定关系', permission: 'PAGE_BINDING', component: Master, props: { initialTab: 'binding' } },
  { key: 'products', label: '货物管理', permission: 'PAGE_PRODUCTS', component: Master, props: { initialTab: 'products' } },
  { key: 'tires', label: '轮胎库存', permission: 'PAGE_TIRES', component: Tires },
  { key: 'tire-imports', label: '轮胎入库', permission: 'PAGE_TIRE_IMPORTS', component: TireImports },
  { key: 'finance-tasks', label: '单趟收支利润', permission: 'PAGE_FINANCE_TASKS', component: Finance, props: { scope: 'TASK' } },
  { key: 'finance-templates', label: '线路收支利润', permission: 'PAGE_FINANCE_TEMPLATES', component: Finance, props: { scope: 'TEMPLATE' } },
  { key: 'finance-company', label: '公司收支利润', permission: 'PAGE_FINANCE_COMPANY', component: Finance, props: { scope: 'COMPANY' } },
  { key: 'finance-vehicles', label: '车辆支出', permission: 'PAGE_FINANCE_VEHICLES', component: Expenses, props: { focus: 'VEHICLE' } },
  { key: 'finance-drivers', label: '司机支出', permission: 'PAGE_FINANCE_DRIVERS', component: Expenses, props: { focus: 'DRIVER' } },
  { key: 'finance-energy', label: '能源费用', permission: 'PAGE_FINANCE_ENERGY', component: Expenses, props: { focus: 'ENERGY' } },
  { key: 'settlements', label: '月度结算', permission: 'PAGE_SETTLEMENTS', component: Settlement },
  { key: 'users', label: '用户管理', permission: 'PAGE_USERS', component: System, props: { initialTab: 'users' } },
  { key: 'roles', label: '角色权限', permission: 'PAGE_ROLES', component: System, props: { initialTab: 'roles' } },
  { key: 'audit', label: '操作日志', permission: 'PAGE_AUDIT', component: System, props: { initialTab: 'audits' } },
];

const routeAliases = { 'task-import-history': 'task-imports', 'charging-history': 'charging-imports', 'import-history': 'task-imports', 'expense-attribution': 'expense-unassigned', 'approvals-queue': 'approvals-todo' };
const canonicalRoute = (key) => routeAliases[key] || key;
const fromHash = () => {
  if (!window.location.hash.startsWith('#/')) return '';
  const requested = window.location.hash.slice(2);
  return canonicalRoute(requested);
};
export const routerStore = reactive({ current: fromHash() || canonicalRoute(localStorage.getItem('admin.currentRoute')) || 'dashboard' });
if (fromHash() && window.location.hash !== `#/${fromHash()}`) window.history.replaceState(null, '', `#/${fromHash()}`);
export const visibleRoutes = computed(() => routes.filter((route) => hasRouteAccess(route, permissions())));
export const routeAccessDenied = computed(() => routes.some((route) => route.key === routerStore.current)
  && !visibleRoutes.value.some((route) => route.key === routerStore.current));
export const activeRoute = computed(() => routeAccessDenied.value ? null
  : visibleRoutes.value.find((route) => route.key === routerStore.current) || visibleRoutes.value[0] || null);
export const visibleNavigation = computed(() => filterNavigation(navigation, routes, (route) => hasRouteAccess(route, permissions())));
export const navigationTrail = (key) => findNavigationTrail(visibleNavigation.value, key);
export function navigate(key) {
  key = canonicalRoute(key);
  if (!visibleRoutes.value.some((route) => route.key === key)) return;
  routerStore.current = key;
  localStorage.setItem('admin.currentRoute', key);
  if (window.location.hash !== `#/${key}`) window.location.hash = `/${key}`;
}
window.addEventListener('hashchange', () => {
  const key = fromHash();
  if (!key) return;
  routerStore.current = key;
  localStorage.setItem('admin.currentRoute', key);
  if (window.location.hash !== `#/${key}`) window.history.replaceState(null, '', `#/${key}`);
});
