<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { masterApi, routeApi, salaryApi } from '../api/modules.js';
import { navigate } from '../router/routes.js';
import { can } from '../stores/auth.js';
import { sourceTypeLabels } from '../utils/displayLabels.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import EntityPicker from '../components/EntityPicker.vue';
import PaginationBar from '../components/PaginationBar.vue';
import FormDialog from '../components/FormDialog.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import ErrorState from '../components/ErrorState.vue';

const rows = ref([]);
const pageNo = ref(1);
const pageSize = 20;
const total = ref(0);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const selected = ref(null);
const history = ref([]);
const historyLoading = ref(false);
const formOpen = ref(false);
const formContext = ref(null);
const filters = reactive({ month: '', driverId: '', source: '', keyword: '' });
const form = reactive({ routeId: '', driverId: '', salaryAmount: '', reason: '' });
const money = (value) => value == null ? '—' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const visibleRows = computed(() => rows.value.filter((row) =>
  (!filters.source || row.sourceType === filters.source)
  && (!filters.keyword || [row.routeNo, row.driverName].some((value) => String(value || '').toLowerCase().includes(filters.keyword.toLowerCase())))));

async function load(page = 1) {
  loading.value = true; error.value = '';
  try {
    const result = await salaryApi.list(page, filters.driverId, filters.month, pageSize);
    rows.value = result.records || [];
    total.value = Number(result.total || 0);
    pageNo.value = page;
  } catch (cause) { rows.value = []; total.value = 0; error.value = cause.message || '工资列表加载失败'; }
  finally { loading.value = false; }
}
function openForm(row = null) {
  formContext.value = row;
  Object.assign(form, { routeId: row?.routeId || '', driverId: row?.driverId || '', salaryAmount: row?.salaryAmount || '', reason: '' });
  formOpen.value = true;
}
async function save() {
  busy.value = true; error.value = '';
  try {
    await salaryApi.create({ routeId: Number(form.routeId), driverId: Number(form.driverId), salaryAmount: form.salaryAmount, reason: form.reason || null });
    formOpen.value = false; await load(pageNo.value);
  } catch (cause) { error.value = cause.message || '工资保存失败'; }
  finally { busy.value = false; }
}
async function openHistory(row) {
  selected.value = row; history.value = []; historyLoading.value = true; error.value = '';
  try { history.value = await salaryApi.history(row.routeId); }
  catch (cause) { error.value = cause.message || '工资历史加载失败'; }
  finally { historyLoading.value = false; }
}
onMounted(() => load(1));
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="工资管理" description="按线路查看司机工资，调整保留历史记录。"><template #actions><button v-if="can('import:history')" type="button" @click="navigate('imports')">前往导入中心</button><button v-if="can('salary:manage')" class="primary" type="button" @click="openForm()">＋ 录入工资</button></template></PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load(pageNo)" />
    <FilterBar><label>月份<input v-model="filters.month" type="month" @change="load(1)" /></label><label>司机<EntityPicker v-model="filters.driverId" :load-page="masterApi.drivers" :label-of="(item) => `${item.name} · ${item.phone}`" placeholder="全部司机" /></label><label>来源<select v-model="filters.source"><option value="">全部来源</option><option value="MANUAL">人工录入</option><option value="IMPORT">导入</option></select></label><label class="ui-search">线路或司机<input v-model.trim="filters.keyword" type="search" placeholder="搜索当前页" /></label><button type="button" @click="load(1)">查询</button></FilterBar>
    <p class="muted">月份和司机由服务端筛选；来源与关键词筛选当前页。</p>
    <DataTable v-if="!error" :columns="[{key:'routeNo',label:'线路'},{key:'driverName',label:'司机'},{key:'businessMonth',label:'月份'},{key:'salaryAmount',label:'工资金额',numeric:true},{key:'sourceType',label:'来源'},{key:'updatedAt',label:'更新时间'},{key:'actions',label:'操作'}]" :rows="visibleRows" :loading="loading" :empty-text="filters.keyword || filters.source ? '当前页没有匹配工资' : '暂无工资记录'">
      <template #salaryAmount="{value}">{{ money(value) }}</template><template #sourceType="{value}">{{ sourceTypeLabels[value] || value }}</template><template #updatedAt="{row}">{{ row.updatedAt || row.createdAt || '—' }}</template>
      <template #actions="{row}"><div class="ui-inline-actions"><button type="button" @click="openHistory(row)">详情与历史</button><button v-if="can('salary:manage')" type="button" @click="openForm(row)">调整</button></div></template>
    </DataTable>
    <PaginationBar v-if="!error" :page-no="pageNo" :page-size="pageSize" :total="total" @change="load" />
    <FormDialog :open="formOpen" :title="formContext ? '调整工资' : '新增工资'" :busy="busy" @close="formOpen = false"><form @submit.prevent="save"><div class="ui-form-grid">
      <label class="ui-form-field ui-full">线路<EntityPicker v-model="form.routeId" :load-page="routeApi.list" :label-of="(item) => item.routeNo" :selected-label="formContext?.routeNo || ''" placeholder="请选择线路" required /></label>
      <label class="ui-form-field ui-full">司机<EntityPicker v-model="form.driverId" :load-page="masterApi.drivers" :label-of="(item) => `${item.name} · ${item.phone}`" :selected-label="formContext?.driverName || ''" placeholder="请选择司机" required /></label>
      <div v-if="formContext" class="ui-form-field">当前工资<strong>{{ money(formContext.salaryAmount) }}</strong></div>
      <label class="ui-form-field">{{ formContext ? '调整后工资（元）' : '工资金额（元）' }}<input v-model="form.salaryAmount" type="number" min="0.01" step="0.01" required /></label>
      <label class="ui-form-field ui-full">调整原因<input v-model.trim="form.reason" :required="!!formContext" placeholder="说明本次录入或调整原因" /></label>
    </div><p class="ui-form-summary">确认：{{ formContext ? '调整' : '新增' }} {{ formContext?.driverName || '所选司机' }} 的 {{ formContext?.routeNo || '所选线路' }}工资至 {{ money(form.salaryAmount) }}。</p><div class="ui-dialog-actions"><button type="button" @click="formOpen = false">取消</button><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '保存工资' }}</button></div></form></FormDialog>
    <DetailDrawer :open="!!selected" title="工资详情与历史" @close="selected = null"><template #subtitle>{{ selected?.routeNo }} · {{ selected?.driverName }}</template><div v-if="selected"><dl class="ui-detail-list"><div><dt>月份</dt><dd>{{ selected.businessMonth }}</dd></div><div><dt>当前金额</dt><dd>{{ money(selected.salaryAmount) }}</dd></div><div><dt>来源</dt><dd>{{ sourceTypeLabels[selected.sourceType] || selected.sourceType }}</dd></div><div><dt>更新时间</dt><dd>{{ selected.updatedAt || selected.createdAt }}</dd></div></dl><h3>调整历史</h3><p v-if="historyLoading" class="muted">正在加载历史…</p><p v-else-if="!history.length" class="muted">暂无调整历史</p><ol v-else class="ui-timeline"><li v-for="item in history" :key="item.id"><strong>{{ money(item.beforeAmount) }} → {{ money(item.afterAmount) }}</strong><p>{{ item.operationTime }} · {{ item.operatorPhone || '操作人信息未提供' }}</p><p>{{ item.reason || '未填写原因' }}</p></li></ol></div></DetailDrawer>
  </div>
</template>
