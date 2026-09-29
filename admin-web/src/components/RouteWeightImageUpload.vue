<script setup>
import { ref } from 'vue';
import { attachmentApi } from '../api/modules.js';
import { weightAdjustUploadBody } from '../views/routeWeightEvidence.js';
import ApprovalEvidence from './ApprovalEvidence.vue';

const props = defineProps({
  taskId: { type: Number, required: true },
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue', 'uploading']);
const uploading = ref(false);
const error = ref('');
const imageTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);

async function chooseImages(event) {
  const files = Array.from(event.target.files || []);
  event.target.value = '';
  if (!files.length || props.disabled || uploading.value) return;
  error.value = '';
  uploading.value = true;
  emit('uploading', true);
  try {
    const uploadedFiles = [...props.modelValue];
    for (const file of files) {
      if (!imageTypes.has(file.type)) throw new Error('请选择 JPG、PNG 或 WebP 图片，确保可以直接预览。');
      if (file.size > 30 * 1024 * 1024) throw new Error('单张图片不能超过 30 MB。');
      const uploaded = await attachmentApi.upload(weightAdjustUploadBody(props.taskId, file));
      uploadedFiles.push(uploaded);
      emit('update:modelValue', [...uploadedFiles]);
    }
  } catch (caught) {
    error.value = caught.message || '图片上传失败，请重新选择图片重试。';
  } finally {
    uploading.value = false;
    emit('uploading', false);
  }
}

function removeImage(id) {
  if (props.disabled || uploading.value) return;
  emit('update:modelValue', props.modelValue.filter((item) => item.id !== id));
  error.value = '';
}
</script>

<template>
  <div class="route-weight-upload">
    <label class="ui-form-field">纠错依据图片（选填）
      <input type="file" accept="image/jpeg,image/png,image/webp" multiple :disabled="disabled || uploading" @change="chooseImages" />
    </label>
    <p class="muted">选择后立即上传；提交纠错前可移除误选图片。已成功上传的图片会保留，上传失败可重新选择。</p>
    <p v-if="uploading" class="muted" role="status">正在上传图片，请稍候…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="modelValue.length" class="route-weight-upload-list">
      <div v-for="(file, index) in modelValue" :key="file.id" class="route-weight-upload-item">
        <ApprovalEvidence :attachments="[file]" :title="`已上传图片 ${index + 1}`" />
        <button type="button" :disabled="disabled || uploading" @click="removeImage(file.id)">移除这张图片</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.route-weight-upload { display: grid; gap: 10px; }
.route-weight-upload p { margin: 0; }
.route-weight-upload-list { display: flex; flex-wrap: wrap; gap: 12px; }
.route-weight-upload-item { width: 142px; display: grid; gap: 8px; padding: 8px; border: 1px solid var(--ui-border); border-radius: var(--ui-radius-card); }
.route-weight-upload-item button { width: 100%; }
</style>
