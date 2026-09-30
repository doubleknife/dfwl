import { computed, reactive } from 'vue';
import MasterDataView from '../views/MasterDataUi2View.vue';
import RoutesView from '../views/TransportTasksV3View.vue';
import DispatchWorkbench from '../views/DispatchWorkbenchV3View.vue';
import TaskImports from '../views/TaskImportsV3View.vue';
import SalariesView from '../views/SalaryLedgerV3View.vue';
import MonthlyOperationsView from '../views/MonthlyOperationsV3View.vue';
import ApprovalCenterView from '../views/ApprovalCenterV3View.vue';
import ExpenseApplicationView from '../views/ExpenseApplicationV3View.vue';
import SystemView from '../views/SystemUi1View.vue';
import { canAny } from '../stores/auth.js';

export const routes = [
  { key: 'master', label: '基础资料', permission: ['driver:list', 'vehicle:list', 'trailer:list', 'template:view'], component: MasterDataView },
  { key: 'routes', label: '运输任务', permission: 'task:view', component: RoutesView },
  { key: 'dispatch', label: '调度工作台', permission: 'dispatch:view', component: DispatchWorkbench },
  { key: 'task-imports', label: '任务导入', permission: 'task:import:view', component: TaskImports },
  { key: 'expense-apply', label: '发起费用申请', permission: 'approval:create', component: ExpenseApplicationView },
  { key: 'approvals', label: '审批中心', permission: ['approval:create', 'approval:process', 'approval:return', 'approval:history:view', 'approval:flow:manage'], component: ApprovalCenterView },
  { key: 'salaries', label: '工资管理', permission: 'salary:view', component: SalariesView },
  { key: 'attendance', label: '司机出勤', permission: 'attendance:view', component: MonthlyOperationsView, props: { scope: 'ATTENDANCE' } },
  { key: 'dispatch-internal', label: '自有车辆月账', permission: 'dispatch:ledger:view', component: MonthlyOperationsView, props: { scope: 'INTERNAL' } },
  { key: 'dispatch-outsourced', label: '外协车辆月账', permission: 'dispatch:ledger:view', component: MonthlyOperationsView, props: { scope: 'OUTSOURCED' } },
  { key: 'system', label: '系统管理', permission: ['user:manage', 'role:manage', 'permission:manage', 'audit:view'], component: SystemView }
];

export const routerStore = reactive({
  current: localStorage.getItem('admin.currentRoute') || 'master'
});

export const visibleRoutes = computed(() => routes.filter((route) => canAny(route.permission)));

export const activeRoute = computed(() => {
  const visible = visibleRoutes.value;
  const current = visible.find((route) => route.key === routerStore.current);
  return current || visible[0] || routes[0];
});

export function navigate(key) {
  routerStore.current = key;
  localStorage.setItem('admin.currentRoute', key);
}
