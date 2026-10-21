<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { tireApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import StatusTag from '../components/StatusTag.vue';
import ErrorState from '../components/ErrorState.vue';
import FormDialog from '../components/FormDialog.vue';
import { groupTireMaintenanceHistory } from './tireMaintenanceHistory.js';

const filters = reactive({ tireNo: '', status: '' });
const rows = ref([]);
const page = ref(1); const pageSize = ref(20);
const total = ref(0);
const inventoryTotals = ref(null);
const selected = ref(null);
const detailTab = ref('profile');
const maintenanceHistory = computed(() => groupTireMaintenanceHistory(selected.value?.history || []));
const editing = ref(false);
const form = reactive({ tireNo: '', barcode: '', materialDescription: '', arrivedAt: '', reason: '', expectedVersion: 0 });
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const statusName = { AVAILABLE: '可用', CLAIMED: '已申领' };
const fieldName = { tire_no: '胎号', barcode: '条码', material_description: '物料描述', arrived_at: '到货时间', status: '状态' };
const dateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '—';
const historyTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '—';
const historyValue = (field, value) => value == null || value === 'null' || value === ''
  ? '未填写' : field === 'status' ? (statusName[value] || value)
    : field === 'arrived_at' ? dateTime(value) : value;
const approvalActionName = {
  SUBMIT: '发起申请', RESUBMIT: '修改并重新提交', APPROVE: '同意',
  RETURN_APPLICANT: '打回申请人', RETURN_STEP: '打回审批节点',
  REJECT: '拒绝', WITHDRAW: '撤回', PENDING: '待处理'
};

async function load(next = 1) {
  if (!can('PAGE_TIRES')) return;
  loading.value = true; error.value = '';
  try {
    const [result, available, claimed] = await Promise.all([
      tireApi.list(next, pageSize.value, filters),
      tireApi.list(1, 1, { status: 'AVAILABLE' }),
      tireApi.list(1, 1, { status: 'CLAIMED' })
    ]);
    rows.value = result.records || [];
    total.value = Number(result.total || 0);
    inventoryTotals.value = {
      available: Number(available.total || 0),
      claimed: Number(claimed.total || 0)
    };
    page.value = next;
  } catch (cause) { error.value = cause.message || '轮胎库存加载失败'; rows.value = []; inventoryTotals.value = null; }
  finally { loading.value = false; }
}
async function open(row) {
  busy.value = true; error.value = ''; selected.value = null; detailTab.value = 'profile';
  try { selected.value = await tireApi.detail(row.id); }
  catch (cause) { error.value = cause.message || '轮胎详情加载失败'; }
  finally { busy.value = false; }
}
async function openForEdit(row) {
  if (busy.value || !can('BUTTON_TIRES_MANAGE') || row.status !== 'AVAILABLE') return;
  await open(row);
  if (selected.value?.id === row.id) beginEdit();
}
function beginEdit() {
  if (!selected.value || selected.value.status !== 'AVAILABLE') return;
  Object.assign(form, { tireNo: selected.value.tireNo, barcode: selected.value.barcode || '',
    materialDescription: selected.value.materialDescription,
    arrivedAt: dateTime(selected.value.arrivedAt).replace(' ', 'T'),
    expectedVersion: selected.value.version, reason: '' });
  editing.value = true;
}
async function save() {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try {
    const result = await tireApi.edit(selected.value.id, { ...form, arrivedAt: form.arrivedAt + ':00' });
    selected.value = result; editing.value = false; await load(page.value);
  } catch (cause) { error.value = cause.message || '轮胎资料保存失败'; }
  finally { busy.value = false; }
}
onMounted(() => load());
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="轮胎库存" description="入库轮胎与当前申领状态；换胎申请只由司机在小程序发起。" />
    <ErrorState v-if="error" :message="error" @retry="load(page)" />
    <div v-if="can('PAGE_TIRES')">
      <div class="tire-overview" role="status"><span>库存总数 <strong>{{ inventoryTotals ? inventoryTotals.available + inventoryTotals.claimed : '—' }}</strong></span><span>可用总数 <strong>{{ inventoryTotals?.available ?? '—' }}</strong></span><span>已申领总数 <strong>{{ inventoryTotals?.claimed ?? '—' }}</strong></span></div>
      <FilterBar>
        <label class="ui-search">胎号<input v-model.trim="filters.tireNo" type="search" placeholder="搜索胎号" @keyup.enter="load(1)" /></label>
        <label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="AVAILABLE">可用</option><option value="CLAIMED">已申领</option></select></label>
        <button type="button" @click="load(1)">查询</button>
        <button type="button" @click="filters.tireNo='';filters.status='';load(1)">清除</button>
      </FilterBar>
      <DataTable v-if="!error" class="ui-paged-table" :columns="[{key:'tireNo',label:'胎号'},{key:'barcode',label:'条码'},
        {key:'materialDescription',label:'物料描述'},{key:'arrivedAt',label:'到货时间'},
        {key:'status',label:'状态'},{key:'applicantName',label:'申请人'},
        {key:'currentVehiclePlate',label:'目前所在车辆'},{key:'actions',label:'操作'}]"
        :rows="rows" :loading="loading" empty-text="暂无符合条件的轮胎">
        <template #arrivedAt="{value}">{{ dateTime(value) }}</template>
        <template #status="{value}"><StatusTag :value="statusName[value] || '状态异常'" :tone="value === 'AVAILABLE' ? 'success' : 'info'" /></template>
        <template #applicantName="{value,row}">{{ row.status === 'CLAIMED' ? value || '—' : '—' }}</template>
        <template #currentVehiclePlate="{value,row}">{{ row.status === 'CLAIMED' ? value || '—' : '—' }}</template>
        <template #actions="{row}">
          <button type="button" class="ui-detail-button" :disabled="busy" @click="open(row)">查看详情</button>
          <button v-if="can('BUTTON_TIRES_MANAGE') && row.status === 'AVAILABLE'" type="button"
            :disabled="busy" @click="openForEdit(row)">人工维护</button>
        </template>
      </DataTable>
      <PaginationBar v-if="!error" :page-no="page" :page-size="pageSize" :total="total" @change="load" @change-size="(size) => { pageSize = size; load(1); }" />
    </div>
    <p v-else class="ui-section-card">当前账号没有轮胎库存查看权限。</p>
    <DetailDrawer :open="!!selected" title="轮胎详情" @close="selected=null">
      <template #subtitle>{{ selected?.tireNo }}</template>
      <template v-if="selected">
        <div class="tire-detail-tabs" role="tablist" aria-label="轮胎详情页签">
          <button type="button" role="tab" :aria-selected="detailTab === 'profile'" :class="{ active: detailTab === 'profile' }" @click="detailTab = 'profile'">轮胎资料</button>
          <button type="button" role="tab" :aria-selected="detailTab === 'approval'" :class="{ active: detailTab === 'approval' }" @click="detailTab = 'approval'">审批链</button>
        </div>
        <div v-if="detailTab === 'profile'">
        <div class="tire-maintenance-actions">
          <strong>轮胎资料维护</strong>
          <button v-if="can('BUTTON_TIRES_MANAGE') && selected.status === 'AVAILABLE'" type="button"
            :disabled="busy" @click="beginEdit">人工维护</button>
          <span v-else-if="selected.status !== 'AVAILABLE'" class="muted">已申领轮胎不能修改入库资料</span>
        </div>
        <dl class="ui-detail-grid">
          <dt>当前状态</dt><dd><StatusTag :value="statusName[selected.status] || '状态异常'" /></dd>
          <dt>胎号</dt><dd>{{ selected.tireNo }}</dd>
          <dt>条码</dt><dd>{{ selected.barcode || '未填写' }}</dd>
          <dt>物料描述</dt><dd>{{ selected.materialDescription }}</dd>
          <dt>到货时间</dt><dd>{{ dateTime(selected.arrivedAt) }}</dd>
          <dt>入库时间</dt><dd>{{ dateTime(selected.createdAt) }}</dd>
          <template v-if="selected.status === 'CLAIMED'"><dt>申请人</dt><dd>{{ selected.applicantName || '—' }}</dd>
            <dt>目前所在车辆</dt><dd>{{ selected.currentVehiclePlate || '—' }}</dd></template>
        </dl>
        <h3>维护历史</h3>
        <div class="tire-history-table-wrap">
          <table class="tire-history-table" aria-label="轮胎资料修改记录">
            <thead><tr><th scope="col">修改项目</th><th scope="col">修改内容</th><th scope="col">原因与操作</th></tr></thead>
            <tbody>
              <template v-for="(group, index) in maintenanceHistory" :key="index">
                <tr v-for="(change, changeIndex) in group.changes" :key="changeIndex" :class="{ 'tire-history-group-start': changeIndex === 0 && index > 0 }">
                  <th scope="row">{{ fieldName[change.fieldName] || '业务信息' }}</th>
                  <td>
                    <span class="tire-history-line"><b>修改前：</b>{{ historyValue(change.fieldName, change.beforeValue) }}</span>
                    <span class="tire-history-line"><b>修改后：</b>{{ historyValue(change.fieldName, change.afterValue) }}</span>
                  </td>
                  <td v-if="changeIndex === 0" :rowspan="group.changes.length">
                    <span class="tire-history-line"><b>原因：</b>{{ group.reason || '未填写' }}</span>
                    <span class="tire-history-line"><b>操作：</b>{{ group.operatorPhone || '未知账号' }}</span>
                    <span class="tire-history-line"><b>时间：</b>{{ historyTime(group.operatedAt) }}</span>
                  </td>
                </tr>
              </template>
              <tr v-if="!maintenanceHistory.length"><td colspan="3" class="tire-history-empty">暂无人工维护记录</td></tr>
            </tbody>
          </table>
        </div>
        </div>
        <div v-else class="tire-history-table-wrap">
          <table class="tire-history-table tire-approval-table" aria-label="轮胎审批链">
            <thead><tr><th scope="col">版本与节点</th><th scope="col">处理结果</th><th scope="col">操作人 / 角色</th><th scope="col">时间</th></tr></thead>
            <tbody>
              <tr v-for="(entry, index) in selected.approvalChain || []" :key="index">
                <td><strong>第 {{ entry.versionNo }} 次提交</strong><span class="tire-history-line">{{ entry.nodeName || '申请人' }}</span></td>
                <td><strong>{{ approvalActionName[entry.eventType] || entry.eventType }}</strong>
                  <span v-if="entry.targetNodeName" class="tire-history-line">目标：{{ entry.targetNodeName }}</span>
                  <span v-else-if="entry.eventType === 'RETURN_APPLICANT'" class="tire-history-line">目标：申请人</span>
                  <span v-if="entry.comment" class="tire-history-line">意见：{{ entry.comment }}</span></td>
                <td>{{ entry.operatorRole || '—' }}<span class="tire-history-line">{{ entry.operatorPhone || '—' }}</span></td>
                <td>{{ historyTime(entry.operatedAt) }}</td>
              </tr>
              <tr v-if="!selected.approvalChain?.length"><td colspan="4" class="tire-history-empty">暂无换胎审批记录</td></tr>
            </tbody>
          </table>
        </div>
      </template>
    </DetailDrawer>
    <FormDialog :open="editing" title="维护轮胎资料" :busy="busy" @close="editing=false">
      <form @submit.prevent="save">
        <div class="ui-form-grid">
          <label class="ui-form-field">胎号<input v-model.trim="form.tireNo" required maxlength="128" /></label>
          <label class="ui-form-field">条码<input v-model.trim="form.barcode" maxlength="128" /></label>
          <label class="ui-form-field ui-full">物料描述<input v-model.trim="form.materialDescription" required maxlength="1000" /></label>
          <label class="ui-form-field">到货时间<input v-model="form.arrivedAt" required type="datetime-local" /></label>
          <label class="ui-form-field ui-full">修改原因<textarea v-model.trim="form.reason" required rows="3" placeholder="记录本次维护的业务依据" /></label>
        </div>
        <div class="ui-dialog-actions"><button type="button" @click="editing=false">取消</button><button class="primary" :disabled="busy">保存并记录历史</button></div>
      </form>
    </FormDialog>
  </main>
</template>

<style scoped>
.tire-overview { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 14px; }
.tire-overview span { min-width: 138px; padding: 10px 14px; border: 1px solid var(--ui-border); border-radius: 8px; background: #fff; color: var(--ui-muted); }
.tire-overview strong { display: block; margin-top: 3px; color: var(--ui-primary, #087563); font-size: 20px; }
.tire-maintenance-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.tire-detail-tabs { display: flex; gap: 8px; margin-bottom: 20px; border-bottom: 1px solid var(--ui-border); }
.tire-detail-tabs button { border: 0; border-bottom: 2px solid transparent; border-radius: 0; background: transparent; padding: 9px 12px; }
.tire-detail-tabs button.active { border-bottom-color: var(--ui-primary, #087563); color: var(--ui-primary, #087563); font-weight: 600; }
.tire-maintenance-actions strong { color: var(--ui-text); }
.tire-history-table-wrap { width: 100%; border: 1px solid var(--ui-border); border-radius: 8px; overflow: hidden; }
.tire-history-table { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 13px; }
.tire-history-table th, .tire-history-table td { padding: 10px 12px; border-bottom: 1px solid var(--ui-border); text-align: left; vertical-align: top; white-space: normal; overflow-wrap: anywhere; }
.tire-history-table thead th { background: var(--ui-surface-subtle); color: var(--ui-text-secondary); }
.tire-history-table thead th:first-child { width: 21%; }
.tire-history-table thead th:nth-child(2) { width: 44%; }
.tire-approval-table thead th:first-child { width: 22%; }
.tire-approval-table thead th:nth-child(2) { width: 28%; }
.tire-approval-table thead th:nth-child(3) { width: 23%; }
.tire-approval-table thead th:nth-child(4) { width: 27%; }
.tire-history-table tbody tr.tire-history-group-start th,
.tire-history-table tbody tr.tire-history-group-start td { border-top: 2px solid var(--ui-border); }
.tire-history-line { display: block; margin-bottom: 4px; }
.tire-history-line:last-child { margin-bottom: 0; }
.tire-history-line b { color: var(--ui-text-secondary); font-weight: 600; }
.tire-history-table tbody tr:last-child th, .tire-history-table tbody tr:last-child td { border-bottom: 0; }
.tire-history-empty { text-align: center !important; color: var(--ui-text-secondary); }
</style>
