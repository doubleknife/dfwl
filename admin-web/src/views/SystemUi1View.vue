<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { systemApi } from '../api/modules.js';
import { authStore, can } from '../stores/auth.js';
import { displayValue } from '../utils/displayLabels.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import StatusTag from '../components/StatusTag.vue';
import EntityPicker from '../components/EntityPicker.vue';
import SearchableRoleSelect from '../components/SearchableRoleSelect.vue';
import RolePermissionTree from '../components/RolePermissionTree.vue';
import { navigation } from '../router/navigationModel.js';
import { routes } from '../router/routes.js';
import { buildPagePermissionTree, overviewCardAccess, pagePermissionName, selectPermissionCodes } from '../router/pagePermissionModel.js';
import PaginationBar from '../components/PaginationBar.vue';
import FormDialog from '../components/FormDialog.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ErrorState from '../components/ErrorState.vue';
import AuditLogsUi2 from '../components/AuditLogsUi2.vue';

const props = defineProps({ initialTab: { type: String, default: 'users' } });
const tab = ref(props.initialTab);
const users = ref([]);
const roles = ref([]);
const permissions = ref([]);
const userPage = ref(1);
const userTotal = ref(0);
const rolePage = ref(1);
const roleTotal = ref(0);
const directoryPage = ref(1);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const success = ref('');
const userDialog = ref(false);
const userDetail = ref(null);
const userInitial = ref('');
const roleInitial = ref('');
const discardTarget = ref('');
const userForm = reactive({ id: '', phone: '', password: '', roleId: '', status: 1 });
const resetTarget = ref(null);
const newPassword = ref('');
const confirmPassword = ref('');
const userToggle = ref(null);
const userMore = ref(null);
const roleDialog = ref(false);
const roleForm = reactive({ id: '', roleCode: '', roleName: '', status: 1, systemFixed: false });
const roleToggle = ref(null);
const roleMore = ref(null);
const permissionRole = ref(null);
const previewRole = ref(null);
const previewLoading = ref(false);
const selectedCodes = ref([]);
const originalCodes = ref([]);
const permissionSearch = ref('');
const pagePermissionTree = computed(() => buildPagePermissionTree(navigation, routes, permissions.value));
const permissionConfirm = ref(false);
const filters = reactive({ user: '', userRole: '', userStatus: '', role: '', roleStatus: '', permission: '', module: '' });
const businessModules = { route: '运输', task: '运输任务', template: '线路模板', dispatch: '调度', driver: '司机', vehicle: '车辆', trailer: '车挂', product: '货物', binding: '绑定', expense: '费用', charging: '充电订单', approval: '审批', tire: '轮胎', salary: '工资', attendance: '出勤', import: '导入', report: '经营报表', settlement: '月结', user: '用户', role: '角色', permission: '权限', audit: '审计', attachment: '附件', auth: '认证' };
const moduleOf = (code) => code?.split(':')[0] || 'other';
const moduleName = (code) => businessModules[moduleOf(code)] || '其他';
const roleName = (id) => roles.value.find((role) => role.id === Number(id))?.roleName || '';
const displayTime = (value) => value ? String(value).replace('T', ' ').replace(/\.\d+$/, '') : '从未登录';
const userRows = computed(() => users.value.filter((row) =>
  (!filters.user || row.phone.includes(filters.user)) && (!filters.userRole || String(row.roleId) === filters.userRole)
  && (!filters.userStatus || String(row.status) === filters.userStatus)));
const roleRows = computed(() => roles.value.filter((row) =>
  (!filters.role || `${row.roleName} ${row.roleCode}`.toLowerCase().includes(filters.role.toLowerCase()))
  && (!filters.roleStatus || String(row.status) === filters.roleStatus)));
const modules = computed(() => [...new Set(permissions.value.map((item) => moduleOf(item.permissionCode)))]);
const directoryRows = computed(() => permissions.value.map((item) => ({
  ...item, permissionName: pagePermissionName(item, routes)
})).filter((item) =>
  (!filters.module || moduleOf(item.permissionCode) === filters.module)
  && (!filters.permission || `${item.permissionName} ${item.permissionCode}`.toLowerCase().includes(filters.permission.toLowerCase()))));
const visibleDirectory = computed(() => directoryRows.value.slice((directoryPage.value - 1) * 15, directoryPage.value * 15));
const addedCodes = computed(() => selectedCodes.value.filter((code) => !originalCodes.value.includes(code)));
const removedCodes = computed(() => originalCodes.value.filter((code) => !selectedCodes.value.includes(code)));
const previewPageGroups = computed(() => {
  const selected = (previewRole.value?.permissions || []).map((item) => item.permissionCode);
  const groups = {};
  const visit = (node, labels) => {
    if (node.type === 'page' && node.access.some((code) => selected.includes(code))) labels.push(node.label);
    else if (node.type === 'derived' && Object.values(overviewCardAccess(selected)).some(Boolean)) labels.push(node.label);
    else for (const child of node.children || []) visit(child, labels);
  };
  for (const domain of pagePermissionTree.value.domains) {
    const labels = [];
    visit(domain, labels);
    if (labels.length) groups[domain.label] = labels;
  }
  const common = pagePermissionTree.value.common.filter((item) => selected.includes(item.permissionCode));
  if (common.length) groups['小程序与公共权限'] = common.map((item) => item.permissionName);
  return groups;
});

async function loadUsers(page = 1) {
  loading.value = true; error.value = '';
  try { const result = await systemApi.users(page, 20); users.value = result.records || []; userTotal.value = Number(result.total || 0); userPage.value = page; }
  catch (cause) { error.value = cause.message || '用户加载失败'; }
  finally { loading.value = false; }
}
async function loadRoles(page = 1) {
  loading.value = true; error.value = '';
  try {
    const result = await systemApi.roles(page, 20);
    roles.value = result.records || []; roleTotal.value = Number(result.total || 0); rolePage.value = page;
    if (tab.value === 'roles' && roles.value.length && !roles.value.some((row) => row.id === previewRole.value?.id)) {
      await viewRole(roles.value[0]);
    }
  }
  catch (cause) { error.value = cause.message || '角色加载失败'; }
  finally { loading.value = false; }
}
async function loadPermissions() {
  loading.value = true; error.value = '';
  try { permissions.value = await systemApi.permissions(); }
  catch (cause) { error.value = cause.message || '权限加载失败'; }
  finally { loading.value = false; }
}
function changeTab(value) { tab.value = value; error.value = ''; if (value === 'users') loadUsers(1); if (value === 'roles') loadRoles(1); if (value === 'permissions') loadPermissions(); }
function openUser(row = null) { Object.assign(userForm, { id: row?.id || '', phone: row?.phone || '', password: '', roleId: row?.roleId || '', status: row?.status ?? 1 }); userInitial.value = JSON.stringify(userForm); userDialog.value = true; }
function requestCloseUser() { if (busy.value) return; if (JSON.stringify(userForm) !== userInitial.value) discardTarget.value = 'user'; else userDialog.value = false; }
function requestCloseReset() { if (busy.value) return; if (newPassword.value || confirmPassword.value) discardTarget.value = 'reset'; else resetTarget.value = null; }
function requestCloseRole() { if (busy.value) return; if (JSON.stringify(roleForm) !== roleInitial.value) discardTarget.value = 'role'; else roleDialog.value = false; }
function requestClosePermissions() { if (busy.value) return; if (addedCodes.value.length || removedCodes.value.length) discardTarget.value = 'permissions'; else permissionRole.value = null; }
function discardChanges() {
  if (discardTarget.value === 'user') userDialog.value = false;
  if (discardTarget.value === 'reset') { resetTarget.value = null; newPassword.value = ''; confirmPassword.value = ''; }
  if (discardTarget.value === 'role') roleDialog.value = false;
  if (discardTarget.value === 'permissions') permissionRole.value = null;
  discardTarget.value = '';
}
function openReset(row) { userMore.value = null; resetTarget.value = row; }
function openUserToggle(row) { userMore.value = null; userToggle.value = row; }
async function saveUser() {
  if (!userForm.roleId) { error.value = '请选择用户角色'; return; }
  busy.value = true; error.value = ''; success.value = '';
  try {
    const body = { phone: userForm.phone, password: userForm.id ? null : userForm.password, roleId: Number(userForm.roleId), status: Number(userForm.status) };
    if (userForm.id) await systemApi.updateUser(userForm.id, body); else await systemApi.createUser(body);
    userDialog.value = false; await loadUsers(userPage.value);
    if (!error.value) success.value = userForm.id ? '用户资料已保存' : '用户已新增';
  } catch (cause) { error.value = cause.message || '用户保存失败'; }
  finally { busy.value = false; }
}
async function savePassword() {
  if (newPassword.value !== confirmPassword.value) { error.value = '两次输入的新密码不一致'; return; }
  busy.value = true; error.value = ''; success.value = '';
  try { await systemApi.resetPassword(resetTarget.value.id, { newPassword: newPassword.value }); resetTarget.value = null; newPassword.value = ''; confirmPassword.value = ''; success.value = '密码已重置'; }
  catch (cause) { error.value = cause.message || '重置密码失败'; }
  finally { busy.value = false; }
}
async function toggleUser() {
  busy.value = true; error.value = ''; success.value = '';
  try { const action = userToggle.value.status === 1 ? '停用' : '启用'; await systemApi.setStatus(userToggle.value.id, { status: userToggle.value.status === 1 ? 0 : 1 }); userToggle.value = null; await loadUsers(userPage.value); if (!error.value) success.value = `账号已${action}`; }
  catch (cause) { error.value = cause.message || '账号状态更新失败'; }
  finally { busy.value = false; }
}
function openRole(row = null) { Object.assign(roleForm, { id: row?.id || '', roleCode: row?.roleCode || '', roleName: row?.roleName || '', status: row?.status ?? 1, systemFixed: !!row?.systemFixed }); roleInitial.value = JSON.stringify(roleForm); roleDialog.value = true; }
function openRoleEdit(row) { roleMore.value = null; openRole(row); }
function openRoleToggle(row) { roleMore.value = null; roleToggle.value = row; }
async function saveRole() {
  busy.value = true; error.value = ''; success.value = '';
  try { const body = { roleName: roleForm.roleName, status: Number(roleForm.status) }; if (roleForm.id) await systemApi.updateRole(roleForm.id, body); else await systemApi.createRole(body); roleDialog.value = false; await loadRoles(rolePage.value); if (!error.value) success.value = roleForm.id ? '角色资料已保存' : '角色已新增'; }
  catch (cause) { error.value = cause.message || '角色保存失败'; }
  finally { busy.value = false; }
}
async function toggleRole() {
  busy.value = true; error.value = ''; success.value = '';
  try { const action = roleToggle.value.status === 1 ? '停用' : '启用'; await systemApi.setRoleStatus(roleToggle.value.id, roleToggle.value.status === 1 ? 0 : 1); roleToggle.value = null; await loadRoles(rolePage.value); if (!error.value) success.value = `角色已${action}`; }
  catch (cause) { error.value = cause.message || '角色状态更新失败'; }
  finally { busy.value = false; }
}
async function openRolePermissions(row) {
  error.value = '';
  try {
    const detail = await systemApi.role(row.id);
    permissionRole.value = detail;
    if (!permissions.value.length) await loadPermissions();
    if (error.value || !permissions.value.length) throw new Error(error.value || '权限目录为空，请刷新后重试');
    const available = new Set(permissions.value.map((item) => item.permissionCode));
    selectedCodes.value = (detail.permissions || []).map((item) => item.permissionCode)
      .filter((code) => available.has(code));
    originalCodes.value = [...selectedCodes.value];
  } catch (cause) { permissionRole.value = null; error.value = cause.message || '角色权限加载失败'; }
}
async function viewRole(row) {
  previewLoading.value = true;
  try { previewRole.value = await systemApi.role(row.id); }
  catch (cause) { error.value = cause.message || '角色权限加载失败'; }
  finally { previewLoading.value = false; }
}
function toggleTreeCodes(codes, checked) {
  selectedCodes.value = selectPermissionCodes(selectedCodes.value, codes, checked, routes, '', permissions.value);
}
function toggleTreeCode(code, checked, ownerKey) {
  selectedCodes.value = selectPermissionCodes(selectedCodes.value, [code], checked, routes, ownerKey, permissions.value);
}
async function savePermissions() {
  busy.value = true; error.value = ''; success.value = '';
  try {
    const savedRoleId = permissionRole.value.id;
    await systemApi.saveRolePermissions(permissionRole.value.id, selectedCodes.value);
    permissionConfirm.value = false;
    permissionRole.value = null;
    await authStore.loadMe();
    if (can('PAGE_ROLES')) {
      await loadRoles(rolePage.value);
      if (previewRole.value?.id === savedRoleId) await viewRole({ id: savedRoleId });
    }
    if (!error.value && !authStore.error) success.value = '角色权限已保存，当前账号权限已同步';
  }
  catch (cause) { error.value = cause.message || '权限保存失败'; }
  finally { busy.value = false; }
}
onMounted(async () => {
  if (can('PAGE_ROLES') || can('PAGE_USERS')) await loadRoles(1);
  if (can('PAGE_ROLES')) await loadPermissions();
  if (tab.value === 'users') loadUsers(1);
});
</script>

<template>
  <div class="ui-page-container">
    <PageHeader :title="{ users: '用户管理', roles: '角色权限', permissions: '权限目录', audits: '操作日志' }[tab] || '系统设置'" :description="{ users: '管理登录账号、角色和启停状态。', roles: '配置业务角色及其正式权限。', permissions: '查看系统已定义的原子权限。', audits: '按业务范围查看只读操作记录。' }[tab] || '按权限管理系统资料。'" />
    <ErrorState v-if="error" :message="error" @retry="changeTab(tab)" />
    <p v-if="success" class="ui-success-message" role="status">{{ success }}</p>
    <section v-if="tab === 'users' && can('PAGE_USERS')">
      <div class="ui-list-heading"><h2>用户列表</h2><button class="primary" type="button" @click="openUser()">＋ 新增用户</button></div>
      <FilterBar><label class="ui-search">手机号<input v-model.trim="filters.user" type="search" placeholder="搜索手机号" /></label><label>角色<select v-model="filters.userRole"><option value="">全部角色</option><option v-for="role in roles" :key="role.id" :value="String(role.id)">{{ role.roleName }}</option></select></label><label>状态<select v-model="filters.userStatus"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label></FilterBar>
      <DataTable v-if="!error" :columns="[{key:'phone',label:'登录账号'},{key:'roleName',label:'角色'},{key:'status',label:'状态'},{key:'lastLoginAt',label:'最近登录'},{key:'actions',label:'操作'}]" :rows="userRows" :loading="loading" :empty-text="filters.user || filters.userRole || filters.userStatus ? '当前页没有匹配用户' : '暂无用户'">
        <template #status="{value}"><StatusTag :value="value === 1 ? '启用' : '停用'" /></template><template #lastLoginAt="{value}">{{ displayTime(value) }}</template>
        <template #actions="{row}"><div class="ui-inline-actions"><button type="button" @click="userDetail = row">详情</button><button type="button" @click="openUser(row)">编辑</button><button type="button" @click="userMore = row">更多</button></div></template>
      </DataTable><PaginationBar :page-no="userPage" :page-size="20" :total="userTotal" @change="loadUsers" />
    </section>
    <section v-if="tab === 'roles' && can('PAGE_ROLES')">
      <div class="ui-list-heading"><h2>角色列表</h2><button class="primary" type="button" @click="openRole()">＋ 新增角色</button></div><FilterBar><label class="ui-search">角色名称或编码<input v-model.trim="filters.role" type="search" placeholder="搜索角色" /></label><label>状态<select v-model="filters.roleStatus"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label></FilterBar>
      <div class="ui-role-workspace"><div class="ui-role-list"><p v-if="loading" class="ui-role-message">正在加载角色…</p><p v-else-if="!roleRows.length" class="ui-role-message">{{ filters.role || filters.roleStatus ? '当前页没有匹配角色' : '暂无角色' }}</p>
        <div v-for="row in roleRows" :key="row.id" class="ui-role-row" :class="{ selected: previewRole?.id === row.id }">
          <button type="button" class="ui-role-select" @click="viewRole(row)"><strong>{{ row.roleName }}</strong><small>{{ row.roleCode }}</small></button>
          <span class="ui-role-state"><span>{{ row.systemFixed ? '系统固定' : '自定义' }}</span><StatusTag :value="row.status === 1 ? '启用' : '停用'" :tone="row.status === 1 ? 'success' : 'danger'" /></span>
          <button v-if="!row.systemFixed" type="button" class="ui-role-more" @click="roleMore = row">更多</button>
        </div><PaginationBar :page-no="rolePage" :page-size="20" :total="roleTotal" @change="loadRoles" /></div>
      <aside class="ui-role-preview" aria-label="角色权限概览"><div class="ui-role-preview-head"><div><h2>{{ previewRole?.roleName || '角色权限' }}</h2><p class="muted">{{ previewRole ? `${previewRole.roleCode} · 当前已授权 ${previewRole.permissions?.length || 0} 项` : '选择左侧角色查看权限' }}</p></div><button v-if="previewRole" type="button" class="primary" :disabled="previewLoading" @click="openRolePermissions(previewRole)">配置权限</button></div>
        <p v-if="previewLoading" class="muted">正在加载权限…</p><p v-else-if="!previewRole" class="muted">暂无可查看的角色。</p><p v-else-if="!previewRole.permissions?.length" class="muted">该角色暂未授权。</p>
        <div v-else class="ui-role-preview-groups"><section v-for="(labels, domain) in previewPageGroups" :key="domain"><h3>{{ domain }} <small>{{ labels.length }} 项</small></h3><ul><li v-for="label in labels" :key="label">{{ label }}</li></ul></section></div>
      </aside></div>
    </section>
    <section v-if="tab === 'permissions' && can('PAGE_ROLES')">
      <div class="ui-list-heading"><h2>权限目录</h2><span class="muted">只读 · 共 {{ permissions.length }} 项</span></div><FilterBar><label class="ui-search">权限名称或编码<input v-model.trim="filters.permission" type="search" placeholder="搜索权限" @input="directoryPage = 1" /></label><label>业务模块<select v-model="filters.module" @change="directoryPage = 1"><option value="">全部模块</option><option v-for="module in modules" :key="module" :value="module">{{ businessModules[module] || '其他' }}</option></select></label></FilterBar>
      <DataTable v-if="!error" :columns="[{key:'module',label:'模块'},{key:'permissionName',label:'权限名称'},{key:'permissionCode',label:'权限编码'},{key:'description',label:'说明'}]" :rows="visibleDirectory" :loading="loading" :empty-text="filters.permission || filters.module ? '没有匹配权限' : '暂无权限'">
        <template #module="{row}">{{ moduleName(row.permissionCode) }}</template><template #permissionCode="{value}"><small class="muted">{{ value }}</small></template><template #description="{row}">{{ displayValue('permissionType', row.permissionType) }}权限</template>
      </DataTable><PaginationBar :page-no="directoryPage" :page-size="15" :total="directoryRows.length" @change="directoryPage = $event" />
    </section>
    <AuditLogsUi2 v-if="tab === 'audits' && can('PAGE_AUDIT')" />
    <DetailDrawer :open="!!userDetail" title="用户详情" @close="userDetail = null"><dl v-if="userDetail" class="ui-detail-grid"><dt>登录账号</dt><dd>{{ userDetail.phone }}</dd><dt>角色</dt><dd>{{ userDetail.roleName || '未配置' }}</dd><dt>状态</dt><dd><StatusTag :value="userDetail.status" /></dd><dt>最近登录</dt><dd>{{ displayTime(userDetail.lastLoginAt) }}</dd></dl></DetailDrawer>
    <FormDialog :open="!!userMore" title="用户操作" @close="userMore = null"><div v-if="userMore"><p>{{ userMore.phone }} · {{ userMore.roleName }}</p><div class="ui-object-actions"><button type="button" @click="openReset(userMore)">重置密码</button></div></div></FormDialog>
    <FormDialog :open="userDialog" :title="userForm.id ? '编辑用户' : '新增用户'" :busy="busy" @close="requestCloseUser"><form @submit.prevent="saveUser"><div class="ui-form-grid"><label class="ui-form-field">手机号<input v-model.trim="userForm.phone" required inputmode="tel" /></label><label v-if="!userForm.id" class="ui-form-field">初始密码<input v-model="userForm.password" required minlength="6" type="password" autocomplete="new-password" /></label><label class="ui-form-field">角色<SearchableRoleSelect v-model="userForm.roleId" :load-page="systemApi.roles" :selected-label="roleName(userForm.roleId)" /></label><label class="ui-form-field">状态<select v-model.number="userForm.status"><option :value="1">启用</option><option :value="0">停用</option></select></label></div><div class="ui-dialog-actions"><button type="button" @click="requestCloseUser">取消</button><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '保存用户' }}</button></div></form></FormDialog>
    <FormDialog :open="!!resetTarget" title="重置密码" :busy="busy" @close="requestCloseReset"><form v-if="resetTarget" @submit.prevent="savePassword"><p>账号：<strong>{{ resetTarget.phone }}</strong> · {{ resetTarget.roleName }}</p><p class="muted">重置后旧密码将无法继续用于登录。</p><label class="ui-form-field">新密码<input v-model="newPassword" required minlength="6" type="password" autocomplete="new-password" /></label><label class="ui-form-field">确认新密码<input v-model="confirmPassword" required minlength="6" type="password" autocomplete="new-password" /></label><div class="ui-dialog-actions"><button type="button" @click="requestCloseReset">取消</button><button class="ui-danger-button" :disabled="busy">确认重置密码</button></div></form></FormDialog>
    <ConfirmDialog :open="!!userToggle" :title="userToggle?.status === 1 ? '停用账号' : '启用账号'" :message="userToggle ? `${userToggle.status === 1 ? '停用' : '启用'} ${userToggle.phone}（${userToggle.roleName}）？停用后该账号无法使用系统。` : ''" :danger="userToggle?.status === 1" :busy="busy" @cancel="userToggle = null" @confirm="toggleUser" />
    <FormDialog :open="roleDialog" :title="roleForm.id ? '编辑角色' : '新增角色'" :busy="busy" @close="requestCloseRole"><form @submit.prevent="saveRole"><div class="ui-form-grid"><label class="ui-form-field">角色名称<input v-model.trim="roleForm.roleName" required /></label><p v-if="roleForm.id" class="ui-form-field muted">角色编码：{{ roleForm.roleCode }}（系统生成）</p><label class="ui-form-field">状态<select v-model.number="roleForm.status"><option :value="1">启用</option><option :value="0">停用</option></select></label></div><div class="ui-dialog-actions"><button type="button" @click="requestCloseRole">取消</button><button class="primary" :disabled="busy">保存角色</button></div></form></FormDialog>
    <FormDialog :open="!!roleMore" title="角色操作" @close="roleMore = null"><div v-if="roleMore"><p>{{ roleMore.roleName }} · {{ roleMore.roleCode }}</p><div class="ui-object-actions"><button type="button" @click="openRoleEdit(roleMore)">编辑角色</button><button type="button" :class="roleMore.status === 1 ? 'ui-danger-link' : ''" @click="openRoleToggle(roleMore)">{{ roleMore.status === 1 ? '停用' : '启用' }}</button></div></div></FormDialog>
    <ConfirmDialog :open="!!roleToggle" :title="roleToggle?.status === 1 ? '停用角色' : '启用角色'" :message="roleToggle ? `${roleToggle.status === 1 ? '停用' : '启用'}角色 ${roleToggle.roleName}？关联用户的权限将受到影响。` : ''" :danger="roleToggle?.status === 1" :busy="busy" @cancel="roleToggle = null" @confirm="toggleRole" />
    <DetailDrawer :open="!!permissionRole" title="配置角色权限" @close="requestClosePermissions">
      <template #subtitle>{{ permissionRole?.roleName }} · {{ permissionRole?.roleCode }}</template>
      <div v-if="permissionRole">
        <div class="ui-permission-toolbar">
          <label class="ui-form-field">搜索页面或操作<input v-model.trim="permissionSearch" type="search" placeholder="页面、操作名称或权限编码" /></label>
          <p>已选 <strong>{{ selectedCodes.length }}</strong> 项 · 新增 {{ addedCodes.length }} 项 · 移除 {{ removedCodes.length }} 项</p>
          <p>共用权限只保存一次；取消共用权限会同步影响使用它的页面。</p>
        </div>
        <RolePermissionTree :model="pagePermissionTree" :selected="selectedCodes" :search="permissionSearch"
          @toggle-codes="toggleTreeCodes" @toggle-code="toggleTreeCode" />
      </div>
      <template #footer><span class="muted">保存前确认新增与移除项目</span><button type="button" @click="requestClosePermissions">取消</button><button class="primary" type="button" :disabled="busy || (!addedCodes.length && !removedCodes.length)" @click="permissionConfirm = true">保存权限</button></template>
    </DetailDrawer>
    <ConfirmDialog :open="permissionConfirm" title="确认角色权限变更" :message="`为 ${permissionRole?.roleName || '角色'} 新增 ${addedCodes.length} 项、移除 ${removedCodes.length} 项权限？关联用户可在账号菜单刷新我的权限。`" :busy="busy" @cancel="permissionConfirm = false" @confirm="savePermissions" />
    <ConfirmDialog :open="!!discardTarget" title="放弃未保存修改" message="当前内容尚未保存。确定放弃本次修改吗？" @cancel="discardTarget = ''" @confirm="discardChanges" />
  </div>
</template>
