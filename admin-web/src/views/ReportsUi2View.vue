<script setup>
import { computed, onMounted, ref } from 'vue';
import { reportApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import FilterBar from '../components/FilterBar.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import StatusTag from '../components/StatusTag.vue';
import { firstAllowedReport, money, pageRows as slicePage } from './ui2DataModel.js';
import { attributionLabels, expenseTypeLabels, sourceTypeLabels } from '../utils/displayLabels.js';

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
const archiveRows = ref([]); const archiveTotal = ref(0); const archivePage = ref(1);
const archiveStatus = ref('COMPLETED'); const archiveLoading = ref(false); const archiveDetail = ref(null);
const archiveExpensePage = ref(1);
async function loadArchive(next = 1) {
  archiveLoading.value = true; error.value = '';
  try {
    const result = await reportApi.completedRoutes(next, 20, archiveStatus.value);
    archiveRows.value = result.records || []; archiveTotal.value = Number(result.total || 0); archivePage.value = next;
  } catch (cause) { error.value = cause.message || '完成线路经营摘要读取失败'; }
  finally { archiveLoading.value = false; }
}
async function openArchive(row) {
  archiveLoading.value = true; error.value = '';
  try { archiveDetail.value = await reportApi.routeArchive(row.routeId); archiveExpensePage.value = 1; }
  catch (cause) { error.value = cause.message || '线路经营档案读取失败'; }
  finally { archiveLoading.value = false; }
}
async function loadArchiveExpense(next) {
  if (!archiveDetail.value) return;
  archiveLoading.value = true; error.value = '';
  try {
    archiveDetail.value = await reportApi.routeArchive(archiveDetail.value.summary.routeId, next);
    archiveExpensePage.value = next;
  } catch (cause) { error.value = cause.message || '线路费用读取失败'; }
  finally { archiveLoading.value = false; }
}
async function load(key = current.value?.key) {
  const item = allowed.value.find(row => row.key === key); if (!item) return;
  selected.value = key; page.value = 1; search.value = ''; loading.value = true; error.value = '';
  try { rows.value = await item.load() || []; }
  catch (cause) { rows.value = []; error.value = cause.message || '报表读取失败'; }
  finally { loading.value = false; }
}
onMounted(() => { if (allowed.value.length) load(allowed.value[0].key); if (can('report:profit')) loadArchive(); });
</script>
<template>
  <div class="ui-page-container"><PageHeader title="经营报表" description="各报表使用正式业务口径；切换分类查看对应数据。" /><ErrorState v-if="error" :message="error" @retry="load(selected)" />
    <section v-if="can('report:profit')" class="ui-section-card">
      <div class="ui-list-heading"><div><h2>已完成线路经营档案</h2><p class="muted">收入、费用和参与工资按正式事实计算；作废线路与有效经营分开显示。</p></div><label>线路状态<select v-model="archiveStatus" @change="loadArchive(1)"><option value="COMPLETED">已完成</option><option value="VOIDED">已作废</option></select></label></div>
      <DataTable :columns="[{key:'routeNo',label:'线路号'},{key:'businessDate',label:'日期'},{key:'status',label:'状态'},{key:'netWeight',label:'净重',numeric:true},{key:'participantCount',label:'参与人数',numeric:true},{key:'vehicleCount',label:'公司车辆数',numeric:true},{key:'incomeAmount',label:'收入',numeric:true},{key:'expenseTotal',label:'总支出',numeric:true},{key:'profitAmount',label:'利润',numeric:true},{key:'actions',label:'操作'}]" :rows="archiveRows" :loading="archiveLoading" empty-text="当前状态没有线路">
        <template #status="{value}"><StatusTag :value="value" /></template>
        <template #netWeight="{value}">{{ value == null ? '—' : `${value} 吨` }}</template>
        <template #incomeAmount="{value}">{{ money(value) }}</template><template #expenseTotal="{value}">{{ money(value) }}</template><template #profitAmount="{value}">{{ money(value) }}</template>
        <template #actions="{row}"><button type="button" @click="openArchive(row)">查看档案</button></template>
      </DataTable>
      <PaginationBar :page-no="archivePage" :page-size="20" :total="archiveTotal" @change="loadArchive" />
    </section>
    <div class="ui-subtabs"><button v-for="item in allowed" :key="item.key" type="button" :class="{active:current?.key===item.key}" @click="load(item.key)">{{ item.label }}</button></div>
    <section v-if="current && !error" class="ui-section-card"><div class="ui-list-heading"><div><h2>{{ current.label }}</h2><p class="muted">共 {{ rows.length }} 条；页面按已返回的完整报表结果分页。</p></div><button type="button" @click="load(selected)">刷新</button></div><FilterBar><label>当前报表关键词<input v-model.trim="search" type="search" placeholder="搜索车牌、司机或线路" @input="page=1" /></label><span class="muted">在当前报表完整结果中筛选。</span></FilterBar>
      <DataTable :columns="columns" :rows="pageRows" :loading="loading" :empty-text="`暂无${current.label}数据`"><template v-for="column in columns" :key="column.key" #[column.key]="{value,row}"><template v-if="moneyKeys.has(column.key)">{{ money(value) }}</template><template v-else-if="column.key==='worked'">{{ value ? '已出勤' : '未出勤' }}</template><template v-else-if="column.key==='energyType'">{{ {ELECTRIC:'电',GAS:'气',TEMP_ELECTRIC:'临时电'}[value] || value }}</template><template v-else-if="column.key==='actualWeight'">{{ value ?? 0 }} 吨</template><template v-else-if="column.key==='mileageKm'">{{ value ?? 0 }} 公里</template><template v-else-if="column.key==='quantity'">{{ value ?? 0 }} {{ row.energyType === 'GAS' ? '立方米' : '度' }}</template><template v-else>{{ value ?? '—' }}</template></template></DataTable>
      <PaginationBar :page-no="page" :page-size="20" :total="filtered.length" @change="page=$event" /></section>
    <DetailDrawer :open="!!archiveDetail" title="线路经营档案" @close="archiveDetail=null">
      <template #subtitle>{{ archiveDetail?.summary?.routeNo }}</template>
      <template v-if="archiveDetail"><div class="ui-summary-row"><span>当前有效收入 <strong>{{ money(archiveDetail.summary.incomeAmount) }}</strong></span><span>归属费用 <strong>{{ money(archiveDetail.summary.attributedExpense) }}</strong></span><span>参与工资 <strong>{{ money(archiveDetail.summary.participantSalary) }}</strong></span><span>当前有效总支出 <strong>{{ money(archiveDetail.summary.expenseTotal) }}</strong></span><span>当前有效利润 <strong>{{ money(archiveDetail.summary.profitAmount) }}</strong></span></div>
        <p v-if="archiveDetail.summary.status === 'VOIDED'" class="muted">此线路已作废；收入、参与工资及归属费用退出当前有效线路经营，公司真实费用继续计入公司口径。</p>
        <h3>参与司机</h3><DataTable :columns="[{key:'sequenceNo',label:'顺序'},{key:'driverName',label:'司机'},{key:'driverType',label:'类型'},{key:'companyPlateNo',label:'公司车牌'},{key:'outsourcedPlateNo',label:'外协车牌'},{key:'salaryAmount',label:'核定工资',numeric:true}]" :rows="archiveDetail.participants" empty-text="暂无参与记录"><template #companyPlateNo="{value}">{{ value || '—' }}</template><template #salaryAmount="{value}">{{ money(value) }}</template></DataTable>
        <h3>归属费用</h3><p class="muted">含冲销记录，金额按正负净额计入上方归属费用。</p><DataTable :columns="[{key:'expenseNo',label:'费用编号'},{key:'expenseType',label:'类型'},{key:'plateNo',label:'公司车牌'},{key:'sourceType',label:'来源'},{key:'attributionType',label:'当前归属'},{key:'amount',label:'金额',numeric:true}]" :rows="archiveDetail.expenses.records" empty-text="暂无归属费用"><template #expenseType="{value}">{{ expenseTypeLabels[value] || value }}</template><template #sourceType="{value}">{{ sourceTypeLabels[value] || value }}</template><template #attributionType="{value}">{{ attributionLabels[value] || value }}</template><template #amount="{value}">{{ money(value) }}</template></DataTable>
        <PaginationBar :page-no="archiveExpensePage" :page-size="20" :total="archiveDetail.expenses.total" @change="loadArchiveExpense" />
      </template>
    </DetailDrawer>
  </div>
</template>
