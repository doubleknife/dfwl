<script setup>
import { computed } from 'vue';
import { calendarDays, dayFact, isCurrentDay, isWeekend, rowTotalMatches } from './vehicleMonthlyGrid.js';

const props = defineProps({
  rows: { type: Array, required: true },
  month: { type: String, required: true },
  scope: { type: String, required: true }
});
const emit = defineEmits(['open-day']);
const days = computed(() => calendarDays(props.month));
const name = computed(() => props.scope === 'OUTSOURCED' ? '外协车辆' : '自有车辆');
function openDay(row, day) {
  const fact = dayFact(row, day);
  if (fact?.count > 0 && fact.tasks?.length) emit('open-day', row, day);
}
</script>

<template>
  <div v-if="!rows.length" class="vehicle-month-empty" role="status">当前月份暂无实际发车记录，请选择其它月份查询。</div>
  <div v-else class="vehicle-month-scroll" role="region" :aria-label="`${name}，日期区域可横向滚动`" tabindex="0">
    <table class="vehicle-month-table">
      <thead><tr>
        <th class="vehicle-month-fixed vehicle-month-plate" scope="col">车牌</th>
        <th class="vehicle-month-fixed vehicle-month-driver" scope="col">执行司机</th>
        <th class="vehicle-month-fixed vehicle-month-trailer" scope="col">当时车挂</th>
        <th v-for="day in days" :key="day" scope="col" :class="{ 'vehicle-month-weekend': isWeekend(month, day), 'vehicle-month-today': isCurrentDay(month, day) }">{{ day }}日</th>
        <th class="vehicle-month-fixed vehicle-month-total" scope="col">月合计</th>
      </tr></thead>
      <tbody><tr v-for="row in rows" :key="row.rowKey">
        <th class="vehicle-month-fixed vehicle-month-plate" scope="row">{{ row.plate || '—' }}</th>
        <td class="vehicle-month-fixed vehicle-month-driver">{{ row.driverName || '—' }}</td>
        <td class="vehicle-month-fixed vehicle-month-trailer">{{ row.trailerPlate || '—' }}</td>
        <td v-for="day in days" :key="day" :class="{ 'vehicle-month-weekend': isWeekend(month, day), 'vehicle-month-today': isCurrentDay(month, day) }">
          <button v-if="dayFact(row, day)?.count > 0 && dayFact(row, day)?.tasks?.length" type="button" class="vehicle-month-count" :aria-label="`${row.plate}，${row.driverName}，${day}日实际发车${dayFact(row, day).count}趟，查看任务`" @click="openDay(row, day)">{{ dayFact(row, day).count }}</button>
          <span v-else-if="dayFact(row, day)?.count > 0" class="vehicle-month-count vehicle-month-count-static">{{ dayFact(row, day).count }}</span>
          <span v-else class="vehicle-month-dash">—</span>
        </td>
        <td class="vehicle-month-fixed vehicle-month-total"><strong>{{ row.monthlyTotal }}</strong><small v-if="!rowTotalMatches(row)" role="alert">明细不一致</small></td>
      </tr></tbody>
    </table>
  </div>
</template>

<style scoped>
.vehicle-month-empty{padding:52px 20px;text-align:center;color:#647b73;background:#fff;border:1px solid #e1e9e5;border-radius:10px}
.vehicle-month-scroll{max-width:100%;max-height:min(63vh,650px);overflow:auto;overscroll-behavior:contain;background:#fff;border:1px solid #dfe9e4;border-radius:10px}
.vehicle-month-table{border-collapse:separate;border-spacing:0;width:max-content;min-width:100%;table-layout:fixed;font-size:13px;color:#253f37}
.vehicle-month-table th,.vehicle-month-table td{box-sizing:border-box;height:42px;padding:7px 8px;border-bottom:1px solid #edf1ee;text-align:center;white-space:nowrap}
.vehicle-month-table thead th{position:sticky;top:0;z-index:3;background:#f4f8f6;color:#526b62;font-weight:600}
.vehicle-month-table thead th:not(.vehicle-month-fixed),.vehicle-month-table tbody td:not(.vehicle-month-fixed){min-width:46px;width:46px}
.vehicle-month-table tbody tr:last-child>*{border-bottom:0}
.vehicle-month-table tbody tr:hover>.vehicle-month-fixed,.vehicle-month-table tbody tr:hover>td:not(.vehicle-month-fixed){background:#f7fbf9}
.vehicle-month-table .vehicle-month-fixed{position:sticky;z-index:2;background:#fff}
.vehicle-month-table thead .vehicle-month-fixed{z-index:5;background:#f4f8f6}
.vehicle-month-table .vehicle-month-plate{left:0;min-width:150px;width:150px;text-align:left;box-shadow:1px 0 #e0e9e4}
.vehicle-month-table .vehicle-month-driver{left:150px;min-width:145px;width:145px;text-align:left;box-shadow:1px 0 #e0e9e4}
.vehicle-month-table .vehicle-month-trailer{left:295px;min-width:135px;width:135px;text-align:left;box-shadow:2px 0 3px -2px #b6c8bd}
.vehicle-month-table .vehicle-month-total{right:0;min-width:94px;width:94px;background:#f4faf6;box-shadow:-2px 0 3px -2px #b6c8bd}
.vehicle-month-table thead .vehicle-month-total{z-index:6;background:#eaf4ed}
.vehicle-month-table tbody .vehicle-month-total{background:#f4faf6;color:#126b51}
.vehicle-month-table .vehicle-month-weekend{background:#fafbf8}
.vehicle-month-table .vehicle-month-today{box-shadow:inset 0 2px #8dbda4}
.vehicle-month-count{min-width:30px;min-height:27px;border:0;border-radius:5px;background:#e6f4eb;color:#116a4d;font-weight:700;cursor:pointer}
.vehicle-month-count:hover{background:#d2eadb}.vehicle-month-count:focus-visible{outline:2px solid #137254;outline-offset:2px}
.vehicle-month-count-static{display:inline-flex;align-items:center;justify-content:center;cursor:default}
.vehicle-month-dash{color:#abbab2}.vehicle-month-total small{display:block;color:#a54a36;font-size:11px}
@media(max-width:1440px){.vehicle-month-table .vehicle-month-plate{min-width:136px;width:136px}.vehicle-month-table .vehicle-month-driver{left:136px;min-width:132px;width:132px}.vehicle-month-table .vehicle-month-trailer{left:268px;min-width:120px;width:120px}}
</style>
