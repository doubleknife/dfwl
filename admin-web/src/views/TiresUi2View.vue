<script setup>
import { onMounted, reactive, ref } from 'vue';
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

const filters = reactive({ tireNo: '', status: '' });
const rows = ref([]);
const page = ref(1);
const total = ref(0);
const selected = ref(null);
const editing = ref(false);
const form = reactive({ tireNo: '', barcode: '', materialDescription: '', arrivedAt: '', reason: '', expectedVersion: 0 });
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const statusName = { AVAILABLE: '可用', CLAIMED: '已申领' };
const fieldName = { tire_no: '胎号', barcode: '条码', material_description: '物料描述', arrived_at: '到货时间', status: '状态' };
const dateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '—';

async function load(next = 1) {
  if (!can('PAGE_TIRES')) return;
  loading.value = true; error.value = '';
  try {
    const result = await tireApi.list(next, 20, filters);
    rows.value = result.records || [];
    total.value = Number(result.total || 0);
    page.value = next;
  } catch (cause) { error.value = cause.message || '轮胎库存加载失败'; rows.value = []; }
  finally { loading.value = false; }
}
async function open(row) {
  busy.value = true; error.value = '';
  try { selected.value = await tireApi.detail(row.id); }
  catch (cause) { error.value = cause.message || '轮胎详情加载失败'; }
  finally { busy.value = false; }
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
      <div class="tire-overview" role="status"><span>库存记录 <strong>{{ total }}</strong></span><span>当前页可用 <strong>{{ rows.filter((item) => item.status === 'AVAILABLE').length }}</strong></span><span>当前页已申领 <strong>{{ rows.filter((item) => item.status === 'CLAIMED').length }}</strong></span></div>
      <FilterBar>
        <label class="ui-search">胎号<input v-model.trim="filters.tireNo" type="search" placeholder="搜索胎号" @keyup.enter="load(1)" /></label>
        <label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="AVAILABLE">可用</option><option value="CLAIMED">已申领</option></select></label>
        <button type="button" @click="load(1)">查询</button>
        <button type="button" @click="filters.tireNo='';filters.status='';load(1)">清除</button>
      </FilterBar>
      <DataTable v-if="!error" :columns="[{key:'tireNo',label:'胎号'},{key:'barcode',label:'条码'},
        {key:'materialDescription',label:'物料描述'},{key:'arrivedAt',label:'到货时间'},
        {key:'status',label:'状态'},{key:'actions',label:'操作'}]"
        :rows="rows" :loading="loading" empty-text="暂无符合条件的轮胎">
        <template #arrivedAt="{value}">{{ dateTime(value) }}</template>
        <template #status="{value}"><StatusTag :value="statusName[value] || '状态异常'" :tone="value === 'AVAILABLE' ? 'success' : 'info'" /></template>
        <template #actions="{row}"><button type="button" :disabled="busy" @click="open(row)">详情</button></template>
      </DataTable>
      <PaginationBar v-if="!error" :page-no="page" :page-size="20" :total="total" @change="load" />
    </div>
    <p v-else class="ui-section-card">当前账号没有轮胎库存查看权限。</p>
    <DetailDrawer :open="!!selected" title="轮胎详情" @close="selected=null">
      <template #subtitle>{{ selected?.tireNo }}</template>
      <template v-if="selected">
        <dl class="ui-detail-grid">
          <dt>当前状态</dt><dd><StatusTag :value="statusName[selected.status] || '状态异常'" /></dd>
          <dt>胎号</dt><dd>{{ selected.tireNo }}</dd>
          <dt>条码</dt><dd>{{ selected.barcode || '未填写' }}</dd>
          <dt>物料描述</dt><dd>{{ selected.materialDescription }}</dd>
          <dt>到货时间</dt><dd>{{ dateTime(selected.arrivedAt) }}</dd>
          <dt>入库时间</dt><dd>{{ dateTime(selected.createdAt) }}</dd>
        </dl>
        <h3>维护历史</h3>
        <DataTable :columns="[{key:'fieldName',label:'修改项目'},{key:'beforeValue',label:'修改前'},
          {key:'afterValue',label:'修改后'},{key:'reason',label:'原因'},
          {key:'operatorPhone',label:'操作账号'},{key:'operatedAt',label:'时间'}]"
          :rows="selected.history || []" empty-text="暂无人工维护记录">
          <template #fieldName="{value}">{{ fieldName[value] || '业务信息' }}</template>
          <template #operatedAt="{value}">{{ dateTime(value) }}</template>
        </DataTable>
      </template>
      <template #footer><button v-if="can('BUTTON_TIRES_MANAGE') && selected?.status === 'AVAILABLE'" type="button" @click="beginEdit">维护轮胎资料</button></template>
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
</style>
