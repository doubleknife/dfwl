<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import ApprovalTemplateForm from '../components/ApprovalTemplateForm.vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { approvalTemplateApi, systemApi } from '../api/modules.js';
import {
  attributionLabels, canConfigureNext, canMaintain, canPublish, categoryLabels, emptyForm,
  evidenceLabels, expenseLabels, formFromConfiguration, formPayload, modeLabels, statusLabels,
  templateSummary
} from './approvalTemplateModel.js';

const loading = ref(false);
const error = ref('');
const notice = ref('');
const categoryFilter = ref('');
const templates = ref([]);
const versions = ref([]);
const roles = ref([]);
const users = ref([]);
const selectedTemplate = ref(null);
const configuration = ref(null);
const createOpen = ref(false);
const nextOpen = ref(false);
const pendingAction = ref('');
const createForm = reactive(emptyForm());
const nextForm = reactive(emptyForm());
const currentFlow = computed(() => configuration.value?.flow || null);
const displayedTemplates = computed(() => templates.value.filter((template) =>
  !categoryFilter.value || template.category === categoryFilter.value));
const latestVersion = computed(() => Math.max(0, ...versions.value.map((version) => version.versionNo)));
const historical = computed(() => currentFlow.value && currentFlow.value.versionNo < latestVersion.value);
const statusTone = { DRAFT: 'info', ACTIVE: 'neutral', MAINTENANCE: 'warning' };

async function execute(task) {
  loading.value = true;
  error.value = '';
  notice.value = '';
  try {
    return await task();
  } catch (caught) {
    error.value = `${caught.message || '请求失败'}${caught.requestId ? `（requestId: ${caught.requestId}）` : ''}`;
    return null;
  } finally {
    loading.value = false;
  }
}

async function fetchCatalog() {
  const list = await approvalTemplateApi.list();
  const allVersions = await Promise.all(list.map((template) => approvalTemplateApi.versions(template.id)));
  templates.value = list.map((template, index) => templateSummary(template, allVersions[index]));
}

async function openTemplate(templateId, flowId = null) {
  selectedTemplate.value = await approvalTemplateApi.detail(templateId);
  versions.value = await approvalTemplateApi.versions(templateId);
  const preferred = versions.value.find((version) => version.id === flowId)
    || versions.value.find((version) => version.id === selectedTemplate.value.activeFlowId)
    || versions.value.reduce((latest, version) => !latest || version.versionNo > latest.versionNo
      ? version : latest, null);
  configuration.value = preferred ? await approvalTemplateApi.version(templateId, preferred.id) : null;
  nextOpen.value = false;
  createOpen.value = false;
  pendingAction.value = '';
}

async function load() {
  await execute(async () => {
    await fetchCatalog();
    const [rolePage, userPage] = await Promise.all([systemApi.roles(), systemApi.users()]);
    roles.value = (rolePage.records || rolePage || []).filter((role) => role.status === 1);
    users.value = (userPage.records || userPage || []).filter((user) => user.status === 1);
    if (selectedTemplate.value) {
      const id = selectedTemplate.value.id;
      if (templates.value.some((item) => item.id === id)) await openTemplate(id, currentFlow.value?.id);
      else { selectedTemplate.value = null; configuration.value = null; }
    }
  });
}

async function selectTemplate(row) {
  await execute(() => openTemplate(row.id));
}

async function selectVersion(row) {
  await execute(async () => {
    configuration.value = await approvalTemplateApi.version(selectedTemplate.value.id, row.id);
    nextOpen.value = false;
  });
}

function startCreate() {
  Object.assign(createForm, emptyForm());
  createOpen.value = true;
  nextOpen.value = false;
  error.value = '';
}

async function createTemplate() {
  await execute(async () => {
    const body = formPayload(createForm, { creating: true });
    const created = await approvalTemplateApi.create(body);
    await fetchCatalog();
    const template = templates.value.find((item) => item.latestFlowId === created.flow.id);
    if (!template) throw new Error('模板创建成功，但列表中尚未找到新版本；请刷新后查看');
    await openTemplate(template.id, created.flow.id);
    notice.value = `已创建 ${template.templateCode} 的草稿 V${created.flow.versionNo}，发布前不会成为 ACTIVE。`;
  });
}

async function maintenance() {
  if (!canMaintain(currentFlow.value, versions.value)) return;
  pendingAction.value = '';
  await execute(async () => {
    const flowId = currentFlow.value.id;
    await approvalTemplateApi.maintenance(flowId);
    await fetchCatalog();
    await openTemplate(selectedTemplate.value.id, flowId);
    Object.assign(nextForm, formFromConfiguration(configuration.value, selectedTemplate.value.category));
    nextOpen.value = true;
    notice.value = '已进入维护。请调整配置并一次保存为下一版本；现有接口不直接改写草稿版本。';
  });
}

function configureNext() {
  if (!canConfigureNext(currentFlow.value, versions.value)) return;
  Object.assign(nextForm, formFromConfiguration(configuration.value, selectedTemplate.value.category));
  nextOpen.value = true;
  error.value = '';
}

async function saveNext() {
  await execute(async () => {
    if (!canConfigureNext(currentFlow.value, versions.value)) throw new Error('只有最新维护中版本可生成下一版');
    const created = await approvalTemplateApi.createNextVersion(currentFlow.value.id, formPayload(nextForm));
    const templateId = selectedTemplate.value.id;
    await fetchCatalog();
    await openTemplate(templateId, created.id);
    notice.value = `已生成草稿 V${created.versionNo}；请核对配置后发布。`;
  });
}

async function publish() {
  if (!canPublish(currentFlow.value)) return;
  pendingAction.value = '';
  await execute(async () => {
    const published = await approvalTemplateApi.publish(currentFlow.value.id);
    const templateId = selectedTemplate.value.id;
    await fetchCatalog();
    await openTemplate(templateId, published.id);
    notice.value = `V${published.versionNo} 已发布为当前 ACTIVE 版本。`;
  });
}

function roleNames(ids) {
  return (ids || []).map((id) => {
    const role = roles.value.find((item) => item.id === id);
    return role ? `${role.roleName}（${role.roleCode}）` : `角色 #${id}`;
  }).join('、') || '未配置';
}

function approverName(id) {
  const user = users.value.find((item) => item.id === id);
  return user ? `${user.phone}（${user.roleName || user.roleCode}）` : `用户 #${id}`;
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="notice" role="status" class="template-notice">{{ notice }}</p>

    <PanelSection title="审批模板" description="按稳定模板管理各自的审批版本；同类别模板可以分别发布。">
      <template #actions>
        <div class="toolbar template-toolbar">
          <select v-model="categoryFilter" aria-label="筛选模板类别">
            <option value="">全部类别</option>
            <option value="TIRE">换胎</option>
            <option value="EXPENSE">费用</option>
            <option value="OTHER">其他</option>
          </select>
          <button type="button" :disabled="loading" @click="load">刷新</button>
          <button type="button" class="primary" :disabled="loading" @click="startCreate">新建模板</button>
        </div>
      </template>
      <p v-if="loading && !templates.length" class="muted">正在加载模板及角色…</p>
      <DataTable :columns="[
        { key: 'displayName', label: '显示名称' }, { key: 'templateCode', label: '模板代码' },
        { key: 'category', label: '类别' }, { key: 'activeVersion', label: 'ACTIVE 版本' },
        { key: 'currentStatus', label: '当前状态' }, { key: 'actions', label: '操作' }
      ]" :rows="displayedTemplates" empty-text="暂无模板">
        <template #category="{ value }">{{ categoryLabels[value] || value }} · {{ value }}</template>
        <template #currentStatus="{ value }"><StatusBadge :value="statusLabels[value] || value" :tone="statusTone[value] || 'neutral'" /></template>
        <template #actions="{ row }"><button type="button" :disabled="loading" @click="selectTemplate(row)">查看版本</button></template>
      </DataTable>
    </PanelSection>

    <PanelSection v-if="createOpen" title="创建模板与草稿 V1" description="创建后仍是 DRAFT；发布是独立操作。">
      <ApprovalTemplateForm :form="createForm" :roles="roles" :users="users" creating
        :busy="loading" submit-label="创建草稿 V1" @submit="createTemplate" @cancel="createOpen = false" />
    </PanelSection>

    <template v-if="selectedTemplate">
      <PanelSection :title="`${selectedTemplate.templateCode} · ${categoryLabels[selectedTemplate.category] || selectedTemplate.category}`"
        description="模板 ID 与 Flow ID 分开；版本历史不会被新发布覆盖。">
        <div class="template-summary">
          <span>模板 ID：{{ selectedTemplate.id }}</span>
          <span>业务类型：{{ selectedTemplate.approvalType }}</span>
          <span>当前 ACTIVE Flow ID：{{ selectedTemplate.activeFlowId ?? '无' }}</span>
        </div>
        <DataTable :columns="[
          { key: 'versionNo', label: '版本' }, { key: 'flowName', label: '版本名称' },
          { key: 'status', label: '状态' }, { key: 'id', label: 'Flow ID' }, { key: 'actions', label: '操作' }
        ]" :rows="versions" empty-text="暂无版本">
          <template #versionNo="{ value }">V{{ value }}</template>
          <template #status="{ value }"><StatusBadge :value="statusLabels[value] || value" :tone="statusTone[value] || 'neutral'" /></template>
          <template #actions="{ row }"><button type="button" :disabled="loading" @click="selectVersion(row)">查看配置</button></template>
        </DataTable>
      </PanelSection>

      <PanelSection v-if="configuration" :title="`V${currentFlow.versionNo} · ${currentFlow.flowName}`"
        :description="historical ? '历史版本：只读，保留当时的角色、节点与费用配置。' : '当前选择的版本完整配置。'">
        <div class="template-summary">
          <StatusBadge :value="statusLabels[currentFlow.status] || currentFlow.status" :tone="statusTone[currentFlow.status] || 'neutral'" />
          <span>Flow ID：{{ currentFlow.id }}</span>
          <span>申请依据：{{ evidenceLabels[configuration.evidenceRequirement] || configuration.evidenceRequirement }}</span>
          <span>配置：{{ configuration.configurationStatus }}</span>
        </div>
        <p><strong>允许发起角色：</strong>{{ roleNames(configuration.initiatorRoleIds) }}</p>
        <p v-if="selectedTemplate.category === 'TIRE'" class="muted">
          换胎发起资格由 tire:request 与 TireAccessPolicy 决定，此角色集合不替代换胎授权。
        </p>
        <div v-if="selectedTemplate.category === 'EXPENSE' && configuration.expenseConfig" class="template-config-grid">
          <span><strong>费用类型：</strong>{{ expenseLabels[configuration.expenseConfig.expenseType] || configuration.expenseConfig.expenseType }}</span>
          <span><strong>录入方式：</strong>{{ configuration.expenseConfig.inputModes.map(value => modeLabels[value] || value).join('、') }}</span>
          <span><strong>费用归属：</strong>{{ configuration.expenseConfig.attributionTypes.map(value => attributionLabels[value] || value).join('、') }}</span>
        </div>
        <p v-if="selectedTemplate.category === 'OTHER'" class="muted">其他模板仅配置身份、依据、角色与节点；不配置动态表单。</p>
        <h3>审批节点</h3>
        <DataTable :columns="[
          { key: 'nodeOrder', label: '顺序' }, { key: 'nodeName', label: '节点' },
          { key: 'approverUserId', label: '审批人' }, { key: 'positionId', label: '岗位 ID' },
          { key: 'allowReturn', label: '允许打回' }
        ]" :rows="configuration.nodes || []">
          <template #approverUserId="{ value }">{{ approverName(value) }}</template>
          <template #allowReturn="{ value }">{{ value ? '是' : '否' }}</template>
        </DataTable>
        <div v-if="!historical" class="row-actions template-actions">
          <button v-if="canMaintain(currentFlow, versions)" type="button" :disabled="loading" @click="pendingAction = 'maintenance'">进入维护</button>
          <button v-if="canConfigureNext(currentFlow, versions)" type="button" :disabled="loading" @click="configureNext">配置下一版本</button>
          <button v-if="canPublish(currentFlow)" type="button" class="primary" :disabled="loading" @click="pendingAction = 'publish'">发布 V{{ currentFlow.versionNo }}</button>
        </div>
        <div v-if="pendingAction" class="template-confirm" role="alertdialog" aria-label="确认审批模板操作">
          <p v-if="pendingAction === 'maintenance'">进入维护会暂停此模板的新申请，并保留当前版本历史。确认继续？</p>
          <p v-else>确认发布 {{ selectedTemplate.templateCode }} V{{ currentFlow.versionNo }}？</p>
          <div class="row-actions">
            <button type="button" class="primary" :disabled="loading" @click="pendingAction === 'maintenance' ? maintenance() : publish()">确认</button>
            <button type="button" :disabled="loading" @click="pendingAction = ''">取消</button>
          </div>
        </div>
        <p v-if="currentFlow.status === 'DRAFT'" class="muted">
          当前后端不直接改写 DRAFT；如需调整已生成的版本，请遵循后端维护流程。
        </p>
      </PanelSection>

      <PanelSection v-if="nextOpen" title="配置并生成下一版本" description="修改将在保存时生成新的 DRAFT；原版本保持历史事实。">
        <ApprovalTemplateForm :form="nextForm" :roles="roles" :users="users"
          :busy="loading" submit-label="保存为下一版本" @submit="saveNext" @cancel="nextOpen = false" />
      </PanelSection>
    </template>
  </div>
</template>
