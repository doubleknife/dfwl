<script setup>
import { onMounted, reactive, ref } from 'vue';
import { masterApi, salaryApi } from '../api/modules.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import PaginationBar from '../components/PaginationBar.vue';
import ErrorState from '../components/ErrorState.vue';
import EntityPicker from '../components/EntityPicker.vue';

const filters = reactive({ month: '', driverId: '' });
const records = ref([]);
const selected = ref(null);
const history = ref([]);
const pageNo = ref(1);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const columns = [
  { key: 'taskNo', label: '任务号' }, { key: 'sequenceNo', label: '参与顺序' },
  { key: 'driverName', label: '司机' }, { key: 'driverType', label: '司机类型' },
  { key: 'businessDate', label: '业务日期' }, { key: 'salaryAmount', label: '核定工资', numeric: true },
  { key: 'actions', label: '操作' }
];

async function load(next = 1) {
  loading.value = true; error.value = '';
  try {
    const page = await salaryApi.list(next, filters.driverId, filters.month, 20);
    records.value = page.records || [];
    total.value = Number(page.total || 0);
    pageNo.value = next;
  } catch (cause) { error.value = cause.message || '参与人工资加载失败'; }
  finally { loading.value = false; }
}
async function detail(row) {
  error.value = '';
  try { history.value = await salaryApi.history(row.participantId); selected.value = row; }
  catch (cause) { error.value = cause.message || '工资历史加载失败'; }
}
onMounted(() => load());
</script>

<template>
  <main class="ui-page-container">
    <PageHeader title="参与人工资" description="工资由运输任务参与人核定，列表和历史均读取同一正式事实。">
      <template #actions><button type="button" @click="load(pageNo)">刷新</button></template>
    </PageHeader>
    <ErrorState v-if="error" :message="error" @retry="load(pageNo)" />
    <FilterBar>
      <label>月份<input v-model="filters.month" type="month" /></label>
      <label>司机<EntityPicker v-model="filters.driverId" :load-page="masterApi.drivers" :label-of="(driver) => `${driver.name} · ${driver.phone || ''}`" placeholder="全部司机" /></label>
      <button type="button" @click="load(1)">查询</button>
    </FilterBar>
    <DataTable :columns="columns" :rows="records" row-key="participantId" :loading="loading" empty-text="暂无参与人工资">
      <template #driverType="{ row }">{{ row.driverType === 'OUTSOURCED' ? '外协司机' : '自有司机' }}</template>
      <template #salaryAmount="{ row }">¥ {{ row.salaryAmount }}</template>
      <template #actions="{ row }"><button type="button" @click="detail(row)">历史</button></template>
    </DataTable>
    <PaginationBar :page-no="pageNo" :page-size="20" :total="total" @change="load" />
    <DetailDrawer :open="!!selected" title="参与人工资历史" @close="selected = null">
      <template #subtitle>{{ selected?.taskNo }} · {{ selected?.driverName }}</template>
      <p v-if="selected">当前核定工资：¥ {{ selected.salaryAmount }}</p>
      <p v-if="!history.length">暂无调整历史</p>
      <ol v-else class="ui-timeline"><li v-for="item in history" :key="item.id">
        <strong>¥ {{ item.beforeAmount ?? '—' }} → ¥ {{ item.afterAmount }}</strong>
        <p>{{ item.operatedAt }} · {{ item.reason }}</p>
      </li></ol>
    </DetailDrawer>
  </main>
</template>
