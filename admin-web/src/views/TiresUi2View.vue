<script setup>
import { onMounted, ref } from 'vue';
import { masterApi, tireApi } from '../api/modules.js';
import { can } from '../stores/auth.js';
import PageHeader from '../components/PageHeader.vue';
import FilterBar from '../components/FilterBar.vue';
import DataTable from '../components/DataTable.vue';
import PaginationBar from '../components/PaginationBar.vue';
import DetailDrawer from '../components/DetailDrawer.vue';
import StatusTag from '../components/StatusTag.vue';
import ErrorState from '../components/ErrorState.vue';
import EntityPicker from '../components/EntityPicker.vue';
import { displayValue } from '../utils/displayLabels.js';

const rows = ref([]);
const selected = ref(null);
const page = ref(1);
const total = ref(0);
const query = ref('');
const status = ref('');
const vehicleId = ref('');
const loading = ref(false);
const error = ref('');

async function load(next = 1) {
  if (!can('tire:list')) return;
  loading.value = true; error.value = '';
  try {
    const result = await tireApi.list(next, 20, { tireNo: query.value, status: status.value, vehicleId: vehicleId.value });
    rows.value = result.records || [];
    total.value = Number(result.total || 0);
    page.value = next;
  } catch (cause) { error.value = cause.message || '轮胎库存读取失败'; rows.value = []; }
  finally { loading.value = false; }
}
function open(row) { selected.value = row; }
onMounted(load);
</script>

<template>
  <div class="ui-page-container">
    <PageHeader title="轮胎管理" description="查看库存、预留及已申领轮胎的当前事实。换胎申请由司机在小程序按正式 OCR 流程发起。" />
    <ErrorState v-if="error" :message="error" @retry="load(page)" />
    <div v-if="can('tire:list')" class="ui-list-heading"><h2>轮胎库存与使用状态</h2><button type="button" @click="load(page)">刷新</button></div>
    <template v-if="can('tire:list')">
      <FilterBar><label>完整胎号<input v-model.trim="query" type="search" placeholder="输入完整胎号" /></label><label>状态<select v-model="status"><option value="">全部状态</option><option value="IN_STOCK">库存中</option><option value="APPROVAL_RESERVED">审批预留</option><option value="CLAIMED">已申领</option></select></label><label v-if="can('vehicle:list')">关联车辆<EntityPicker v-model="vehicleId" :load-page="masterApi.vehicles" :label-of="row=>row.plateNo" placeholder="全部车辆" /></label><button type="button" @click="load(1)">查询</button><button type="button" @click="query='';status='';vehicleId='';load(1)">清除</button></FilterBar>
      <DataTable v-if="!error" :columns="[{key:'tireNo',label:'胎号'},{key:'status',label:'状态'},{key:'barcode',label:'条码'},{key:'vehiclePlateNo',label:'关联车辆'},{key:'driverName',label:'关联司机'},{key:'installTime',label:'安装时间'},{key:'actions',label:'操作'}]" :rows="rows" :loading="loading" empty-text="没有符合条件的轮胎"><template #status="{value}"><StatusTag :value="value" /></template><template #actions="{row}"><button type="button" @click="open(row)">查看详情</button></template></DataTable>
      <PaginationBar v-if="!error" :page-no="page" :page-size="20" :total="total" @change="load" />
    </template>
    <p v-else class="ui-section-card">当前账号没有轮胎库存查看权限。换胎申请请使用司机端的正式流程。</p>
    <DetailDrawer :open="!!selected" title="轮胎详情" @close="selected=null"><template v-if="selected"><p class="ui-detail-lead">{{ selected.tireNo }}</p><dl class="ui-detail-grid"><dt>当前状态</dt><dd><StatusTag :value="selected.status" /></dd><dt>条码</dt><dd>{{ selected.barcode || '未记录' }}</dd><dt>来源</dt><dd>{{ displayValue('sourceType', selected.dataSource) }}</dd><dt>安装时间</dt><dd>{{ selected.installTime || '尚未安装' }}</dd><dt>关联车辆</dt><dd>{{ selected.vehiclePlateNo || '未关联' }}</dd><dt>关联司机</dt><dd>{{ selected.driverName || '未关联' }}</dd><dt>入库时间</dt><dd>{{ selected.createdAt || '未记录' }}</dd></dl><p class="muted">申请与审批事实请在审批中心查看。</p></template></DetailDrawer>
  </div>
</template>
