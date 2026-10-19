<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { masterApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from './PageHeader.vue';
import FilterBar from './FilterBar.vue';
import DataTable from './DataTable.vue';
import StatusTag from './StatusTag.vue';
import EntityPicker from './EntityPicker.vue';
import PaginationBar from './PaginationBar.vue';
import FormDialog from './FormDialog.vue';
import DetailDrawer from './DetailDrawer.vue';
import ConfirmDialog from './ConfirmDialog.vue';
import ErrorState from './ErrorState.vue';

const tab = ref('driver');
const pageNo = ref(1);
const pageSize = 20;
const total = ref(0);
const rows = ref([]);
const drivers = ref([]);
const vehicles = ref([]);
const trailers = ref([]);
const keyword = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const target = ref(null);
const action = ref('');
const confirmTarget = ref(null);
const changeTarget = ref(null);
const replacePending = ref(null);
const historyTarget = ref(null);
const history = ref([]);
const historyLoading = ref(false);
const binding = reactive({ vehicleId: '', trailerId: '', reason: '' });
const visibleRows = computed(() => rows.value.filter((row) => {
  const name = tab.value === 'driver' ? `${row.name} ${row.phone}` : row.plateNo;
  return name.toLowerCase().includes(keyword.value.trim().toLowerCase());
}));
const vehicleLabel = (id, name = '') => id ? name || vehicles.value.find((item) => item.id === id)?.plateNo || '车牌未加载' : '未绑定';
const trailerLabel = (id, name = '') => id ? name || trailers.value.find((item) => item.id === id)?.plateNo || '车挂号未加载' : '未绑定';
const driverLabel = (id) => id ? drivers.value.find((item) => item.id === id)?.name || '司机姓名未加载' : '未绑定';
const displayName = (row) => tab.value === 'driver' ? `${row.name} · ${row.phone}` : row.plateNo;

async function load(page = 1) {
  loading.value = true; error.value = '';
  try {
    const [result, driverResult, vehicleResult, trailerResult] = await Promise.all([
      tab.value === 'driver' ? masterApi.bindingDrivers(page, pageSize) : masterApi.bindingVehicles(page, pageSize),
      masterApi.bindingDrivers(1),
      masterApi.bindingVehicles(1),
      masterApi.bindingTrailers(1)
    ]);
    rows.value = result.records || [];
    total.value = Number(result.total || 0);
    pageNo.value = page;
    drivers.value = driverResult.records || [];
    vehicles.value = vehicleResult.records || [];
    trailers.value = trailerResult.records || [];
  } catch (cause) { rows.value = []; total.value = 0; error.value = cause.message || '绑定列表加载失败'; }
  finally { loading.value = false; }
}
function changeTab(value) { tab.value = value; keyword.value = ''; closeAction(); load(1); }
function closeAction() { target.value = null; action.value = ''; binding.vehicleId = ''; binding.trailerId = ''; binding.reason = ''; }
function openBind(row) { if (tab.value === 'driver' && row.driverType !== 'INTERNAL') return; target.value = row; action.value = 'bind'; binding.vehicleId = ''; binding.trailerId = ''; error.value = ''; }
function openChange(row) { if (tab.value === 'driver' && row.driverType !== 'INTERNAL') return; changeTarget.value = row; error.value = ''; }
function proceedChange() {
  if (!changeTarget.value) return;
  replacePending.value = { id: changeTarget.value.id, tab: tab.value };
  confirmTarget.value = changeTarget.value;
  changeTarget.value = null;
}
function openUnbind(row) { confirmTarget.value = row; error.value = ''; }
async function saveBinding() {
  if (!target.value) return;
  busy.value = true; error.value = '';
  try {
    if (tab.value === 'driver') await masterApi.bindDriverVehicle(target.value.id, { vehicleId: Number(binding.vehicleId) });
    else await masterApi.bindTrailerVehicle(Number(binding.trailerId), { vehicleId: target.value.id });
    closeAction(); await load(pageNo.value);
  } catch (cause) { error.value = cause.message || '绑定失败；请核对当前车辆及运输状态'; }
  finally { busy.value = false; }
}
async function confirmUnbind() {
  if (!confirmTarget.value) return;
  const previousId = confirmTarget.value.id;
  busy.value = true; error.value = '';
  try {
    if (tab.value === 'driver') await masterApi.unbindDriverVehicle(confirmTarget.value.id, { reason: binding.reason || null });
    else await masterApi.unbindTrailerVehicle(confirmTarget.value.currentTrailerId, { reason: binding.reason || null });
    confirmTarget.value = null; binding.reason = ''; await load(pageNo.value);
    if (replacePending.value?.id === previousId && replacePending.value.tab === tab.value) {
      const refreshed = rows.value.find((row) => row.id === previousId);
      if (refreshed && !error.value) openBind(refreshed);
      else error.value = '旧绑定已解除，请重新查找该对象并绑定新对象。';
    }
    replacePending.value = null;
  } catch (cause) { replacePending.value = null; error.value = cause.message || '解绑失败；当前运输可能限制换绑'; }
  finally { busy.value = false; }
}
async function openHistory(row) {
  historyTarget.value = row; historyLoading.value = true; history.value = []; error.value = '';
  try {
    history.value = tab.value === 'driver'
      ? await masterApi.driverHistory(row.id)
      : await masterApi.trailerHistory(row.id);
  } catch (cause) { error.value = cause.message || '绑定历史加载失败'; }
  finally { historyLoading.value = false; }
}
onMounted(() => load(1));
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="绑定关系" description="更换绑定需先解绑，再选择新对象；运输中的绑定限制由系统校验。"><template #actions><button type="button" @click="load(pageNo)">刷新</button></template></PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load(pageNo)" />
    <div class="ui-subtabs"><button type="button" :class="{ active: tab === 'driver' }" @click="changeTab('driver')">司机车辆绑定</button><button type="button" :class="{ active: tab === 'trailer' }" @click="changeTab('trailer')">车辆车挂绑定</button></div>
    <div class="binding-overview" role="status"><strong>{{ tab === 'driver' ? '司机与公司车辆' : '公司车辆与车挂' }}</strong><span>当前页 {{ rows.length }} 条，其中已绑定 {{ rows.filter((row) => tab === 'driver' ? !!row.currentVehicleId : !!row.currentTrailerId).length }} 条</span><small>更换时先解绑再绑定；每次操作均由服务端校验并保留历史。</small></div>
    <FilterBar><label class="ui-search">{{ tab === 'driver' ? '司机姓名或手机号' : '车牌号' }}<input v-model.trim="keyword" type="search" placeholder="搜索当前页" /></label><span class="muted">筛选当前页；使用分页查看其他记录</span></FilterBar>
    <DataTable v-if="!error && tab === 'driver'" :columns="[{key:'name',label:'司机'},{key:'phone',label:'手机号'},{key:'driverType',label:'类型'},{key:'currentVehicleId',label:'当前车辆'},{key:'status',label:'状态'},{key:'actions',label:'操作'}]" :rows="visibleRows" :loading="loading" :empty-text="keyword ? '当前页没有匹配司机' : '暂无司机'">
      <template #driverType="{value}">{{ value === 'INTERNAL' ? '自有司机' : '外协司机' }}</template>
      <template #currentVehicleId="{value,row}">{{ vehicleLabel(value, row.currentVehiclePlateNo) }}</template>
      <template #status="{value}"><StatusTag :value="value === 1 ? '启用' : '停用'" :tone="value === 1 ? 'success' : 'info'" /></template>
      <template #actions="{row}"><div class="ui-inline-actions"><button v-if="row.driverType === 'INTERNAL' && !row.currentVehicleId && can('BUTTON_BINDING_BIND_DRIVER')" type="button" @click="openBind(row)">绑定车辆</button><button v-if="row.driverType === 'INTERNAL' && row.currentVehicleId && can('BUTTON_BINDING_BIND_DRIVER') && can('BUTTON_BINDING_UNBIND_DRIVER')" type="button" @click="openChange(row)">更换</button><button v-if="row.driverType === 'INTERNAL' && row.currentVehicleId && can('BUTTON_BINDING_UNBIND_DRIVER')" type="button" class="ui-danger-link" @click="openUnbind(row)">解绑</button><button v-if="can('PAGE_BINDING')" type="button" @click="openHistory(row)">历史</button></div></template>
    </DataTable>
    <DataTable v-else-if="!error" :columns="[{key:'plateNo',label:'车辆'},{key:'currentTrailerId',label:'当前车挂'},{key:'status',label:'状态'},{key:'actions',label:'操作'}]" :rows="visibleRows" :loading="loading" :empty-text="keyword ? '当前页没有匹配车辆' : '暂无车辆'">
      <template #currentTrailerId="{value,row}">{{ trailerLabel(value, row.currentTrailerPlateNo) }}</template>
      <template #status="{value}"><StatusTag :value="value === 1 ? '启用' : '停用'" :tone="value === 1 ? 'success' : 'info'" /></template>
      <template #actions="{row}"><div class="ui-inline-actions"><button v-if="!row.currentTrailerId && can('BUTTON_BINDING_BIND_TRAILER')" type="button" @click="openBind(row)">绑定车挂</button><button v-if="row.currentTrailerId && can('BUTTON_BINDING_BIND_TRAILER') && can('BUTTON_BINDING_UNBIND_TRAILER')" type="button" @click="openChange(row)">更换</button><button v-if="row.currentTrailerId && can('BUTTON_BINDING_UNBIND_TRAILER')" type="button" class="ui-danger-link" @click="openUnbind(row)">解绑</button><button v-if="can('PAGE_BINDING')" type="button" @click="openHistory(row)">历史</button></div></template>
    </DataTable>
    <PaginationBar v-if="!error" :page-no="pageNo" :page-size="pageSize" :total="total" @change="load" />
    <FormDialog :open="!!changeTarget" title="更换绑定" @close="changeTarget = null"><div v-if="changeTarget"><p>{{ displayName(changeTarget) }} 当前绑定 {{ tab === 'driver' ? vehicleLabel(changeTarget.currentVehicleId, changeTarget.currentVehiclePlateNo) : trailerLabel(changeTarget.currentTrailerId, changeTarget.currentTrailerPlateNo) }}。</p><p class="muted">系统按“先解绑、再绑定”执行。解绑立即生效；如果后续绑定失败，该对象会保持未绑定。运输中的绑定由后端拒绝解除。</p><div class="ui-dialog-actions"><button type="button" @click="changeTarget = null">取消</button><button class="ui-danger-button" type="button" @click="proceedChange">继续：确认解绑</button></div></div></FormDialog>
    <FormDialog :open="action === 'bind'" :title="tab === 'driver' ? '绑定司机车辆' : '绑定车辆车挂'" :busy="busy" @close="closeAction">
      <form v-if="target" @submit.prevent="saveBinding"><p>当前对象：<strong>{{ displayName(target) }}</strong></p><p class="muted">当前绑定：{{ tab === 'driver' ? vehicleLabel(target.currentVehicleId, target.currentVehiclePlateNo) : trailerLabel(target.currentTrailerId, target.currentTrailerPlateNo) }}</p>
        <label v-if="tab === 'driver'" class="ui-form-field">选择车辆<EntityPicker v-model="binding.vehicleId" :load-page="masterApi.bindingVehicles" :label-of="(item) => item.plateNo" placeholder="请选择车辆" required /></label>
        <label v-else class="ui-form-field">选择车挂<EntityPicker v-model="binding.trailerId" :load-page="masterApi.bindingTrailers" :label-of="(item) => item.plateNo" placeholder="请选择车挂" required /></label>
        <div class="ui-dialog-actions"><button type="button" @click="closeAction">取消</button><button class="primary" :disabled="busy">{{ busy ? '正在提交…' : '确认绑定' }}</button></div>
      </form>
    </FormDialog>
    <ConfirmDialog :open="!!confirmTarget" title="确认解绑" :message="confirmTarget ? `确认将${displayName(confirmTarget)}与${tab === 'driver' ? vehicleLabel(confirmTarget.currentVehicleId, confirmTarget.currentVehiclePlateNo) : trailerLabel(confirmTarget.currentTrailerId, confirmTarget.currentTrailerPlateNo)}解绑？当前运输中的绑定由后端拒绝，历史会保留。${replacePending ? '解绑立即生效，后续绑定若失败将保持未绑定。' : ''}` : ''" danger :busy="busy" @cancel="confirmTarget = null; replacePending = null" @confirm="confirmUnbind" />
    <DetailDrawer :open="!!historyTarget" :title="'绑定历史'" @close="historyTarget = null"><p v-if="historyTarget">{{ displayName(historyTarget) }}</p><p v-if="historyLoading">正在加载历史…</p><p v-else-if="!history.length" class="muted">暂无绑定历史</p><ol v-else class="ui-timeline"><li v-for="item in history" :key="item.id"><strong>{{ item.targetName || (tab === 'driver' ? driverLabel(item.targetId) : trailerLabel(item.targetId)) }} · {{ item.subjectName || vehicleLabel(item.subjectId) }}</strong><p>绑定 {{ item.bindTime }} · {{ item.bindByPhone || '操作人信息未提供' }}</p><p v-if="item.unbindTime">解绑 {{ item.unbindTime }} · {{ item.unbindByPhone || '操作人信息未提供' }}<span v-if="item.unbindReason"> · {{ item.unbindReason }}</span></p><p v-else>当前有效</p></li></ol></DetailDrawer>
  </div>
</template>

<style scoped>
.binding-overview { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 14px; padding: 8px 12px; background: var(--ui-surface-muted, #f5faf8); border: 1px solid var(--ui-border); border-radius: 8px; margin: 8px 0 10px; }
.binding-overview strong { color: var(--ui-primary, #087563); }
.binding-overview small { flex:1 1 260px; color: var(--ui-muted); text-align:right; }
@media (max-width:900px) { .binding-overview small { flex-basis:100%; text-align:left; } }
</style>
