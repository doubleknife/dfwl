<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import { systemApi } from '../api/modules.js';
import { confirmAndRun, useRequest } from '../composables/useRequest.js';
import { can } from '../stores/auth.js';

const { loading, error, run } = useRequest();
const tab = ref('users');
const state = reactive({ users: [], roles: [], permissions: [], audits: [] });
const userForm = reactive({ id: '', phone: '', password: '', roleId: '', status: 1 });
const resetForm = reactive({ userId: '', password: '' });
const roleForm = reactive({ id: '', roleCode: '', roleName: '', status: 1 });
const permissionFilter = ref('');
const rolePermission = reactive({ roleId: '', selectedCodes: [] });
const auditFilter = reactive({ module: '', businessType: '', operatorId: '', startTime: '', endTime: '' });

const permissionGroups = computed(() => {
  return state.permissions.reduce((groups, item) => {
    const key = item.permissionType || 'UNKNOWN';
    groups[key] = groups[key] || [];
    groups[key].push(item);
    return groups;
  }, {});
});

function firstAllowedTab() {
  if (can('user:manage')) return 'users';
  if (can('role:manage')) return 'roles';
  if (can('permission:manage')) return 'permissions';
  if (can('audit:view')) return 'audits';
  return 'users';
}

async function load() {
  await run(async () => {
    const [users, roles, permissions, audits] = await Promise.all([
      can('user:manage') ? systemApi.users() : Promise.resolve({ records: [] }),
      can('role:manage') ? systemApi.roles() : Promise.resolve({ records: [] }),
      can('permission:manage') ? systemApi.permissions(permissionFilter.value) : Promise.resolve([]),
      can('audit:view') ? systemApi.auditLogs(normalizedAuditFilter()) : Promise.resolve({ records: [] })
    ]);
    state.users = users.records || users || [];
    state.roles = roles.records || roles || [];
    state.permissions = permissions || [];
    state.audits = audits.records || audits || [];
  });
}

function normalizedAuditFilter() {
  return {
    module: auditFilter.module,
    businessType: auditFilter.businessType,
    operatorId: auditFilter.operatorId,
    startTime: auditFilter.startTime,
    endTime: auditFilter.endTime
  };
}

function editUser(row) {
  Object.assign(userForm, { id: row.id, phone: row.phone, password: '', roleId: row.roleId, status: row.status });
}

function resetUserForm() {
  Object.assign(userForm, { id: '', phone: '', password: '', roleId: '', status: 1 });
}

async function saveUser() {
  await run(async () => {
    const body = {
      phone: userForm.phone,
      password: userForm.id ? null : userForm.password,
      roleId: Number(userForm.roleId),
      status: Number(userForm.status)
    };
    if (userForm.id) {
      await systemApi.updateUser(Number(userForm.id), body);
    } else {
      await systemApi.createUser(body);
    }
    resetUserForm();
    await load();
  });
}

async function toggleUser(row) {
  await confirmAndRun(`确认${row.status === 1 ? '停用' : '启用'}该账号？`, async () => run(async () => {
    await systemApi.setStatus(row.id, { status: row.status === 1 ? 0 : 1 });
    await load();
  }));
}

async function resetPassword() {
  await confirmAndRun('确认重置该账号密码？', async () => run(async () => {
    await systemApi.resetPassword(Number(resetForm.userId), { newPassword: resetForm.password });
    Object.assign(resetForm, { userId: '', password: '' });
  }));
}

function editRole(row) {
  Object.assign(roleForm, { id: row.id, roleCode: row.roleCode, roleName: row.roleName, status: row.status });
}

function resetRoleForm() {
  Object.assign(roleForm, { id: '', roleCode: '', roleName: '', status: 1 });
}

async function saveRole() {
  await run(async () => {
    const body = { roleCode: roleForm.roleCode, roleName: roleForm.roleName, status: Number(roleForm.status) };
    if (roleForm.id) {
      await systemApi.updateRole(Number(roleForm.id), body);
    } else {
      await systemApi.createRole(body);
    }
    resetRoleForm();
    await load();
  });
}

async function toggleRole(row) {
  await confirmAndRun(`确认${row.status === 1 ? '停用' : '启用'}该角色？`, async () => run(async () => {
    await systemApi.setRoleStatus(row.id, row.status === 1 ? 0 : 1);
    await load();
  }));
}

async function configureRole(row) {
  await run(async () => {
    const detail = await systemApi.role(row.id);
    rolePermission.roleId = detail.id;
    rolePermission.selectedCodes = (detail.permissions || []).map((item) => item.permissionCode);
    tab.value = 'rolePermissions';
  });
}

function togglePermission(code) {
  const index = rolePermission.selectedCodes.indexOf(code);
  if (index >= 0) rolePermission.selectedCodes.splice(index, 1);
  else rolePermission.selectedCodes.push(code);
}

async function saveRolePermissions() {
  await run(async () => {
    await systemApi.saveRolePermissions(Number(rolePermission.roleId), rolePermission.selectedCodes);
    await load();
  });
}

async function loadPermissions() {
  await run(async () => {
    state.permissions = await systemApi.permissions(permissionFilter.value);
  });
}

async function loadAudits() {
  await run(async () => {
    const page = await systemApi.auditLogs(normalizedAuditFilter());
    state.audits = page.records || page || [];
  });
}

onMounted(() => {
  tab.value = firstAllowedTab();
  load();
});
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="tabs">
      <button v-if="can('user:manage')" :class="{ active: tab === 'users' }" @click="tab = 'users'">用户</button>
      <button v-if="can('role:manage')" :class="{ active: tab === 'roles' }" @click="tab = 'roles'">角色</button>
      <button v-if="can('permission:manage')" :class="{ active: tab === 'permissions' }" @click="tab = 'permissions'">权限</button>
      <button v-if="can('audit:view')" :class="{ active: tab === 'audits' }" @click="tab = 'audits'">操作日志</button>
    </div>

    <PanelSection v-if="tab === 'users'" title="用户管理">
      <div class="two-column">
        <form v-permission="'user:manage'" class="form-grid" @submit.prevent="saveUser">
          <input v-model.trim="userForm.phone" required aria-label="手机号" placeholder="手机号" />
          <input v-if="!userForm.id" v-model="userForm.password" required aria-label="初始密码" placeholder="初始密码" />
          <select v-model.number="userForm.roleId" required aria-label="角色">
            <option value="" disabled>选择单一角色</option>
            <option v-for="role in state.roles" :key="role.id" :value="role.id">{{ role.roleName }} / {{ role.roleCode }}</option>
          </select>
          <select v-model.number="userForm.status" aria-label="状态"><option :value="1">启用</option><option :value="0">停用</option></select>
          <div class="row-actions">
            <button class="primary" :disabled="loading">{{ userForm.id ? '保存用户' : '新增用户' }}</button>
            <button type="button" @click="resetUserForm">清空</button>
          </div>
        </form>
        <form v-permission="'user:manage'" class="form-grid" @submit.prevent="resetPassword">
          <input v-model="resetForm.userId" required aria-label="用户 ID" placeholder="用户 ID" />
          <input v-model="resetForm.password" required aria-label="新密码" placeholder="新密码" />
          <button type="submit">重置密码</button>
        </form>
      </div>
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'phone', label: '手机号' }, { key: 'roleName', label: '角色' },
        { key: 'status', label: '状态' }, { key: 'lastLoginAt', label: '最后登录' }, { key: 'actions', label: '操作' }
      ]" :rows="state.users">
        <template #status="{ value }">{{ value === 1 ? '启用' : '停用' }}</template>
        <template #actions="{ row }">
          <div class="row-actions">
            <button v-permission="'user:manage'" type="button" @click="editUser(row)">编辑</button>
            <button v-permission="'user:manage'" type="button" @click="toggleUser(row)">{{ row.status === 1 ? '停用' : '启用' }}</button>
          </div>
        </template>
      </DataTable>
    </PanelSection>

    <PanelSection v-if="tab === 'roles'" title="角色管理">
      <form v-permission="'role:manage'" class="inline-form" @submit.prevent="saveRole">
        <input v-model.trim="roleForm.roleCode" required aria-label="角色编码" placeholder="角色编码" />
        <input v-model.trim="roleForm.roleName" required aria-label="角色名称" placeholder="角色名称" />
        <select v-model.number="roleForm.status" aria-label="角色状态"><option :value="1">启用</option><option :value="0">停用</option></select>
        <button class="primary" :disabled="loading">{{ roleForm.id ? '保存角色' : '新增角色' }}</button>
        <button type="button" @click="resetRoleForm">清空</button>
      </form>
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'roleCode', label: '编码' }, { key: 'roleName', label: '名称' },
        { key: 'systemFixed', label: '系统内置' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }
      ]" :rows="state.roles">
        <template #systemFixed="{ value }">{{ value ? '是' : '否' }}</template>
        <template #status="{ value }">{{ value === 1 ? '启用' : '停用' }}</template>
        <template #actions="{ row }">
          <div class="row-actions">
            <button v-permission="'role:manage'" type="button" @click="editRole(row)">编辑</button>
            <button v-permission="'role:manage'" type="button" @click="toggleRole(row)">{{ row.status === 1 ? '停用' : '启用' }}</button>
            <button v-permission="'role:manage'" type="button" @click="configureRole(row)">配置权限</button>
          </div>
        </template>
      </DataTable>
    </PanelSection>

    <PanelSection v-if="tab === 'rolePermissions'" title="角色权限配置">
      <template #actions><button @click="tab = 'roles'">返回角色</button></template>
      <div class="permission-grid">
        <label v-for="permission in state.permissions" :key="permission.permissionCode" class="check-row">
          <input :checked="rolePermission.selectedCodes.includes(permission.permissionCode)" type="checkbox" @change="togglePermission(permission.permissionCode)" />
          <span>{{ permission.permissionCode }}</span>
          <small>{{ permission.permissionName }} / {{ permission.permissionType }}</small>
        </label>
      </div>
      <div class="row-actions">
        <button v-permission="'role:manage'" class="primary" :disabled="!rolePermission.roleId || loading" @click="saveRolePermissions">保存权限</button>
      </div>
    </PanelSection>

    <PanelSection v-if="tab === 'permissions'" title="权限管理">
      <template #actions>
        <div class="toolbar">
          <select v-model="permissionFilter" aria-label="权限类型">
            <option value="">全部</option>
            <option>MENU</option>
            <option>BUTTON</option>
            <option>API</option>
            <option>MINI_PROGRAM</option>
          </select>
          <button class="primary" @click="loadPermissions">查询</button>
        </div>
      </template>
      <div class="permission-groups">
        <section v-for="(items, type) in permissionGroups" :key="type">
          <h2>{{ type }}</h2>
          <DataTable :columns="[
            { key: 'permissionCode', label: '权限码' }, { key: 'permissionName', label: '名称' },
            { key: 'permissionType', label: '类型' }, { key: 'status', label: '状态' }
          ]" :rows="items">
            <template #status="{ value }">{{ value === 1 ? '启用' : '停用' }}</template>
          </DataTable>
        </section>
      </div>
    </PanelSection>

    <PanelSection v-if="tab === 'audits'" title="操作日志">
      <template #actions><button class="primary" @click="loadAudits">查询</button></template>
      <div class="inline-form">
        <input v-model.trim="auditFilter.module" aria-label="模块" placeholder="模块" />
        <input v-model.trim="auditFilter.businessType" aria-label="业务类型" placeholder="业务类型" />
        <input v-model.trim="auditFilter.operatorId" aria-label="操作人" placeholder="操作人 ID" />
        <input v-model="auditFilter.startTime" type="datetime-local" aria-label="开始时间" />
        <input v-model="auditFilter.endTime" type="datetime-local" aria-label="结束时间" />
      </div>
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'module', label: '模块' }, { key: 'businessType', label: '业务' },
        { key: 'operationType', label: '操作' }, { key: 'operatorId', label: '操作人' },
        { key: 'operationTime', label: '时间' }, { key: 'reason', label: '原因' },
        { key: 'beforeJson', label: 'Before' }, { key: 'afterJson', label: 'After' }
      ]" :rows="state.audits">
        <template #beforeJson="{ value }"><code class="json-inline">{{ value || '-' }}</code></template>
        <template #afterJson="{ value }"><code class="json-inline">{{ value || '-' }}</code></template>
      </DataTable>
    </PanelSection>
  </div>
</template>
