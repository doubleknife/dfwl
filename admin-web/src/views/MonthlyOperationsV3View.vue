<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { attendanceApi, dispatchLedgerApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import EntityPicker from '../components/EntityPicker.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import DetailDrawer from '../components/DetailDrawer.vue';

const props = defineProps({ scope: { type: String, required: true } });
const attendance = computed(() => props.scope === 'ATTENDANCE');
const outsourced = computed(() => props.scope === 'OUTSOURCED');
const title = computed(() => attendance.value ? '司机出勤' : outsourced.value ? '外协车辆月度台账' : '自有车辆月度台账');
const today = new Date();
const filters = reactive({ month: `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`, driverId: '' });
const rows = ref([]);
const pageNo = ref(1);
const total = ref(0);
const monthTotal = ref(0);
const daysInMonth = ref(31);
const loading = ref(false);
const exporting = ref(false);
const error = ref('');
const selected = ref(null);
const days = Array.from({ length: 31 }, (_, index) => index + 1);
const permission = computed(() => attendance.value ? 'attendance:export' : 'dispatch:ledger:export');
const description = computed(() => attendance.value
  ? '仅统计自有司机实际发车日期；同一天多趟仍计 1 天。'
  : '按参与人真实发车事实及当时车牌、车挂快照统计；当天多次发车逐次计数。');

async function load(next = 1) {
  loading.value = true; error.value = '';
  try {
    const result = attendance.value
      ? await attendanceApi.list({ month: filters.month, driverId: filters.driverId }, next)
      : await dispatchLedgerApi.list(props.scope, { month: filters.month }, next);
    rows.value = result.records || [];
    pageNo.value = next;
    total.value = Number(result.total || 0);
    monthTotal.value = Number(result.monthTotal || 0);
    daysInMonth.value = Number(result.daysInMonth || 31);
  } catch (cause) { error.value = cause.message || '月度台账加载失败'; }
  finally { loading.value = false; }
}
function openDay(row, day) {
  const fact = row.days.find((item) => item.day === day);
  if (fact?.tasks?.length) selected.value = { title: attendance.value ? row.driverName : `${row.plate} · ${row.driverName}`, day, tasks: fact.tasks };
}
async function download() {
  exporting.value = true; error.value = '';
  try {
    const blob = attendance.value
      ? await attendanceApi.export({ month: filters.month, driverId: filters.driverId })
      : await dispatchLedgerApi.export(props.scope, { month: filters.month });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a'); link.href = url; link.download = `${title.value}-${filters.month}.xlsx`;
    link.click(); URL.revokeObjectURL(url);
  } catch (cause) { error.value = cause.message || '月度台账导出失败'; }
  finally { exporting.value = false; }
}
onMounted(() => load());
</script>

<template>
  <main class="ui-page-container">
    <PageHeader :title="title" :description="description">
      <template #actions><button type="button" @click="load(pageNo)">刷新</button><button v-if="can(permission)" type="button" :disabled="exporting" @click="download">{{ exporting ? '正在导出…' : '下载当月 Excel' }}</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load(pageNo)" />
    <FilterBar><label>月份<input v-model="filters.month" type="month" /></label>
      <label v-if="attendance">自有司机<EntityPicker v-model="filters.driverId" remote-search :load-page="attendanceApi.drivers" :label-of="(driver) => `${driver.label} · ${driver.detail || ''}`" placeholder="全部自有司机" /></label>
      <button type="button" @click="load(1)">查询</button>
    </FilterBar>
    <p class="v3-month-hint">{{ attendance ? '本月出勤人日' : '本月实际发车次数' }}：<strong>{{ monthTotal }}</strong> · 点击有记录的日期查看任务。</p>
    <div class="v3-month-scroll" role="region" :aria-label="`${title}月表`" tabindex="0">
      <table class="v3-month-table"><thead><tr>
        <th class="v3-sticky">{{ attendance ? '司机' : '车牌 / 执行司机' }}</th>
        <th v-if="!attendance">当时车挂</th>
        <th v-for="day in days" :key="day">{{ day }}日</th><th>月合计</th>
      </tr></thead><tbody>
        <tr v-for="row in rows" :key="row.rowKey">
          <th scope="row" class="v3-sticky"><strong>{{ attendance ? row.driverName : row.plate }}</strong><small v-if="!attendance">{{ row.driverName }}</small></th>
          <td v-if="!attendance">{{ row.trailerPlate || '—' }}</td>
          <td v-for="day in days" :key="day"><button v-if="row.days[day - 1]?.count" type="button" class="v3-day" :aria-label="`${day}日${row.days[day - 1].count}次，查看任务`" @click="openDay(row, day)">{{ attendance ? '✓' : row.days[day - 1].count }}</button><span v-else class="v3-blank">—</span></td>
          <td><strong>{{ row.monthlyTotal }}</strong></td>
        </tr></tbody></table>
      <p v-if="loading" class="ui-loading" role="status">正在加载月度数据…</p>
      <p v-else-if="!rows.length" class="ui-empty" role="status">当前月份暂无记录</p>
    </div>
    <PaginationBar :page-no="pageNo" :page-size="20" :total="total" @change="load" />
    <DetailDrawer :open="!!selected" :title="`${selected?.day || ''}日实际发车任务`" @close="selected = null">
      <template #subtitle>{{ selected?.title }}</template>
      <div v-for="task in selected?.tasks || []" :key="`${task.taskId}-${task.departedAt}`" class="v3-month-task"><strong>{{ task.taskNo }}</strong><span>{{ task.templateName }} · {{ task.customerName }}</span><small>发车：{{ task.departedAt }}</small></div>
    </DetailDrawer>
  </main>
</template>

<style scoped>
.v3-month-hint{margin:16px 0;color:#506c65}.v3-month-scroll{overflow-x:auto;max-width:100%;background:#fff;border:1px solid #e2ebe7;border-radius:10px}.v3-month-table{border-collapse:separate;border-spacing:0;min-width:1400px;width:100%;font-size:14px}.v3-month-table th,.v3-month-table td{border-bottom:1px solid #edf1ee;padding:11px 8px;text-align:center;white-space:nowrap}.v3-month-table thead th{background:#f6f9f7;color:#516c65}.v3-month-table .v3-sticky{position:sticky;left:0;z-index:2;background:#fff;text-align:left;min-width:170px;box-shadow:1px 0 #e2ebe7}.v3-month-table thead .v3-sticky{z-index:3;background:#f6f9f7}.v3-month-table small{display:block;color:#6a817a;font-weight:400}.v3-day{min-width:38px;min-height:38px;border:0;border-radius:6px;color:#086b5d;background:#e6f3ef;cursor:pointer;font-weight:700}.v3-day:hover{background:#cce9e1}.v3-blank{color:#9caaa5}.v3-month-task{padding:14px 0;border-bottom:1px solid #e6eeea}.v3-month-task>*{display:block;margin:3px 0}.v3-month-task span,.v3-month-task small{color:#59716b}
</style>
