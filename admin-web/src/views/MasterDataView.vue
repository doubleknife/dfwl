<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import { masterApi } from '../api/modules.js';
import { confirmAndRun, useRequest } from '../composables/useRequest.js';
import { can } from '../stores/auth.js';

const { loading, error, run } = useRequest();
const tab = ref('drivers');
const state = reactive({ drivers: [], vehicles: [], trailers: [], customers: [], products: [], history: [] });
const driverForm = reactive({ name: '', phone: '', driverType: 'INTERNAL' });
const vehicleForm = reactive({ plateNo: '', energyType: 'ELECTRIC', maxLoad: '0', loadStandardType: 'PERCENT', loadStandardPercent: '0.9000' });
const trailerForm = reactive({ plateNo: '', insuranceComplete: true });
const customerForm = reactive({ id: '', customerCode: '', customerName: '', status: 1, remark: '' });
const productForm = reactive({ id: '', productCode: '', productName: '', status: 1, remark: '' });
const binding = reactive({ driverId: '', vehicleId: '', trailerId: '' });

async function load() {
  await run(async () => {
    const [drivers, vehicles, trailers, customers, products] = await Promise.all([
      can('driver:list') ? masterApi.drivers() : Promise.resolve({ records: [] }),
      can('vehicle:list') ? masterApi.vehicles() : Promise.resolve({ records: [] }),
      can('trailer:list') ? masterApi.trailers() : Promise.resolve({ records: [] }),
      can('route:list') ? masterApi.customers() : Promise.resolve({ records: [] }),
      can('route:list') ? masterApi.products() : Promise.resolve({ records: [] })
    ]);
    state.drivers = drivers.records || drivers || [];
    state.vehicles = vehicles.records || vehicles || [];
    state.trailers = trailers.records || trailers || [];
    state.customers = customers.records || customers || [];
    state.products = products.records || products || [];
  });
}

async function createDriver() {
  await run(async () => {
    await masterApi.createDriver(driverForm);
    Object.assign(driverForm, { name: '', phone: '', driverType: 'INTERNAL' });
    await load();
  });
}

async function createVehicle() {
  await run(async () => {
    await masterApi.createVehicle({ ...vehicleForm, maxLoad: vehicleForm.maxLoad });
    Object.assign(vehicleForm, { plateNo: '', energyType: 'ELECTRIC', maxLoad: '0', loadStandardType: 'PERCENT', loadStandardPercent: '0.9000' });
    await load();
  });
}

async function createTrailer() {
  await run(async () => {
    await masterApi.createTrailer(trailerForm);
    Object.assign(trailerForm, { plateNo: '', insuranceComplete: true });
    await load();
  });
}

async function bindDriverVehicle() {
  await run(async () => {
    await masterApi.bindDriverVehicle(Number(binding.driverId), { vehicleId: Number(binding.vehicleId) });
    await load();
  });
}

async function unbindDriverVehicle() {
  await confirmAndRun('确认解绑司机当前车辆？', async () => run(async () => {
    await masterApi.unbindDriverVehicle(Number(binding.driverId));
    await load();
  }));
}

async function bindTrailerVehicle() {
  await run(async () => {
    await masterApi.bindTrailerVehicle(Number(binding.trailerId), { vehicleId: Number(binding.vehicleId) });
    await load();
  });
}

async function unbindTrailerVehicle() {
  await confirmAndRun('确认解绑车挂当前车辆？', async () => run(async () => {
    await masterApi.unbindTrailerVehicle(Number(binding.trailerId));
    await load();
  }));
}

async function showVehicleHistory(vehicleId) {
  await run(async () => {
    state.history = await masterApi.vehicleHistory(vehicleId);
  });
}

function editCustomer(row) {
  Object.assign(customerForm, {
    id: row.id,
    customerCode: row.customerCode || '',
    customerName: row.customerName || '',
    status: row.status ?? 1,
    remark: row.remark || ''
  });
}

function resetCustomerForm() {
  Object.assign(customerForm, { id: '', customerCode: '', customerName: '', status: 1, remark: '' });
}

async function saveCustomer() {
  await run(async () => {
    const body = {
      customerCode: customerForm.customerCode || null,
      customerName: customerForm.customerName,
      status: Number(customerForm.status),
      remark: customerForm.remark || null
    };
    if (customerForm.id) {
      await masterApi.updateCustomer(Number(customerForm.id), body);
    } else {
      await masterApi.createCustomer(body);
    }
    resetCustomerForm();
    await load();
  });
}

async function toggleCustomer(row) {
  await confirmAndRun(`确认${row.status === 1 ? '停用' : '启用'}该客户？`, async () => run(async () => {
    await masterApi.setCustomerStatus(row.id, row.status === 1 ? 0 : 1);
    await load();
  }));
}

function editProduct(row) {
  Object.assign(productForm, {
    id: row.id,
    productCode: row.productCode || '',
    productName: row.productName || '',
    status: row.status ?? 1,
    remark: row.remark || ''
  });
}

function resetProductForm() {
  Object.assign(productForm, { id: '', productCode: '', productName: '', status: 1, remark: '' });
}

async function saveProduct() {
  await run(async () => {
    const body = {
      productCode: productForm.productCode || null,
      productName: productForm.productName,
      status: Number(productForm.status),
      remark: productForm.remark || null
    };
    if (productForm.id) {
      await masterApi.updateProduct(Number(productForm.id), body);
    } else {
      await masterApi.createProduct(body);
    }
    resetProductForm();
    await load();
  });
}

async function toggleProduct(row) {
  await confirmAndRun(`确认${row.status === 1 ? '停用' : '启用'}该产品？`, async () => run(async () => {
    await masterApi.setProductStatus(row.id, row.status === 1 ? 0 : 1);
    await load();
  }));
}

onMounted(load);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="tabs">
      <button :class="{ active: tab === 'drivers' }" @click="tab = 'drivers'">司机</button>
      <button :class="{ active: tab === 'vehicles' }" @click="tab = 'vehicles'">车辆</button>
      <button :class="{ active: tab === 'trailers' }" @click="tab = 'trailers'">车挂</button>
      <button :class="{ active: tab === 'binding' }" @click="tab = 'binding'">绑定</button>
      <button :class="{ active: tab === 'customerProduct' }" @click="tab = 'customerProduct'">客户产品</button>
    </div>
    <PanelSection v-if="tab === 'drivers'" title="司机">
      <form v-permission="'driver:add'" class="inline-form" @submit.prevent="createDriver">
        <input v-model.trim="driverForm.name" required aria-label="姓名" />
        <input v-model.trim="driverForm.phone" required aria-label="手机号" />
        <select v-model="driverForm.driverType"><option>INTERNAL</option><option>OUTSOURCED</option></select>
        <button class="primary" :disabled="loading">新增司机</button>
      </form>
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'name', label: '姓名' }, { key: 'phone', label: '手机号' },
        { key: 'driverType', label: '类型' }, { key: 'status', label: '状态' }
      ]" :rows="state.drivers" />
    </PanelSection>
    <PanelSection v-if="tab === 'vehicles'" title="车辆">
      <form v-permission="'vehicle:add'" class="inline-form" @submit.prevent="createVehicle">
        <input v-model.trim="vehicleForm.plateNo" required aria-label="车牌号" />
        <select v-model="vehicleForm.energyType"><option>ELECTRIC</option><option>GAS</option></select>
        <input v-model="vehicleForm.maxLoad" required aria-label="最大载重" />
        <button class="primary" :disabled="loading">新增车辆</button>
      </form>
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'plateNo', label: '车牌' }, { key: 'energyType', label: '能源' },
        { key: 'maxLoad', label: '载重' }, { key: 'status', label: '状态' }, { key: 'actions', label: '操作' }
      ]" :rows="state.vehicles">
        <template #actions="{ row }"><button @click="showVehicleHistory(row.id)">绑定历史</button></template>
      </DataTable>
    </PanelSection>
    <PanelSection v-if="tab === 'trailers'" title="车挂">
      <form v-permission="'trailer:add'" class="inline-form" @submit.prevent="createTrailer">
        <input v-model.trim="trailerForm.plateNo" required aria-label="车挂号" />
        <label><input v-model="trailerForm.insuranceComplete" type="checkbox" /> 保险完整</label>
        <button class="primary" :disabled="loading">新增车挂</button>
      </form>
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'plateNo', label: '车挂号' }, { key: 'insuranceComplete', label: '保险' }, { key: 'status', label: '状态' }
      ]" :rows="state.trailers" />
    </PanelSection>
    <PanelSection v-if="tab === 'binding'" title="绑定与解绑">
      <form class="inline-form" @submit.prevent="bindDriverVehicle">
        <input v-model="binding.driverId" aria-label="司机 ID" />
        <input v-model="binding.vehicleId" aria-label="车辆 ID" />
        <button v-permission="'driver:bindVehicle'" class="primary">司机绑定车辆</button>
        <button v-permission="'driver:unbindVehicle'" type="button" @click="unbindDriverVehicle">司机解绑</button>
      </form>
      <form class="inline-form" @submit.prevent="bindTrailerVehicle">
        <input v-model="binding.trailerId" aria-label="车挂 ID" />
        <input v-model="binding.vehicleId" aria-label="车辆 ID" />
        <button v-permission="'trailer:bindVehicle'" class="primary">车挂绑定车辆</button>
        <button v-permission="'trailer:unbindVehicle'" type="button" @click="unbindTrailerVehicle">车挂解绑</button>
      </form>
      <DataTable :columns="[
        { key: 'id', label: 'ID' }, { key: 'driverId', label: '司机' }, { key: 'vehicleId', label: '车辆' },
        { key: 'trailerId', label: '车挂' }, { key: 'operationTime', label: '时间' }
      ]" :rows="state.history" />
    </PanelSection>
    <PanelSection v-if="tab === 'customerProduct'" title="客户与产品">
      <div class="two-column">
        <section>
          <form v-permission="customerForm.id ? 'route:edit' : 'route:create'" class="form-grid" @submit.prevent="saveCustomer">
            <input v-model.trim="customerForm.customerCode" aria-label="客户编码" placeholder="客户编码" />
            <input v-model.trim="customerForm.customerName" required aria-label="客户名称" placeholder="客户名称" />
            <select v-model.number="customerForm.status" aria-label="客户状态"><option :value="1">启用</option><option :value="0">停用</option></select>
            <input v-model.trim="customerForm.remark" aria-label="客户备注" placeholder="备注" />
            <div class="row-actions">
              <button class="primary" :disabled="loading">{{ customerForm.id ? '保存客户' : '新增客户' }}</button>
              <button type="button" @click="resetCustomerForm">清空</button>
            </div>
          </form>
          <DataTable :columns="[
            { key: 'id', label: 'ID' }, { key: 'customerCode', label: '编码' }, { key: 'customerName', label: '客户' },
            { key: 'status', label: '状态' }, { key: 'remark', label: '备注' }, { key: 'actions', label: '操作' }
          ]" :rows="state.customers">
            <template #status="{ value }">{{ value === 1 ? '启用' : '停用' }}</template>
            <template #actions="{ row }">
              <div class="row-actions">
                <button v-permission="'route:edit'" type="button" @click="editCustomer(row)">编辑</button>
                <button v-permission="'route:edit'" type="button" @click="toggleCustomer(row)">{{ row.status === 1 ? '停用' : '启用' }}</button>
              </div>
            </template>
          </DataTable>
        </section>
        <section>
          <form v-permission="productForm.id ? 'route:edit' : 'route:create'" class="form-grid" @submit.prevent="saveProduct">
            <input v-model.trim="productForm.productCode" aria-label="产品编码" placeholder="产品编码" />
            <input v-model.trim="productForm.productName" required aria-label="产品名称" placeholder="产品名称" />
            <select v-model.number="productForm.status" aria-label="产品状态"><option :value="1">启用</option><option :value="0">停用</option></select>
            <input v-model.trim="productForm.remark" aria-label="产品备注" placeholder="备注" />
            <div class="row-actions">
              <button class="primary" :disabled="loading">{{ productForm.id ? '保存产品' : '新增产品' }}</button>
              <button type="button" @click="resetProductForm">清空</button>
            </div>
          </form>
          <DataTable :columns="[
            { key: 'id', label: 'ID' }, { key: 'productCode', label: '编码' }, { key: 'productName', label: '产品' },
            { key: 'status', label: '状态' }, { key: 'remark', label: '备注' }, { key: 'actions', label: '操作' }
          ]" :rows="state.products">
            <template #status="{ value }">{{ value === 1 ? '启用' : '停用' }}</template>
            <template #actions="{ row }">
              <div class="row-actions">
                <button v-permission="'route:edit'" type="button" @click="editProduct(row)">编辑</button>
                <button v-permission="'route:edit'" type="button" @click="toggleProduct(row)">{{ row.status === 1 ? '停用' : '启用' }}</button>
              </div>
            </template>
          </DataTable>
        </section>
      </div>
    </PanelSection>
  </div>
</template>
