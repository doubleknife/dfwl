<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../components/DataTable.vue';
import PanelSection from '../components/PanelSection.vue';
import { importApi } from '../api/modules.js';
import { confirmAndRun, useRequest } from '../composables/useRequest.js';

const { loading, error, run } = useRequest();
const history = ref([]);
const task = ref(null);
const file = ref(null);
const form = reactive({ businessType: 'ROUTE', originalFileId: '', templateId: '' });

async function loadHistory() {
  await run(async () => {
    const data = await importApi.history();
    history.value = data.records || data || [];
  });
}

async function uploadFile(event) {
  file.value = event.target.files?.[0] || null;
  if (!file.value) return;
  await run(async () => {
    const fd = new FormData();
    fd.append('file', file.value);
    fd.append('ownerType', 'IMPORT');
    fd.append('ownerId', '0');
    fd.append('purpose', 'IMPORT_FILE');
    const attachment = await importApi.uploadFile(fd);
    form.originalFileId = String(attachment.id);
  });
}

async function preview() {
  await run(async () => {
    task.value = await importApi.preview({
      businessType: form.businessType,
      templateId: form.templateId ? Number(form.templateId) : null,
      originalFileId: Number(form.originalFileId)
    });
    await loadHistory();
  });
}

async function commit() {
  await confirmAndRun('确认正式提交导入？提交后将逐行落库。', async () => run(async () => {
    task.value = await importApi.commit(task.value.id);
    await loadHistory();
  }));
}

async function exportFailures(row) {
  await run(async () => {
    const blob = await importApi.failures(row.id);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `import-failures-${row.id}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  });
}

onMounted(loadHistory);
</script>

<template>
  <div class="view-stack">
    <p v-if="error" class="error">{{ error }}</p>
    <PanelSection title="上传 / 预览 / 提交">
      <div class="form-grid">
        <select v-model="form.businessType"><option>ROUTE</option><option>TIRE</option><option>EXPENSE</option><option>SALARY</option></select>
        <input type="file" @change="uploadFile" />
        <input v-model="form.originalFileId" aria-label="IMPORT_FILE 附件 ID" />
        <input v-model="form.templateId" aria-label="导入模板 ID（可选）" />
        <button v-permission="'import:preview'" class="primary" :disabled="loading" @click="preview">预览校验</button>
        <button v-permission="'import:commit'" :disabled="!task || loading" @click="commit">确认 commit</button>
      </div>
      <DataTable v-if="task" :columns="[
        { key: 'rowNo', label: '行号' }, { key: 'previewStatus', label: '预览' }, { key: 'finalStatus', label: '最终' },
        { key: 'businessType', label: '业务' }, { key: 'businessId', label: '业务 ID' }, { key: 'finalErrorMessage', label: '错误' }
      ]" :rows="task.rows || []" />
    </PanelSection>
    <PanelSection title="导入历史">
      <DataTable :columns="[
        { key: 'id', label: '任务' }, { key: 'businessType', label: '类型' }, { key: 'status', label: '状态' },
        { key: 'successCount', label: '成功' }, { key: 'unpublishedCount', label: '未发布' }, { key: 'failureCount', label: '失败' },
        { key: 'actions', label: '操作' }
      ]" :rows="history">
        <template #actions="{ row }"><button v-permission="'import:failure:export'" @click="exportFailures(row)">导出失败</button></template>
      </DataTable>
    </PanelSection>
  </div>
</template>
