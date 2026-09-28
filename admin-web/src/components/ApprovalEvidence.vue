<script setup>
import { onUnmounted, ref, watch } from 'vue';
import { attachmentApi } from '../api/modules.js';
import { detailError } from '../views/approvalDetailModel.js';

const props = defineProps({
  attachments: { type: Array, default: () => [] },
  title: { type: String, required: true },
  thumbnails: { type: Boolean, default: true }
});

const preview = ref(null);
const previewFailed = ref(false);
const loadingId = ref(null);
const error = ref('');
const thumbnailUrls = ref({});
const thumbnailErrors = ref({});
const downloadUrls = new Set();
const rasterTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp']);
let thumbnailVersion = 0;

function clearThumbnails() {
  for (const url of Object.values(thumbnailUrls.value)) URL.revokeObjectURL(url);
  thumbnailUrls.value = {};
  thumbnailErrors.value = {};
}

async function loadThumbnail(file, version = thumbnailVersion) {
  if (!rasterTypes.has(file.contentType)) return;
  thumbnailErrors.value = { ...thumbnailErrors.value, [file.id]: '' };
  try {
    const blob = await attachmentApi.download(file.id);
    const url = URL.createObjectURL(blob);
    if (version !== thumbnailVersion) { URL.revokeObjectURL(url); return; }
    thumbnailUrls.value = { ...thumbnailUrls.value, [file.id]: url };
  } catch (caught) {
    if (version === thumbnailVersion) {
      thumbnailErrors.value = { ...thumbnailErrors.value, [file.id]: detailError(caught, '图片') };
    }
  }
}

watch([() => props.attachments, () => props.thumbnails], ([files]) => {
  thumbnailVersion += 1;
  clearThumbnails();
  if (props.thumbnails) files.forEach((file) => { void loadThumbnail(file); });
}, { immediate: true });

function closePreview() {
  if (preview.value?.ownedUrl) URL.revokeObjectURL(preview.value.url);
  preview.value = null;
  previewFailed.value = false;
}

function thumbnailImageFailed(id) {
  const url = thumbnailUrls.value[id];
  if (url) URL.revokeObjectURL(url);
  const nextUrls = { ...thumbnailUrls.value };
  delete nextUrls[id];
  thumbnailUrls.value = nextUrls;
  thumbnailErrors.value = { ...thumbnailErrors.value, [id]: '图片无法显示' };
  if (preview.value?.url === url) closePreview();
}

function openThumbnail(file) {
  const url = thumbnailUrls.value[file.id];
  if (!url) return;
  closePreview();
  preview.value = { name: props.title, url, ownedUrl: false };
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
      preview.value = { name: file.originalFilename, url, ownedUrl: true };
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
  thumbnailVersion += 1;
  closePreview();
  clearThumbnails();
  for (const url of downloadUrls) URL.revokeObjectURL(url);
  downloadUrls.clear();
});
</script>

<template>
  <div class="approval-evidence">
    <strong>{{ title }}{{ thumbnails && attachments.length ? ` · ${attachments.length} 张` : '' }}</strong>
    <p v-if="!attachments.length" class="muted">无附件</p>
    <div v-else-if="thumbnails" class="approval-thumbnails">
      <div v-for="file in attachments" :key="file.id" class="approval-thumbnail">
        <button v-if="!rasterTypes.has(file.contentType)" type="button" class="approval-thumbnail-file" :disabled="loadingId !== null" @click="openFile(file)">{{ file.originalFilename || `附件 #${file.id}` }} · {{ loadingId === file.id ? '读取中…' : '下载' }}</button>
        <button v-else-if="thumbnailUrls[file.id]" type="button" class="approval-thumbnail-button" :aria-label="`放大查看${title}`" @click="openThumbnail(file)">
          <img :src="thumbnailUrls[file.id]" :alt="title" @error="thumbnailImageFailed(file.id)" />
        </button>
        <button v-else-if="thumbnailErrors[file.id]" type="button" class="approval-thumbnail-retry" @click="loadThumbnail(file)">预览失败，重试</button>
        <span v-else class="muted">正在加载预览…</span>
      </div>
    </div>
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
        <p v-if="previewFailed" class="approval-preview-error" role="alert">图片无法显示，请关闭后重试。</p>
        <img v-else :src="preview.url" :alt="preview.name" @error="previewFailed = true" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.approval-evidence { display: grid; gap: 8px; }
.approval-evidence ul { margin: 0; padding-left: 20px; }
.approval-evidence li { display: flex; align-items: center; gap: 12px; margin: 6px 0; overflow-wrap: anywhere; }
.approval-thumbnails { display: grid; grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); gap: 10px; }
.approval-thumbnail { min-height: 104px; border-radius: 8px; background: #f4f8f7; display: grid; place-items: center; overflow: hidden; }
.approval-thumbnail-button { display: block; width: 100%; height: 104px; padding: 0; border: 0; cursor: zoom-in; background: transparent; }
.approval-thumbnail-button img { width: 100%; height: 100%; object-fit: cover; display: block; }
.approval-thumbnail-file { width: 100%; padding: 8px; overflow-wrap: anywhere; }
.approval-thumbnail-retry { padding: 8px; }
.approval-preview { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; background: #000b; padding: 24px; }
.approval-preview-card { max-width: min(96vw, 1000px); max-height: 96vh; overflow: auto; background: white; padding: 16px; border-radius: 8px; }
.approval-preview-head { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 12px; }
.approval-preview img { display: block; max-width: 100%; max-height: 80vh; object-fit: contain; }
.approval-preview-error { padding: 36px; color: var(--ui-danger); }
</style>
