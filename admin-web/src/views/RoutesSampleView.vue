<script setup>
import { onMounted, reactive, ref } from 'vue';
import { masterApi, routeApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import { navigate } from '../router/routes.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import StatusTag from '../components/StatusTag.vue';
import FormDialog from '../components/FormDialog.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import ErrorState from '../components/ErrorState.vue';
import PaginationBar from '../components/PaginationBar.vue';

const rows = ref([]);
const customers = ref([]);
const products = ref([]);
const drivers = ref([]);
const vehicles = ref([]);
const pageNo = ref(1);
const total = ref(0);
const pageSize = 20;
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const filters = reactive({ date: '', status: '', customerId: '', driverId: '', keyword: '' });
const selected = ref(null);
const weights = ref([]);
const participants = ref([]);
const publishing = ref(null);
const publishForm = reactive({ initialSalary: '', outsourcedPlateNo: '' });
const transferring = ref(null);
const transferForm = reactive({ newDriverId: '', outsourcedPlateNo: '', newSalary: '', reason: '' });
const salaryDecisions = ref([]);
const salaryTarget = ref(null);
const salaryForm = reactive({ amount: '', reason: '' });
const detailTab = ref('overview');
const editing = ref(false);
const confirming = ref(null);
const operation = ref('');
const form = reactive({ businessDate: '', customerId: '', productId: '', direction: 'OUTBOUND', loadingPlace: '', unloadingPlace: '', taxUnitPrice: '', assignedDriverId: '', externalRouteNo: '', tripSequence: '' });
const unloadForm = reactive({ grossWeight: '', tareWeight: '' });
const adjustForm = reactive({ grossWeight: '', tareWeight: '', reason: '', attachmentIds: '' });
const columns = [{ key: 'routeNo', label: '线路号' }, { key: 'businessDate', label: '日期' }, { key: 'customerId', label: '客户' }, { key: 'assignedDriverId', label: '司机' }, { key: 'departureVehicleId', label: '车辆' }, { key: 'status', label: '状态' }, { key: 'netWeight', label: '净重', numeric: true }, { key: 'actions', label: '操作' }];
const customerName = (id) => customers.value.find((item) => item.id === id)?.customerName || (id ? `客户 #${id}` : '—');
const driverName = (id) => drivers.value.find((item) => item.id === id)?.name || (id ? `司机 #${id}` : '未分配');
const vehicleName = (id) => vehicles.value.find((item) => item.id === id)?.plateNo || (id ? `车辆 #${id}` : '未发车');
const selectedDriverType = (id) => drivers.value.find((item) => item.id === Number(id))?.driverType;
const selectedDriverVehicle = (id) => drivers.value.find((item) => item.id === Number(id))?.currentVehicleId;

async function load(next = pageNo.value) {
  loading.value = true; error.value = '';
  try {
    const page = await routeApi.list(next, pageSize, {
      businessDate: filters.date, status: filters.status, customerId: filters.customerId,
      driverId: filters.driverId, keyword: filters.keyword
    });
    rows.value = page.records || page || [];
    total.value = Number(page.total || 0);
    pageNo.value = next;
  } catch (cause) { error.value = cause.message || '线路加载失败'; }
  finally { loading.value = false; }
}
async function loadReferences() {
  try {
    const [customerPage, productPage, driverPage, vehiclePage] = await Promise.all([
      masterApi.customers(), masterApi.products(),
      can('driver:list') ? masterApi.drivers() : Promise.resolve({ records: [] }),
      can('vehicle:list') ? masterApi.vehicles() : Promise.resolve({ records: [] })
    ]);
    customers.value = customerPage.records || customerPage || [];
    products.value = productPage.records || productPage || [];
    drivers.value = driverPage.records || driverPage || [];
    vehicles.value = vehiclePage.records || vehiclePage || [];
  } catch (cause) { error.value = cause.message || '基础资料加载失败'; }
}
function resetForm(row = null) {
  Object.assign(form, { businessDate: row?.businessDate || new Date().toISOString().slice(0, 10), customerId: row?.customerId || '', productId: row?.productId || '', direction: row?.direction || 'OUTBOUND', loadingPlace: row?.loadingPlace || '', unloadingPlace: row?.unloadingPlace || '', taxUnitPrice: row?.taxUnitPrice || '', assignedDriverId: row?.assignedDriverId || '', externalRouteNo: row?.externalRouteNo || '', tripSequence: row?.tripSequence || '' });
  editing.value = true;
}
async function save() {
  saving.value = true; error.value = '';
  try {
    const body = { ...form, customerId: Number(form.customerId), productId: Number(form.productId), assignedDriverId: form.assignedDriverId ? Number(form.assignedDriverId) : null, externalRouteNo: form.externalRouteNo || null, tripSequence: form.tripSequence || null };
    if (selected.value?.id) await routeApi.update(selected.value.id, body);
    else await routeApi.create(body);
    editing.value = false;
    await load();
    if (selected.value) await detail(selected.value);
  } catch (cause) { error.value = cause.message || '线路保存失败'; }
  finally { saving.value = false; }
}
async function detail(row) {
  error.value = ''; detailTab.value = 'overview';
  try {
    selected.value = await routeApi.detail(row.id);
    const versions = await routeApi.weightVersions(row.id);
    weights.value = versions.records || versions || [];
    participants.value = await routeApi.participants(row.id);
  } catch (cause) { error.value = cause.message || '详情加载失败'; }
}
function openPublish(row) {
  publishing.value = row;
  Object.assign(publishForm, { initialSalary: '', outsourcedPlateNo: '' });
}
async function submitPublish() {
  if (!publishing.value || saving.value) return;
  saving.value = true; error.value = '';
  try {
    await routeApi.action(publishing.value.id, 'publish', {
      initialSalary: publishForm.initialSalary, outsourcedPlateNo: publishForm.outsourcedPlateNo || null
    });
    publishing.value = null;
    await load();
  } catch (cause) { error.value = cause.message || '发布失败'; }
  finally { saving.value = false; }
}
async function openTransfer(row) {
  error.value = '';
  try {
    const [route, history] = await Promise.all([routeApi.detail(row.id), routeApi.participants(row.id)]);
    transferring.value = route;
    participants.value = history;
    salaryDecisions.value = history.filter((item) => !['CANCELLED', 'LEGACY_ENDED'].includes(item.status))
      .map((item) => ({ participantId: item.id, amount: item.salaryAmount == null ? '' : String(item.salaryAmount) }));
    Object.assign(transferForm, { newDriverId: '', outsourcedPlateNo: '', newSalary: '', reason: '' });
  } catch (cause) { error.value = cause.message || '转派资料加载失败'; }
}
async function submitTransfer() {
  if (!transferring.value || saving.value) return;
  saving.value = true; error.value = '';
  try {
    await routeApi.transfer(transferring.value.id, {
      expectedCurrentParticipantId: transferring.value.currentParticipantId,
      expectedRouteVersion: transferring.value.version,
      newDriverId: Number(transferForm.newDriverId),
      outsourcedPlateNo: transferForm.outsourcedPlateNo || null,
      newSalary: transferForm.newSalary,
      reason: transferForm.reason,
      salaries: salaryDecisions.value.map((item) => ({ participantId: item.participantId, amount: item.amount }))
    });
    transferring.value = null;
    await load();
    if (selected.value) await detail(selected.value);
  } catch (cause) { error.value = cause.message || '转派失败；请刷新线路版本后重试'; }
  finally { saving.value = false; }
}
function openSalary(row, participant) {
  salaryTarget.value = { route: row, participant };
  Object.assign(salaryForm, { amount: participant.salaryAmount == null ? '' : String(participant.salaryAmount), reason: '' });
}
async function submitSalary() {
  if (!salaryTarget.value || saving.value) return;
  saving.value = true; error.value = '';
  try {
    const { route, participant } = salaryTarget.value;
    await routeApi.adjustParticipantSalary(route.id, participant.id, {
      amount: salaryForm.amount, expectedParticipantVersion: participant.version, reason: salaryForm.reason
    });
    salaryTarget.value = null;
    await detail(route);
    await load();
  } catch (cause) { error.value = cause.message || '工资调整失败；请刷新参与记录'; }
  finally { saving.value = false; }
}
function ask(row, action, label, danger = false, body = {}) {
  confirming.value = { row, action, label, danger, body };
}
async function confirmAction() {
  if (!confirming.value) return;
  saving.value = true; error.value = '';
  const { row, action, body } = confirming.value;
  try {
    if (action === 'delete') await routeApi.delete(row.id);
    else await routeApi.action(row.id, action, body);
    confirming.value = null;
    if (selected.value?.id === row.id) selected.value = null;
    await load();
  } catch (cause) { error.value = cause.message || '操作失败'; }
  finally { saving.value = false; }
}
async function submitOperation() {
  if (!selected.value) return;
  saving.value = true; error.value = '';
  try {
    if (operation.value === 'unload') await routeApi.unload(selected.value.id, { ...unloadForm });
    else await routeApi.adjustWeight(selected.value.id, { grossWeight: adjustForm.grossWeight, tareWeight: adjustForm.tareWeight, reason: adjustForm.reason, attachmentIds: adjustForm.attachmentIds ? adjustForm.attachmentIds.split(',').map((id) => Number(id.trim())) : [] });
    operation.value = '';
    await detail(selected.value);
    await load();
  } catch (cause) { error.value = cause.message || '操作失败'; }
  finally { saving.value = false; }
}
onMounted(async () => { await Promise.all([load(), loadReferences()]); });
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="线路管理" description="查看运输进度、重量版本，并处理线路操作。">
      <template #actions><button type="button" @click="navigate('imports')">导入线路</button><button v-if="can('route:create')" type="button" class="primary" @click="selected = null; resetForm()">＋ 新建线路</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load" />
    <FilterBar>
      <label>业务日期<input v-model="filters.date" type="date" @change="load(1)" /></label>
      <label>状态<select v-model="filters.status" @change="load(1)"><option value="">全部状态</option><option value="UNPUBLISHED">未发布</option><option value="PUBLISHED">等待发车</option><option value="TRANSFER_PENDING">等待接手</option><option value="IN_TRANSIT">运输中</option><option value="COMPLETED">已完成</option><option value="CANCELLED">已取消</option><option value="VOIDED">已作废</option></select></label>
      <label>客户<select v-model="filters.customerId" @change="load(1)"><option value="">全部客户</option><option v-for="item in customers" :key="item.id" :value="String(item.id)">{{ item.customerName }}</option></select></label>
      <label v-if="can('driver:list')">司机<select v-model="filters.driverId" @change="load(1)"><option value="">全部司机</option><option v-for="item in drivers" :key="item.id" :value="String(item.id)">{{ item.name }}</option></select></label>
      <label class="ui-search">线路号或地点<input v-model.trim="filters.keyword" placeholder="请输入线路号或地点" @keydown.enter="load(1)" /></label><button type="button" @click="load(1)">查询</button>
    </FilterBar>
    <DataTable :columns="columns" :rows="rows" :loading="loading" empty-text="当前条件下没有线路">
      <template #customerId="{ value }">{{ customerName(value) }}</template><template #assignedDriverId="{ value }">{{ driverName(value) }}</template><template #departureVehicleId="{ value }">{{ vehicleName(value) }}</template>
      <template #status="{ value }"><StatusTag :value="value" /></template><template #netWeight="{ value }">{{ value == null ? '—' : `${value} 吨` }}</template>
      <template #actions="{ row }"><div class="ui-inline-actions"><button type="button" @click="detail(row)">详情</button><button v-if="row.status === 'UNPUBLISHED' && can('route:publish')" type="button" @click="openPublish(row)">发布</button><button v-if="['PUBLISHED', 'TRANSFER_PENDING'].includes(row.status) && can('route:depart')" type="button" @click="ask(row, 'depart', '发车')">发车</button><button v-if="['IN_TRANSIT', 'TRANSFER_PENDING'].includes(row.status) && can('route:transfer')" type="button" @click="openTransfer(row)">转派</button><details class="ui-more-menu"><summary>更多</summary><div><button v-if="row.status === 'PUBLISHED' && can('route:cancel')" type="button" @click="ask(row, 'cancel', '取消', true, { reason: '管理端取消' })">取消线路</button><button v-if="row.status === 'COMPLETED' && can('route:void')" type="button" @click="ask(row, 'void', '作废', true, { reason: '管理端作废' })">作废线路</button><button v-if="row.status === 'VOIDED' && can('route:reactivate')" type="button" @click="ask(row, 'reactivate', '重新启用')">重新启用</button><button v-if="can('route:delete') && ['UNPUBLISHED', 'PUBLISHED', 'CANCELLED'].includes(row.status)" type="button" @click="ask(row, 'delete', '删除', true)">删除线路</button></div></details></div></template>
    </DataTable>
    <PaginationBar :page-no="pageNo" :page-size="pageSize" :total="total" @change="load" />
    <FormDialog :open="editing" :title="selected?.id ? '编辑线路' : '新建线路'" :busy="saving" @close="editing = false">
      <form @submit.prevent="save"><div class="ui-form-grid">
        <label class="ui-form-field">业务日期<input v-model="form.businessDate" type="date" required /></label>
        <label class="ui-form-field">客户<select v-model="form.customerId" required><option value="">请选择客户</option><option v-for="item in customers" :key="item.id" :value="item.id">{{ item.customerName }}</option></select></label>
        <label class="ui-form-field">产品<select v-model="form.productId" required><option value="">请选择产品</option><option v-for="item in products" :key="item.id" :value="item.id">{{ item.productName }}</option></select></label>
        <label class="ui-form-field">方向<select v-model="form.direction"><option value="OUTBOUND">去程</option><option value="RETURN">返程</option></select></label>
        <label class="ui-form-field">装货地<input v-model.trim="form.loadingPlace" required placeholder="请输入装货地" /></label>
        <label class="ui-form-field">卸货地<input v-model.trim="form.unloadingPlace" required placeholder="请输入卸货地" /></label>
        <label class="ui-form-field">含税单价<input v-model="form.taxUnitPrice" type="number" min="0" step="0.01" required /></label>
        <label class="ui-form-field">指定司机<select v-model="form.assignedDriverId"><option value="">暂不指定</option><option v-for="item in drivers" :key="item.id" :value="item.id">{{ item.name }} · {{ item.phone }}</option></select></label>
        <label class="ui-form-field">外部线路号<input v-model.trim="form.externalRouteNo" placeholder="可选" /></label>
        <label class="ui-form-field">趟次<input v-model.trim="form.tripSequence" placeholder="可选" /></label>
      </div><div class="ui-dialog-actions"><button type="button" @click="editing = false">取消</button><button class="primary" :disabled="saving">{{ saving ? '正在保存…' : '保存线路' }}</button></div></form>
    </FormDialog>
    <FormDialog :open="!!publishing" title="发布线路" :busy="saving" @close="publishing = null">
      <form @submit.prevent="submitPublish"><div class="ui-form-grid">
        <label class="ui-form-field">初始线路工资（元）<input v-model="publishForm.initialSalary" type="number" min="0" step="0.01" required /></label>
        <label v-if="selectedDriverType(publishing?.assignedDriverId) === 'OUTSOURCED'" class="ui-form-field">真实外协车牌<input v-model.trim="publishForm.outsourcedPlateNo" required /></label>
      </div><p>司机类型由后端司机资料确认；外协司机不使用公司车辆绑定。</p>
      <div class="ui-dialog-actions"><button type="button" @click="publishing = null">取消</button><button class="primary" :disabled="saving">确认发布</button></div></form>
    </FormDialog>
    <FormDialog :open="!!transferring" title="线路转派与全员工资核定" :busy="saving" @close="transferring = null">
      <form @submit.prevent="submitTransfer"><div class="ui-form-grid">
        <label v-for="decision in salaryDecisions" :key="decision.participantId" class="ui-form-field">
          参与 #{{ participants.find((item) => item.id === decision.participantId)?.sequenceNo }} · {{ driverName(participants.find((item) => item.id === decision.participantId)?.driverId) }} 工资（元）
          <input v-model="decision.amount" type="number" min="0" step="0.01" required />
        </label>
        <label class="ui-form-field">接手司机<select v-model="transferForm.newDriverId" required><option value="">请选择司机</option><option v-for="driver in drivers.filter((item) => item.status === 1)" :key="driver.id" :value="driver.id">{{ driver.name }} · {{ driver.driverType === 'OUTSOURCED' ? '外协' : '自有' }}</option></select></label>
        <label v-if="selectedDriverType(transferForm.newDriverId) === 'INTERNAL'" class="ui-form-field">当前公司车辆<input :value="vehicleName(selectedDriverVehicle(transferForm.newDriverId))" readonly /></label>
        <label v-if="selectedDriverType(transferForm.newDriverId) === 'OUTSOURCED'" class="ui-form-field">真实外协车牌<input v-model.trim="transferForm.outsourcedPlateNo" required /></label>
        <label class="ui-form-field">新参与司机工资（元）<input v-model="transferForm.newSalary" type="number" min="0" step="0.01" required /></label>
        <label class="ui-form-field">转派原因<input v-model.trim="transferForm.reason" required maxlength="500" /></label>
      </div><p>转派后线路等待接手司机重新发车；历史参与及工资调整永久保留。</p>
      <div class="ui-dialog-actions"><button type="button" @click="transferring = null">取消</button><button class="primary" :disabled="saving">确认转派</button></div></form>
    </FormDialog>
    <FormDialog :open="!!salaryTarget" title="调整参与工资" :busy="saving" @close="salaryTarget = null">
      <form @submit.prevent="submitSalary"><div class="ui-form-grid">
        <label class="ui-form-field">工资金额（元）<input v-model="salaryForm.amount" type="number" min="0" step="0.01" required /></label>
        <label class="ui-form-field">调整原因<input v-model.trim="salaryForm.reason" required maxlength="500" /></label>
      </div><div class="ui-dialog-actions"><button type="button" @click="salaryTarget = null">取消</button><button class="primary" :disabled="saving">确认调整</button></div></form>
    </FormDialog>
    <DetailDrawer :open="!!selected && !editing" title="线路详情" @close="selected = null; operation = ''">
      <template #subtitle>{{ selected?.routeNo }}</template>
      <div class="ui-subtabs"><button :class="{active: detailTab === 'overview'}" @click="detailTab = 'overview'">概况</button><button :class="{active: detailTab === 'participants'}" @click="detailTab = 'participants'">参与历史</button><button :class="{active: detailTab === 'weights'}" @click="detailTab = 'weights'">重量版本</button><button :class="{active: detailTab === 'actions'}" @click="detailTab = 'actions'">线路操作</button></div>
      <template v-if="selected && detailTab === 'overview'"><dl class="ui-detail-list"><div><dt>状态</dt><dd><StatusTag :value="selected.status" /></dd></div><div><dt>业务日期</dt><dd>{{ selected.businessDate }}</dd></div><div><dt>客户</dt><dd>{{ customerName(selected.customerId) }}</dd></div><div><dt>产品</dt><dd>{{ products.find((item) => item.id === selected.productId)?.productName || `产品 #${selected.productId}` }}</dd></div><div><dt>司机</dt><dd>{{ driverName(selected.assignedDriverId) }}</dd></div><div><dt>发车车辆</dt><dd>{{ vehicleName(selected.departureVehicleId) }}</dd></div><div><dt>起讫地点</dt><dd>{{ selected.loadingPlace }} → {{ selected.unloadingPlace }}</dd></div><div><dt>净重</dt><dd>{{ selected.netWeight ?? '未称重' }}</dd></div><div><dt>发车时间</dt><dd>{{ selected.departureTime || '—' }}</dd></div><div><dt>卸货时间</dt><dd>{{ selected.unloadTime || '—' }}</dd></div></dl></template>
      <div v-if="detailTab === 'participants'">
        <DataTable :columns="[{key:'sequenceNo',label:'顺序'},{key:'driverName',label:'司机'},{key:'driverType',label:'类型'},{key:'companyVehicleId',label:'公司车辆'},{key:'outsourcedPlateNo',label:'外协车牌'},{key:'status',label:'状态'},{key:'salaryAmount',label:'工资',numeric:true},{key:'actions',label:'操作'}]" :rows="participants" empty-text="暂无参与记录">
          <template #driverType="{ value }">{{ value === 'OUTSOURCED' ? '外协' : value === 'INTERNAL' ? '自有' : '历史未确认' }}</template>
          <template #companyVehicleId="{ value }">{{ value ? vehicleName(value) : '—' }}</template>
          <template #outsourcedPlateNo="{ value }">{{ value || '—' }}</template>
          <template #status="{ value }"><StatusTag :value="value" /></template>
          <template #salaryAmount="{ value }">{{ value == null ? '未授权或资料不完整' : `${value} 元` }}</template>
          <template #actions="{ row }"><button v-if="can('route:salary:adjust') && !['CANCELLED', 'LEGACY_ENDED'].includes(row.status)" type="button" @click="openSalary(selected, row)">调整工资</button></template>
        </DataTable>
      </div>
      <DataTable v-if="detailTab === 'weights'" :columns="[{key:'versionNo',label:'版本'},{key:'grossWeight',label:'毛重',numeric:true},{key:'tareWeight',label:'皮重',numeric:true},{key:'netWeight',label:'净重',numeric:true},{key:'loadStandardMet',label:'达标'},{key:'reason',label:'原因'}]" :rows="weights" empty-text="尚无重量版本"><template #loadStandardMet="{value}">{{ value === true ? '达标' : value === false ? '未达标' : '—' }}</template></DataTable>
      <div v-if="selected && detailTab === 'actions'" class="ui-operation-panel"><p>操作历史：当前接口只提供重量版本与线路当前状态，暂无独立状态变更历史接口。</p></div>
      <form v-if="operation" id="route-operation-form" class="ui-operation-form" @submit.prevent="submitOperation"><h3>{{ operation === 'unload' ? '录入卸货重量' : '重量纠错' }}</h3><template v-if="operation === 'unload'"><label>毛重<input v-model="unloadForm.grossWeight" type="number" min="0" step="0.001" required /></label><label>皮重<input v-model="unloadForm.tareWeight" type="number" min="0" step="0.001" required /></label></template><template v-else><label>毛重<input v-model="adjustForm.grossWeight" type="number" min="0" step="0.001" required /></label><label>皮重<input v-model="adjustForm.tareWeight" type="number" min="0" step="0.001" required /></label><label>纠错原因<input v-model.trim="adjustForm.reason" required /></label><label>证据附件 ID（用逗号分隔）<input v-model.trim="adjustForm.attachmentIds" /></label></template></form>
      <template #footer>
        <button v-if="operation" type="button" @click="operation = ''">取消操作</button>
        <button v-if="operation" class="primary" type="submit" form="route-operation-form" :disabled="saving">{{ saving ? '正在提交…' : '确认提交' }}</button>
        <button v-if="!operation && selected && ['UNPUBLISHED', 'PUBLISHED'].includes(selected.status) && can('route:edit')" type="button" @click="resetForm(selected)">编辑线路</button>
        <button v-if="!operation && selected?.status === 'IN_TRANSIT' && can('route:unload')" type="button" @click="detailTab = 'actions'; operation = 'unload'">录入卸货重量</button>
        <button v-if="!operation && selected && ['IN_TRANSIT', 'TRANSFER_PENDING'].includes(selected.status) && can('route:transfer')" type="button" @click="openTransfer(selected)">转派线路</button>
        <button v-if="!operation && selected && can('route:weight:adjust')" type="button" @click="detailTab = 'actions'; operation = 'adjust'">重量纠错</button>
      </template>
    </DetailDrawer>
    <ConfirmDialog :open="!!confirming" :title="`${confirming?.label || ''}线路`" :message="`确认${confirming?.label || ''}线路 ${confirming?.row?.routeNo || ''}？${confirming?.danger ? '此操作可能影响后续运输与结算。' : ''}`" :danger="confirming?.danger" :busy="saving" @confirm="confirmAction" @cancel="confirming = null" />
  </div>
</template>
