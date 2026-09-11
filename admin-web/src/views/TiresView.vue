<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import { tireApi } from '../api/modules.js';
import { useRequest } from '../composables/useRequest.js';

const { error, run } = useRequest();
const tires = ref([]);
const requestDetail = ref(null);
const requestForm = reactive({ driverId: '', vehicleId: '', itemsJson: '[]' });
const ocrForm = reactive({ ocrId: '', confirmedTireNo: '' });

async function load() {
  await run(async () => {
    const data = await tireApi.list();
    tires.value = data.records || data || [];
  });
}

async function createRequest() {
  await run(async () => {
    requestDetail.value = await tireApi.request({
      driverId: Number(requestForm.driverId),
      vehicleId: Number(requestForm.vehicleId),
      items: JSON.parse(requestForm.itemsJson)
    });
    await load();
  });
}

async function showRequest() {
  await run(async () => {
    requestDetail.value = await tireApi.requestDetail(Number(requestForm.requestId));
  });
}

async function confirmOcr() {
  await run(async () => {
    requestDetail.value = await tireApi.confirmOcr(Number(ocrForm.ocrId), { confirmedTireNo: ocrForm.confirmedTireNo });
  });
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <PanelSection title="轮胎库存 / 历史已使用">
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'tireNo', label: '胎号' }, { key: 'status', label: '状态' },
        { key: 'vehicleId', label: '车辆' }, { key: 'driverId', label: '司机' }, { key: 'installTime', label: '安装时间' }
      ]" :rows="tires" />
    </PanelSection>
    <div class="two-column">
      <PanelSection title="换胎申请">
        <form v-permission="'tire:request'" class="form-grid" @submit.prevent="createRequest">
          <input v-model="requestForm.driverId" aria-label="司机 ID" />
          <input v-model="requestForm.vehicleId" aria-label="车辆 ID" />
          <textarea v-model="requestForm.itemsJson" rows="6" aria-label='[{"tireId":1,"confirmedTireNo":"T-001"}]'></textarea>
          <button class="primary">提交申请</button>
        </form>
      </PanelSection>
      <PanelSection title="申请详情 / OCR">
        <form class="inline-form" @submit.prevent="showRequest">
          <input v-model="requestForm.requestId" aria-label="申请 ID" />
          <button>查询申请</button>
        </form>
        <form class="inline-form" @submit.prevent="confirmOcr">
          <input v-model="ocrForm.ocrId" aria-label="OCR ID" />
          <input v-model="ocrForm.confirmedTireNo" aria-label="确认胎号" />
          <button>确认 OCR</button>
        </form>
        <pre v-if="requestDetail" class="json-box">{{ requestDetail }}</pre>
      </PanelSection>
    </div>
  </div>
</template>
