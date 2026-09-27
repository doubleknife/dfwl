import { computed, reactive } from 'vue';
import DashboardView from '../views/DashboardUi2View.vue';
import MasterDataView from '../views/MasterDataUi2View.vue';
import RoutesView from '../views/RoutesSampleView.vue';
import ImportCenterView from '../views/ImportCenterUi2View.vue';
import ExpensesView from '../views/ExpensesUi1View.vue';
import ApprovalsView from '../views/ApprovalsUi2View.vue';
import ApprovalFlowsView from '../views/ApprovalFlowsUi2View.vue';
import TiresView from '../views/TiresUi2View.vue';
import SalariesView from '../views/SalariesUi1View.vue';
import ReportsView from '../views/ReportsUi2View.vue';
import SettlementsView from '../views/SettlementsUi2View.vue';
import SystemView from '../views/SystemUi1View.vue';
import { canAny } from '../stores/auth.js';

export const routes = [
  { key: 'dashboard', label: '首页看板', permission: 'report:dashboard', component: DashboardView },
  { key: 'master', label: '基础资料', permission: ['driver:list', 'vehicle:list', 'trailer:list', 'route:list', 'route:create', 'route:edit'], component: MasterDataView },
  { key: 'routes', label: '线路管理', permission: 'route:list', component: RoutesView },
  { key: 'imports', label: '导入中心', permission: 'import:history', component: ImportCenterView },
  { key: 'expenses', label: '费用管理', permission: 'expense:list', component: ExpensesView },
  { key: 'approvals', label: '审批中心', permission: ['approval:history:view', 'approval:create', 'approval:process', 'approval:return'], component: ApprovalsView },
  { key: 'approvalFlows', label: '审批模板', permission: 'approval:flow:manage', component: ApprovalFlowsView },
  { key: 'tires', label: '轮胎管理', permission: ['tire:list', 'tire:request'], component: TiresView },
  { key: 'salaries', label: '工资管理', permission: ['salary:view', 'salary:manage'], component: SalariesView },
  { key: 'reports', label: '经营报表', permission: ['report:profit', 'report:vehicle', 'report:driver', 'report:attendance', 'report:energy'], component: ReportsView },
  { key: 'settlements', label: '月度结算', permission: 'settlement:view', component: SettlementsView },
  { key: 'system', label: '系统管理', permission: ['user:manage', 'role:manage', 'permission:manage', 'audit:view'], component: SystemView }
];

export const routerStore = reactive({
  current: localStorage.getItem('admin.currentRoute') || 'dashboard'
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
