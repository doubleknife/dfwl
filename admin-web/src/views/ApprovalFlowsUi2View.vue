<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { approvalTemplateApi, systemApi } from '../api/modules.js';
import { loadAllPages } from './approvalApplicationModel.js';
import { attributionLabels, canConfigureNext, canMaintain, canPublish, categoryLabels, emptyForm, evidenceLabels, expenseLabels, formFromConfiguration, formPayload, modeLabels, statusLabels, templateSummary } from './approvalTemplateModel.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import StatusTag from '../components/StatusTag.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ApprovalTemplateWizard from '../components/ApprovalTemplateWizard.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ErrorState from '../components/ErrorState.vue';

const mode = ref('list');
const templates = ref([]);
const versions = ref([]);
const selected = ref(null);
const config = ref(null);
const roles = ref([]);
const users = ref([]);
const editorKind = ref('create');
const form = reactive(emptyForm());
const loading = ref(false);
const error = ref('');
const notice = ref('');
const categoryFilter = ref('');
const search = ref('');
const page = ref(1);
const confirmation = ref('');
const filtered = computed(() => templates.value.filter((row) => (!categoryFilter.value || row.category === categoryFilter.value) && (!search.value || `${row.displayName} ${row.templateCode}`.toLowerCase().includes(search.value.toLowerCase()))));
const visible = computed(() => filtered.value.slice((page.value - 1) * 15, page.value * 15));
const current = computed(() => config.value?.flow || null);
const latest = computed(() => Math.max(0, ...versions.value.map((row) => row.versionNo)));
const historical = computed(() => current.value && current.value.versionNo < latest.value);
function roleNames(ids) { return (ids || []).map((id) => roles.value.find((row) => row.id === id)?.roleName || '原角色已停用').join('、') || '未配置'; }
function approverName(id) { return users.value.find((row) => row.id === id)?.phone || '原审批人已停用'; }
async function run(task) { loading.value = true; error.value = ''; try { return await task(); } catch (cause) { error.value = `${cause.message || '操作失败'}${cause.requestId ? `（requestId: ${cause.requestId}）` : ''}`; return null; } finally { loading.value = false; } }
async function fetchCatalog() {
  const list = await approvalTemplateApi.list();
  const histories = await Promise.all(list.map((row) => approvalTemplateApi.versions(row.id)));
  templates.value = list.map((row, i) => templateSummary(row, histories[i]));
}
async function load() {
  await run(async () => {
    await fetchCatalog();
    const [allRoles, allUsers] = await Promise.all([loadAllPages(systemApi.roles), loadAllPages(systemApi.users)]);
    roles.value = allRoles.filter((row) => row.status === 1); users.value = allUsers.filter((row) => row.status === 1);
  });
}
async function openTemplate(row, versionId = null) {
  await run(async () => {
    selected.value = await approvalTemplateApi.detail(row.id);
    versions.value = await approvalTemplateApi.versions(row.id);
    const chosen = versions.value.find((item) => item.id === versionId) || versions.value.find((item) => item.id === selected.value.activeFlowId) || [...versions.value].sort((a,b) => b.versionNo-a.versionNo)[0];
    config.value = chosen ? await approvalTemplateApi.version(row.id, chosen.id) : null;
    mode.value = 'detail';
  });
}
function startCreate() { Object.assign(form, emptyForm()); editorKind.value = 'create'; mode.value = 'editor'; error.value = ''; notice.value = ''; }
function startNext() { if (!canConfigureNext(current.value, versions.value)) return; Object.assign(form, formFromConfiguration(config.value, selected.value.category)); editorKind.value = 'next'; mode.value = 'editor'; error.value = ''; }
async function save() {
  await run(async () => {
    if (editorKind.value === 'create') {
      const created = await approvalTemplateApi.create(formPayload(form, { creating: true }));
      await fetchCatalog();
      const found = templates.value.find((row) => row.latestFlowId === created.flow.id);
      if (!found) throw new Error('草稿已创建，请刷新模板列表');
      await openTemplate(found, created.flow.id);
      notice.value = `草稿 V${created.flow.versionNo} 已创建，发布前不会生效。`;
    } else {
      const created = await approvalTemplateApi.createNextVersion(current.value.id, formPayload(form));
      const row = selected.value;
      await fetchCatalog(); await openTemplate(row, created.id);
      notice.value = `草稿 V${created.versionNo} 已创建；旧版本保持不变。`;
    }
  });
}
async function confirmAction() {
  const kind = confirmation.value; confirmation.value = '';
  await run(async () => {
    const flowId = current.value.id; const row = selected.value;
    if (kind === 'publish') { const published = await approvalTemplateApi.publish(flowId); await fetchCatalog(); await openTemplate(row, published.id); notice.value = `V${published.versionNo} 已发布为当前版本。`; }
    if (kind === 'maintenance') { await approvalTemplateApi.maintenance(flowId); await fetchCatalog(); await openTemplate(row, flowId); notice.value = '已进入维护；该模板新申请将暂停，旧历史不变。'; }
  });
}
onMounted(load);
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="审批模板" :description="mode === 'list' ? '按稳定模板查看当前发布版本。' : mode === 'editor' ? '分步核对配置后保存草稿。' : '版本历史保持只读，发布不会覆盖旧审批。'" />
    <ErrorState v-if="error" :message="error" @retry="mode === 'list' ? load() : error = ''" /><p v-if="notice" role="status" class="ui-notice">{{ notice }}</p>
    <template v-if="mode === 'list'"><div class="ui-list-heading"><h2>模板列表</h2><button class="primary" type="button" @click="startCreate">＋ 新建模板</button></div><FilterBar><label class="ui-search">模板名称或代码<input v-model.trim="search" type="search" @input="page=1" /></label><label>类别<select v-model="categoryFilter" @change="page=1"><option value="">全部类别</option><option value="EXPENSE">费用</option><option value="TIRE">换胎</option><option value="OTHER">其他</option></select></label><button type="button" @click="load">刷新</button></FilterBar><DataTable v-if="!error" :columns="[{key:'displayName',label:'模板名称'},{key:'category',label:'类别'},{key:'activeVersion',label:'当前发布版本'},{key:'currentStatus',label:'状态'},{key:'templateCode',label:'模板代码'},{key:'actions',label:'操作'}]" :rows="visible" :loading="loading" empty-text="没有符合条件的模板"><template #category="{value}">{{ categoryLabels[value] || value }}</template><template #currentStatus="{value}"><StatusTag :value="statusLabels[value] || value" /></template><template #templateCode="{value}"><small class="muted">{{ value }}</small></template><template #actions="{row}"><button type="button" @click="openTemplate(row)">查看版本</button></template></DataTable><PaginationBar v-if="!error" :page-no="page" :page-size="15" :total="filtered.length" @change="page=$event" /></template>
    <section v-else-if="mode === 'editor'" class="ui-workflow"><button type="button" @click="mode=selected ? 'detail' : 'list'">← 返回{{ selected ? '模板详情' : '模板列表' }}</button><div class="ui-section-card"><h2>{{ editorKind === 'create' ? '新建模板与草稿 V1' : '生成下一版本草稿' }}</h2><ApprovalTemplateWizard :key="`${editorKind}-${selected?.id || 'new'}`" :form="form" :roles="roles" :users="users" :creating="editorKind==='create'" :busy="loading" :submit-label="editorKind==='create' ? '保存草稿 V1' : '保存下一版本草稿'" @submit="save" @cancel="mode=selected ? 'detail' : 'list'" /></div></section>
    <section v-else-if="selected" class="ui-workflow"><button type="button" @click="mode='list'; selected=null; config=null">← 返回模板列表</button><div class="ui-section-card"><div class="ui-list-heading"><div><h2>{{ templates.find(row => row.id===selected.id)?.displayName || selected.templateCode }}</h2><p class="muted">{{ categoryLabels[selected.category] }} · 模板代码 {{ selected.templateCode }}</p></div><StatusTag :value="current?.status ? statusLabels[current.status] : '无版本'" /></div><h3>版本历史</h3><DataTable :columns="[{key:'versionNo',label:'版本'},{key:'flowName',label:'版本名称'},{key:'status',label:'状态'},{key:'actions',label:'操作'}]" :rows="versions" empty-text="暂无版本"><template #versionNo="{value}">V{{ value }}</template><template #status="{value}"><StatusTag :value="statusLabels[value] || value" /></template><template #actions="{row}"><button type="button" @click="openTemplate(selected,row.id)">查看配置</button></template></DataTable></div>
      <div v-if="config && current" class="ui-section-card"><div class="ui-list-heading"><div><h3>V{{ current.versionNo }} · {{ current.flowName }}</h3><p class="muted">{{ historical ? '历史版本，只读' : '当前选中版本' }}</p></div><StatusTag :value="statusLabels[current.status]" /></div><dl class="ui-detail-grid"><dt>申请依据</dt><dd>{{ evidenceLabels[config.evidenceRequirement] }}</dd><dt>允许发起角色</dt><dd>{{ roleNames(config.initiatorRoleIds) }}</dd><template v-if="selected.category==='EXPENSE' && config.expenseConfig"><dt>费用类型</dt><dd>{{ expenseLabels[config.expenseConfig.expenseType] }}</dd><dt>录入方式</dt><dd>{{ config.expenseConfig.inputModes.map(value => modeLabels[value]).join('、') }}</dd><dt>费用归属</dt><dd>{{ config.expenseConfig.attributionTypes.map(value => attributionLabels[value]).join('、') }}</dd></template></dl><h3>审批节点</h3><DataTable :columns="[{key:'nodeOrder',label:'顺序'},{key:'nodeName',label:'节点名称'},{key:'approverUserId',label:'审批人'},{key:'allowReturn',label:'允许打回'}]" :rows="config.nodes || []" row-key="nodeOrder"><template #approverUserId="{value}">{{ approverName(value) }}</template><template #allowReturn="{value}">{{ value ? '允许' : '不允许' }}</template></DataTable><div v-if="!historical" class="ui-dialog-actions"><button v-if="canMaintain(current,versions)" type="button" @click="confirmation='maintenance'">进入维护</button><button v-if="canConfigureNext(current,versions)" type="button" @click="startNext">配置下一版本</button><button v-if="canPublish(current)" class="primary" type="button" @click="confirmation='publish'">发布 V{{ current.versionNo }}</button></div><p v-if="current.status==='DRAFT'" class="muted">草稿不可直接改写；需要调整时按现有维护与版本流程生成下一版。</p></div></section>
    <ConfirmDialog :open="!!confirmation" :title="confirmation==='publish' ? '发布审批模板' : '进入模板维护'" :message="confirmation==='publish' ? `确认发布 ${selected?.templateCode || ''} V${current?.versionNo || ''}？旧审批和旧版本快照不会变化。` : `确认维护 ${selected?.templateCode || ''}？新申请将暂时不能使用此模板，旧历史保留。`" :busy="loading" :danger="confirmation==='maintenance'" @cancel="confirmation=''" @confirm="confirmAction" />
  </div>
</template>
