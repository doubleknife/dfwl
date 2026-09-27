<script setup>
import { computed, onMounted, ref } from 'vue';
import { reportApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import FilterBar from '../components/FilterBar.vue';
import { firstAllowedReport, money, pageRows as slicePage } from './ui2DataModel.js';

const definitions = [
  { key: 'profit', label: '线路利润', permission: 'report:profit', load: reportApi.profit, columns: [['routeNo','线路'],['businessDate','日期'],['customerName','客户'],['driverName','司机'],['plateNo','车辆'],['actualWeight','实际载重'],['incomeAmount','收入',true],['routeExpense','费用',true],['driverSalary','工资',true],['profitAmount','利润',true]] },
  { key: 'vehicle', label: '车辆支出', permission: 'report:vehicle', load: reportApi.vehicle, columns: [['plateNo','车牌'],['routeCount','趟次'],['penaltyAmount','罚款',true],['carWashAmount','洗车费',true],['waterAmount','水费',true],['repairAmount','维修费',true],['energyAmount','能源费',true],['amount','支出合计',true]] },
  { key: 'driver', label: '司机支出', permission: 'report:driver', load: reportApi.driver, columns: [['driverName','司机'],['routeCount','趟次'],['mileageKm','里程'],['penaltyAmount','罚款',true],['carWashAmount','洗车费',true],['waterAmount','水费',true],['repairAmount','维修费',true],['driverSalary','工资',true],['amount','费用合计',true]] },
  { key: 'attendance', label: '出勤', permission: 'report:attendance', load: reportApi.attendance, columns: [['workDate','工作日期'],['driverName','司机'],['routeCount','趟次'],['workDays','出勤天数'],['worked','出勤']] },
  { key: 'energy', label: '电费与气费', permission: 'report:energy', load: reportApi.energy, columns: [['plateNo','车牌'],['energyType','能源类型'],['quantity','用量',true],['amount','金额',true],['orderCount','笔数']] }
];
const allowed = computed(() => definitions.filter(item => can(item.permission)));
const selected = ref(''); const rows = ref([]); const page = ref(1); const loading = ref(false); const error = ref(''); const search = ref('');
const current = computed(() => allowed.value.find(item => item.key === selected.value) || firstAllowedReport(definitions, can));
const filtered = computed(() => rows.value.filter(row => !search.value || Object.values(row).some(value => String(value ?? '').toLowerCase().includes(search.value.toLowerCase()))));
const pageRows = computed(() => slicePage(filtered.value, page.value));
const columns = computed(() => (current.value?.columns || []).map(([key,label,numeric]) => ({key,label,numeric:!!numeric})));
const moneyKeys = new Set(['incomeAmount','routeExpense','driverSalary','profitAmount','penaltyAmount','carWashAmount','waterAmount','repairAmount','energyAmount','amount']);
async function load(key = current.value?.key) {
  const item = allowed.value.find(row => row.key === key); if (!item) return;
  selected.value = key; page.value = 1; search.value = ''; loading.value = true; error.value = '';
  try { rows.value = await item.load() || []; }
  catch (cause) { rows.value = []; error.value = cause.message || '报表读取失败'; }
  finally { loading.value = false; }
}
onMounted(() => { if (allowed.value.length) load(allowed.value[0].key); });
</script>
<template>
  <div class="ui-page-container"><PageHeader title="经营报表" description="各报表使用正式业务口径；切换分类查看对应数据。" /><ErrorState v-if="error" :message="error" @retry="load(selected)" />
    <div class="ui-subtabs"><button v-for="item in allowed" :key="item.key" type="button" :class="{active:current?.key===item.key}" @click="load(item.key)">{{ item.label }}</button></div>
    <section v-if="current && !error" class="ui-section-card"><div class="ui-list-heading"><div><h2>{{ current.label }}</h2><p class="muted">共 {{ rows.length }} 条；页面按已返回的完整报表结果分页。</p></div><button type="button" @click="load(selected)">刷新</button></div><FilterBar><label>当前报表关键词<input v-model.trim="search" type="search" placeholder="搜索车牌、司机或线路" @input="page=1" /></label><span class="muted">在当前报表完整结果中筛选。</span></FilterBar>
      <DataTable :columns="columns" :rows="pageRows" :loading="loading" :empty-text="`暂无${current.label}数据`"><template v-for="column in columns" :key="column.key" #[column.key]="{value,row}"><template v-if="moneyKeys.has(column.key)">{{ money(value) }}</template><template v-else-if="column.key==='worked'">{{ value ? '已出勤' : '未出勤' }}</template><template v-else-if="column.key==='energyType'">{{ {ELECTRIC:'电',GAS:'气',TEMP_ELECTRIC:'临时电'}[value] || value }}</template><template v-else-if="column.key==='actualWeight'">{{ value ?? 0 }} 吨</template><template v-else-if="column.key==='mileageKm'">{{ value ?? 0 }} 公里</template><template v-else-if="column.key==='quantity'">{{ value ?? 0 }} {{ row.energyType === 'GAS' ? '立方米' : '度' }}</template><template v-else>{{ value ?? '—' }}</template></template></DataTable>
      <PaginationBar :page-no="page" :page-size="20" :total="filtered.length" @change="page=$event" /></section>
  </div>
</template>
