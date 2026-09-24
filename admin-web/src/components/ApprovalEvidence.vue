<script setup>
import { onUnmounted, ref } from 'vue';
import { attachmentApi } from '../api/modules.js';
import { detailError } from '../views/approvalDetailModel.js';

defineProps({
  attachments: { type: Array, default: () => [] },
  title: { type: String, required: true }
});

const preview = ref(null);
const loadingId = ref(null);
const error = ref('');
const downloadUrls = new Set();
const rasterTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp']);

function closePreview() {
  if (preview.value) URL.revokeObjectURL(preview.value.url);
  preview.value = null;
}

async function openFile(file) {
  if (loadingId.value !== null) return;
  loadingId.value = file.id;
  error.value = '';
  try {
    const blob = await attachmentApi.download(file.id);
    const url = URL.createObjectURL(blob);
    if (rasterTypes.has(file.contentType || blob.type)) {
      closePreview();
      preview.value = { name: file.originalFilename, url };
    } else {
      downloadUrls.add(url);
      const link = document.createElement('a');
      link.href = url;
      link.download = file.originalFilename || `attachment-${file.id}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => { URL.revokeObjectURL(url); downloadUrls.delete(url); }, 5000);
    }
  } catch (caught) {
    error.value = detailError(caught, '附件');
  } finally {
    loadingId.value = null;
  }
}

onUnmounted(() => {
  closePreview();
  for (const url of downloadUrls) URL.revokeObjectURL(url);
  downloadUrls.clear();
});
</script>

<template>
  <div class="approval-evidence">
    <strong>{{ title }}</strong>
    <p v-if="!attachments.length" class="muted">无附件</p>
    <ul v-else>
      <li v-for="file in attachments" :key="file.id">
        <span>{{ file.originalFilename || `附件 #${file.id}` }}</span>
        <button type="button" :disabled="loadingId !== null" @click="openFile(file)">
          {{ loadingId === file.id ? '读取中…' : rasterTypes.has(file.contentType) ? '预览' : '下载' }}
        </button>
      </li>
    </ul>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="preview" class="approval-preview" role="dialog" aria-label="图片预览">
      <div class="approval-preview-card">
        <div class="approval-preview-head"><strong>{{ preview.name }}</strong><button type="button" @click="closePreview">关闭预览</button></div>
        <img :src="preview.url" :alt="preview.name" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.approval-evidence { display: grid; gap: 8px; }
.approval-evidence ul { margin: 0; padding-left: 20px; }
.approval-evidence li { display: flex; align-items: center; gap: 12px; margin: 6px 0; overflow-wrap: anywhere; }
.approval-preview { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; background: #000b; padding: 24px; }
.approval-preview-card { max-width: min(96vw, 1000px); max-height: 96vh; overflow: auto; background: white; padding: 16px; border-radius: 8px; }
.approval-preview-head { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 12px; }
.approval-preview img { display: block; max-width: 100%; max-height: 80vh; object-fit: contain; }
</style>
