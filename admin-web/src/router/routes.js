import { computed, reactive } from 'vue';
import MasterDataView from '../views/MasterDataUi2View.vue';
import RoutesView from '../views/TransportTasksV3View.vue';
import DispatchWorkbench from '../views/DispatchWorkbenchV3View.vue';
import TaskImports from '../views/TaskImportsV3View.vue';
import SalariesView from '../views/ParticipantSalariesV3View.vue';
import SystemView from '../views/SystemUi1View.vue';
import { canAny } from '../stores/auth.js';

export const routes = [
  { key: 'master', label: '基础资料', permission: ['driver:list', 'vehicle:list', 'trailer:list', 'template:view'], component: MasterDataView },
  { key: 'routes', label: '运输任务', permission: 'task:view', component: RoutesView },
  { key: 'dispatch', label: '调度工作台', permission: 'dispatch:view', component: DispatchWorkbench },
  { key: 'task-imports', label: '任务导入', permission: 'task:import:view', component: TaskImports },
  { key: 'salaries', label: '工资管理', permission: ['salary:view', 'salary:adjust'], component: SalariesView },
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
