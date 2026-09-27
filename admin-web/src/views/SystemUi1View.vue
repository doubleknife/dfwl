<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { systemApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { displayValue } from '../utils/displayLabels.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import StatusTag from '../components/StatusTag.vue';
import EntityPicker from '../components/EntityPicker.vue';
import PaginationBar from '../components/PaginationBar.vue';
import FormDialog from '../components/FormDialog.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ErrorState from '../components/ErrorState.vue';

const tab = ref(can('user:manage') ? 'users' : can('role:manage') ? 'roles' : can('permission:manage') ? 'permissions' : 'audits');
const users = ref([]);
const roles = ref([]);
const permissions = ref([]);
const audits = ref([]);
const userPage = ref(1);
const userTotal = ref(0);
const rolePage = ref(1);
const roleTotal = ref(0);
const auditPage = ref(1);
const auditTotal = ref(0);
const directoryPage = ref(1);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const userDialog = ref(false);
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
const selectedCodes = ref([]);
const originalCodes = ref([]);
const permissionSearch = ref('');
const permissionConfirm = ref(false);
const filters = reactive({ user: '', userRole: '', userStatus: '', role: '', roleStatus: '', permission: '', module: '' });
const auditFilter = reactive({ module: '', businessType: '', operatorId: '', startTime: '', endTime: '' });
const businessModules = { route: '线路', driver: '司机', vehicle: '车辆', trailer: '车挂', expense: '费用', approval: '审批', tire: '轮胎', salary: '工资', import: '导入', report: '报表', settlement: '月结', user: '用户', role: '角色', permission: '权限', audit: '审计', attachment: '附件', auth: '认证' };
const moduleOf = (code) => code?.split(':')[0] || 'other';
const moduleName = (code) => businessModules[moduleOf(code)] || '其他';
const roleName = (id) => roles.value.find((role) => role.id === Number(id))?.roleName || '角色名称未加载';
const userRows = computed(() => users.value.filter((row) =>
  (!filters.user || row.phone.includes(filters.user)) && (!filters.userRole || String(row.roleId) === filters.userRole)
  && (!filters.userStatus || String(row.status) === filters.userStatus)));
const roleRows = computed(() => roles.value.filter((row) =>
  (!filters.role || `${row.roleName} ${row.roleCode}`.toLowerCase().includes(filters.role.toLowerCase()))
  && (!filters.roleStatus || String(row.status) === filters.roleStatus)));
const modules = computed(() => [...new Set(permissions.value.map((item) => moduleOf(item.permissionCode)))]);
const directoryRows = computed(() => permissions.value.filter((item) =>
  (!filters.module || moduleOf(item.permissionCode) === filters.module)
  && (!filters.permission || `${item.permissionName} ${item.permissionCode}`.toLowerCase().includes(filters.permission.toLowerCase()))));
const visibleDirectory = computed(() => directoryRows.value.slice((directoryPage.value - 1) * 15, directoryPage.value * 15));
const filteredRolePermissions = computed(() => permissions.value.filter((item) =>
  !permissionSearch.value || `${item.permissionName} ${item.permissionCode}`.toLowerCase().includes(permissionSearch.value.toLowerCase())));
const permissionGroups = computed(() => filteredRolePermissions.value.reduce((groups, item) => {
  const key = moduleOf(item.permissionCode);
  (groups[key] ||= []).push(item);
  return groups;
}, {}));
const addedCodes = computed(() => selectedCodes.value.filter((code) => !originalCodes.value.includes(code)));
const removedCodes = computed(() => originalCodes.value.filter((code) => !selectedCodes.value.includes(code)));

async function loadUsers(page = 1) {
  loading.value = true; error.value = '';
  try { const result = await systemApi.users(page, 20); users.value = result.records || []; userTotal.value = Number(result.total || 0); userPage.value = page; }
  catch (cause) { error.value = cause.message || '用户加载失败'; }
  finally { loading.value = false; }
}
async function loadRoles(page = 1) {
  loading.value = true; error.value = '';
  try { const result = await systemApi.roles(page, 20); roles.value = result.records || []; roleTotal.value = Number(result.total || 0); rolePage.value = page; }
  catch (cause) { error.value = cause.message || '角色加载失败'; }
  finally { loading.value = false; }
}
async function loadPermissions() {
  loading.value = true; error.value = '';
  try { permissions.value = await systemApi.permissions(); }
  catch (cause) { error.value = cause.message || '权限加载失败'; }
  finally { loading.value = false; }
}
async function loadAudits(page = 1) {
  loading.value = true; error.value = '';
  try { const result = await systemApi.auditLogs({ ...auditFilter, pageNo: page, pageSize: 20 }); audits.value = result.records || []; auditTotal.value = Number(result.total || 0); auditPage.value = page; }
  catch (cause) { error.value = cause.message || '审计日志加载失败'; }
  finally { loading.value = false; }
}
function changeTab(value) { tab.value = value; error.value = ''; if (value === 'users') loadUsers(1); if (value === 'roles') loadRoles(1); if (value === 'permissions') loadPermissions(); if (value === 'audits') loadAudits(1); }
function openUser(row = null) { Object.assign(userForm, { id: row?.id || '', phone: row?.phone || '', password: '', roleId: row?.roleId || '', status: row?.status ?? 1 }); userDialog.value = true; }
function openReset(row) { userMore.value = null; resetTarget.value = row; }
function openUserToggle(row) { userMore.value = null; userToggle.value = row; }
async function saveUser() {
  busy.value = true; error.value = '';
  try {
    const body = { phone: userForm.phone, password: userForm.id ? null : userForm.password, roleId: Number(userForm.roleId), status: Number(userForm.status) };
    if (userForm.id) await systemApi.updateUser(userForm.id, body); else await systemApi.createUser(body);
    userDialog.value = false; await loadUsers(userPage.value);
  } catch (cause) { error.value = cause.message || '用户保存失败'; }
  finally { busy.value = false; }
}
async function savePassword() {
  if (newPassword.value !== confirmPassword.value) { error.value = '两次输入的新密码不一致'; return; }
  busy.value = true; error.value = '';
  try { await systemApi.resetPassword(resetTarget.value.id, { newPassword: newPassword.value }); resetTarget.value = null; newPassword.value = ''; confirmPassword.value = ''; }
  catch (cause) { error.value = cause.message || '重置密码失败'; }
  finally { busy.value = false; }
}
async function toggleUser() {
  busy.value = true; error.value = '';
  try { await systemApi.setStatus(userToggle.value.id, { status: userToggle.value.status === 1 ? 0 : 1 }); userToggle.value = null; await loadUsers(userPage.value); }
  catch (cause) { error.value = cause.message || '账号状态更新失败'; }
  finally { busy.value = false; }
}
function openRole(row = null) { Object.assign(roleForm, { id: row?.id || '', roleCode: row?.roleCode || '', roleName: row?.roleName || '', status: row?.status ?? 1, systemFixed: !!row?.systemFixed }); roleDialog.value = true; }
function openRoleEdit(row) { roleMore.value = null; openRole(row); }
function openRoleToggle(row) { roleMore.value = null; roleToggle.value = row; }
async function saveRole() {
  busy.value = true; error.value = '';
  try { const body = { roleCode: roleForm.roleCode, roleName: roleForm.roleName, status: Number(roleForm.status) }; if (roleForm.id) await systemApi.updateRole(roleForm.id, body); else await systemApi.createRole(body); roleDialog.value = false; await loadRoles(rolePage.value); }
  catch (cause) { error.value = cause.message || '角色保存失败'; }
  finally { busy.value = false; }
}
async function toggleRole() {
  busy.value = true; error.value = '';
  try { await systemApi.setRoleStatus(roleToggle.value.id, roleToggle.value.status === 1 ? 0 : 1); roleToggle.value = null; await loadRoles(rolePage.value); }
  catch (cause) { error.value = cause.message || '角色状态更新失败'; }
  finally { busy.value = false; }
}
async function openRolePermissions(row) {
  error.value = '';
  try {
    const detail = await systemApi.role(row.id);
    permissionRole.value = detail;
    selectedCodes.value = (detail.permissions || []).map((item) => item.permissionCode);
    originalCodes.value = [...selectedCodes.value];
    if (!permissions.value.length) await loadPermissions();
  } catch (cause) { error.value = cause.message || '角色权限加载失败'; }
}
function togglePermission(code) {
  selectedCodes.value = selectedCodes.value.includes(code) ? selectedCodes.value.filter((item) => item !== code) : [...selectedCodes.value, code];
}
function toggleModule(items, checked) {
  const codes = items.map((item) => item.permissionCode);
  selectedCodes.value = checked ? [...new Set([...selectedCodes.value, ...codes])] : selectedCodes.value.filter((code) => !codes.includes(code));
}
async function savePermissions() {
  busy.value = true; error.value = '';
  try { await systemApi.saveRolePermissions(permissionRole.value.id, selectedCodes.value); permissionConfirm.value = false; permissionRole.value = null; await loadRoles(rolePage.value); }
  catch (cause) { error.value = cause.message || '权限保存失败'; }
  finally { busy.value = false; }
}
onMounted(async () => {
  if (can('role:manage') || can('user:manage')) await loadRoles(1);
  if (can('permission:manage') || can('role:manage')) await loadPermissions();
  if (tab.value === 'users') loadUsers(1);
  if (tab.value === 'audits') loadAudits(1);
});
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="系统管理" description="管理用户、角色与权限；操作日志保留完整审计记录。" />
    <ErrorState v-if="error" :message="error" @retry="changeTab(tab)" />
    <div class="ui-subtabs"><button v-if="can('user:manage')" type="button" :class="{active:tab==='users'}" @click="changeTab('users')">用户</button><button v-if="can('role:manage')" type="button" :class="{active:tab==='roles'}" @click="changeTab('roles')">角色</button><button v-if="can('permission:manage')" type="button" :class="{active:tab==='permissions'}" @click="changeTab('permissions')">权限目录</button><button v-if="can('audit:view')" type="button" :class="{active:tab==='audits'}" @click="changeTab('audits')">操作日志</button></div>
    <section v-if="tab === 'users' && can('user:manage')">
      <div class="ui-list-heading"><h2>用户列表</h2><button class="primary" type="button" @click="openUser()">＋ 新增用户</button></div>
      <FilterBar><label class="ui-search">手机号<input v-model.trim="filters.user" type="search" placeholder="搜索当前页" /></label><label>角色<select v-model="filters.userRole"><option value="">全部角色</option><option v-for="role in roles" :key="role.id" :value="String(role.id)">{{ role.roleName }}</option></select></label><label>状态<select v-model="filters.userStatus"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label></FilterBar><p class="muted">筛选当前页；角色列表也按页加载。</p>
      <DataTable v-if="!error" :columns="[{key:'phone',label:'手机号'},{key:'roleName',label:'角色'},{key:'status',label:'状态'},{key:'lastLoginAt',label:'最近登录'},{key:'actions',label:'操作'}]" :rows="userRows" :loading="loading" :empty-text="filters.user || filters.userRole || filters.userStatus ? '当前页没有匹配用户' : '暂无用户'">
        <template #status="{value}"><StatusTag :value="value === 1 ? '启用' : '停用'" :tone="value === 1 ? 'success' : 'warning'" /></template><template #lastLoginAt="{value}">{{ value || '从未登录' }}</template>
        <template #actions="{row}"><div class="ui-inline-actions"><button type="button" @click="openUser(row)">编辑</button><button type="button" @click="userMore = row">更多</button></div></template>
      </DataTable><PaginationBar :page-no="userPage" :page-size="20" :total="userTotal" @change="loadUsers" />
    </section>
    <section v-if="tab === 'roles' && can('role:manage')">
      <div class="ui-list-heading"><h2>角色列表</h2><button class="primary" type="button" @click="openRole()">＋ 新增角色</button></div><FilterBar><label class="ui-search">角色名称或编码<input v-model.trim="filters.role" type="search" placeholder="搜索当前页" /></label><label>状态<select v-model="filters.roleStatus"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></label></FilterBar><p class="muted">筛选当前页。</p>
      <DataTable v-if="!error" :columns="[{key:'roleName',label:'角色名称'},{key:'roleCode',label:'角色编码'},{key:'systemFixed',label:'固定角色'},{key:'status',label:'状态'},{key:'actions',label:'操作'}]" :rows="roleRows" :loading="loading" :empty-text="filters.role || filters.roleStatus ? '当前页没有匹配角色' : '暂无角色'">
        <template #systemFixed="{value}"><StatusTag :value="value ? '系统固定' : '自定义'" :tone="value ? 'info' : 'success'" /></template><template #status="{value}"><StatusTag :value="value === 1 ? '启用' : '停用'" :tone="value === 1 ? 'success' : 'warning'" /></template>
        <template #actions="{row}"><div class="ui-inline-actions"><button type="button" @click="openRolePermissions(row)">配置权限</button><button v-if="!row.systemFixed" type="button" @click="roleMore = row">更多</button></div></template>
      </DataTable><PaginationBar :page-no="rolePage" :page-size="20" :total="roleTotal" @change="loadRoles" />
    </section>
    <section v-if="tab === 'permissions' && can('permission:manage')">
      <div class="ui-list-heading"><h2>权限目录</h2><span class="muted">只读 · 共 {{ permissions.length }} 项</span></div><FilterBar><label class="ui-search">权限名称或编码<input v-model.trim="filters.permission" type="search" placeholder="搜索权限" @input="directoryPage = 1" /></label><label>业务模块<select v-model="filters.module" @change="directoryPage = 1"><option value="">全部模块</option><option v-for="module in modules" :key="module" :value="module">{{ businessModules[module] || '其他' }}</option></select></label></FilterBar>
      <DataTable v-if="!error" :columns="[{key:'module',label:'模块'},{key:'permissionName',label:'权限名称'},{key:'permissionCode',label:'权限编码'},{key:'description',label:'说明'}]" :rows="visibleDirectory" :loading="loading" :empty-text="filters.permission || filters.module ? '没有匹配权限' : '暂无权限'">
        <template #module="{row}">{{ moduleName(row.permissionCode) }}</template><template #permissionCode="{value}"><small class="muted">{{ value }}</small></template><template #description="{row}">{{ displayValue('permissionType', row.permissionType) }}权限</template>
      </DataTable><PaginationBar :page-no="directoryPage" :page-size="15" :total="directoryRows.length" @change="directoryPage = $event" />
    </section>
    <section v-if="tab === 'audits' && can('audit:view')"><div class="ui-list-heading"><h2>操作日志</h2><button type="button" @click="loadAudits(1)">查询</button></div><FilterBar><label>模块<input v-model.trim="auditFilter.module" /></label><label>业务类型<input v-model.trim="auditFilter.businessType" /></label><label>操作人编号<input v-model.trim="auditFilter.operatorId" /></label><label>开始时间<input v-model="auditFilter.startTime" type="datetime-local" /></label><label>结束时间<input v-model="auditFilter.endTime" type="datetime-local" /></label></FilterBar><DataTable v-if="!error" :columns="[{key:'operationTime',label:'时间'},{key:'module',label:'模块'},{key:'businessType',label:'业务'},{key:'operationType',label:'操作'},{key:'operatorId',label:'操作人'},{key:'reason',label:'原因'},{key:'beforeJson',label:'变更前'},{key:'afterJson',label:'变更后'}]" :rows="audits" :loading="loading" empty-text="暂无操作日志"><template #beforeJson="{value}"><code class="json-inline">{{ value || '—' }}</code></template><template #afterJson="{value}"><code class="json-inline">{{ value || '—' }}</code></template></DataTable><PaginationBar v-if="!error" :page-no="auditPage" :page-size="20" :total="auditTotal" @change="loadAudits" /></section>
    <FormDialog :open="!!userMore" title="用户操作" @close="userMore = null"><div v-if="userMore"><p>{{ userMore.phone }} · {{ userMore.roleName }}</p><div class="ui-object-actions"><button type="button" @click="openReset(userMore)">重置密码</button><button type="button" :class="userMore.status === 1 ? 'ui-danger-link' : ''" @click="openUserToggle(userMore)">{{ userMore.status === 1 ? '停用' : '启用' }}</button></div></div></FormDialog>
    <FormDialog :open="userDialog" :title="userForm.id ? '编辑用户' : '新增用户'" :busy="busy" @close="userDialog = false"><form @submit.prevent="saveUser"><div class="ui-form-grid"><label class="ui-form-field">手机号<input v-model.trim="userForm.phone" required inputmode="tel" /></label><label v-if="!userForm.id" class="ui-form-field">初始密码<input v-model="userForm.password" required type="password" autocomplete="new-password" /></label><label class="ui-form-field">角色<EntityPicker v-model="userForm.roleId" :load-page="systemApi.roles" :label-of="(item) => item.roleName" :selected-label="roleName(userForm.roleId)" placeholder="请选择角色" required /></label><label class="ui-form-field">状态<select v-model.number="userForm.status"><option :value="1">启用</option><option :value="0">停用</option></select></label></div><div class="ui-dialog-actions"><button type="button" @click="userDialog = false">取消</button><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '保存用户' }}</button></div></form></FormDialog>
    <FormDialog :open="!!resetTarget" title="重置密码" :busy="busy" @close="resetTarget = null"><form v-if="resetTarget" @submit.prevent="savePassword"><p>账号：<strong>{{ resetTarget.phone }}</strong> · {{ resetTarget.roleName }}</p><p class="muted">重置后旧密码将无法继续用于登录。</p><label class="ui-form-field">新密码<input v-model="newPassword" required type="password" autocomplete="new-password" /></label><label class="ui-form-field">确认新密码<input v-model="confirmPassword" required type="password" autocomplete="new-password" /></label><div class="ui-dialog-actions"><button type="button" @click="resetTarget = null">取消</button><button class="ui-danger-button" :disabled="busy">确认重置密码</button></div></form></FormDialog>
    <ConfirmDialog :open="!!userToggle" :title="userToggle?.status === 1 ? '停用账号' : '启用账号'" :message="userToggle ? `${userToggle.status === 1 ? '停用' : '启用'} ${userToggle.phone}（${userToggle.roleName}）？停用后该账号无法使用系统。` : ''" :danger="userToggle?.status === 1" :busy="busy" @cancel="userToggle = null" @confirm="toggleUser" />
    <FormDialog :open="roleDialog" :title="roleForm.id ? '编辑角色' : '新增角色'" :busy="busy" @close="roleDialog = false"><form @submit.prevent="saveRole"><div class="ui-form-grid"><label class="ui-form-field">角色名称<input v-model.trim="roleForm.roleName" required /></label><label class="ui-form-field">角色编码<input v-model.trim="roleForm.roleCode" required :readonly="!!roleForm.id" /></label><label class="ui-form-field">状态<select v-model.number="roleForm.status"><option :value="1">启用</option><option :value="0">停用</option></select></label></div><div class="ui-dialog-actions"><button type="button" @click="roleDialog = false">取消</button><button class="primary" :disabled="busy">保存角色</button></div></form></FormDialog>
    <FormDialog :open="!!roleMore" title="角色操作" @close="roleMore = null"><div v-if="roleMore"><p>{{ roleMore.roleName }} · {{ roleMore.roleCode }}</p><div class="ui-object-actions"><button type="button" @click="openRoleEdit(roleMore)">编辑角色</button><button type="button" :class="roleMore.status === 1 ? 'ui-danger-link' : ''" @click="openRoleToggle(roleMore)">{{ roleMore.status === 1 ? '停用' : '启用' }}</button></div></div></FormDialog>
    <ConfirmDialog :open="!!roleToggle" :title="roleToggle?.status === 1 ? '停用角色' : '启用角色'" :message="roleToggle ? `${roleToggle.status === 1 ? '停用' : '启用'}角色 ${roleToggle.roleName}？关联用户的权限将受到影响。` : ''" :danger="roleToggle?.status === 1" :busy="busy" @cancel="roleToggle = null" @confirm="toggleRole" />
    <DetailDrawer :open="!!permissionRole" title="配置角色权限" @close="permissionRole = null"><template #subtitle>{{ permissionRole?.roleName }} · {{ permissionRole?.roleCode }}</template><div v-if="permissionRole"><label class="ui-form-field">搜索权限<input v-model.trim="permissionSearch" type="search" placeholder="权限名称或编码" /></label><p class="muted">已选 {{ selectedCodes.length }} 项 · 新增 {{ addedCodes.length }} 项 · 移除 {{ removedCodes.length }} 项</p><div v-for="(items, module) in permissionGroups" :key="module" class="ui-permission-group"><div class="ui-permission-group-head"><h3>{{ businessModules[module] || '其他' }}</h3><button type="button" @click="toggleModule(items, !items.every((item) => selectedCodes.includes(item.permissionCode)))">{{ items.every((item) => selectedCodes.includes(item.permissionCode)) ? '取消本组' : '选择本组' }}</button></div><label v-for="item in items" :key="item.permissionCode" class="ui-permission-item"><input type="checkbox" :checked="selectedCodes.includes(item.permissionCode)" @change="togglePermission(item.permissionCode)" /><span><strong>{{ item.permissionName }}</strong><small>{{ item.permissionCode }} · {{ displayValue('permissionType', item.permissionType) }}</small></span></label></div></div><template #footer><span class="muted">保存前确认新增与移除项目</span><button type="button" @click="permissionRole = null">取消</button><button class="primary" type="button" :disabled="!addedCodes.length && !removedCodes.length" @click="permissionConfirm = true">保存权限</button></template></DetailDrawer>
    <ConfirmDialog :open="permissionConfirm" title="确认角色权限变更" :message="`为 ${permissionRole?.roleName || '角色'} 新增 ${addedCodes.length} 项、移除 ${removedCodes.length} 项权限？保存后重新登录的用户将使用新权限。`" :busy="busy" @cancel="permissionConfirm = false" @confirm="savePermissions" />
  </div>
</template>
