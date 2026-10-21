<script setup>
import { ref } from 'vue';
import { attachmentApi } from '../api/modules.js';
import { authStore } from '../stores/auth.js';
import ApprovalEvidence from './ApprovalEvidence.vue';

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  purpose: { type: String, required: true },
  disabled: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue', 'uploading']);
const uploading = ref(false);
const error = ref('');
const imageTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']);

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
      if (!imageTypes.has(file.type) || file.size > 30 * 1024 * 1024)
        throw new Error('请选择不超过 30 MB 的 JPG、PNG、WebP 或 HEIC 图片');
      const body = new FormData();
      body.append('ownerType', 'APPROVAL_UPLOAD');
      body.append('ownerId', String(authStore.user?.id || ''));
      body.append('purpose', props.purpose);
      body.append('file', file);
      const uploaded = await attachmentApi.upload(body);
      uploadedFiles.push(uploaded);
      emit('update:modelValue', [...uploadedFiles]);
    }
  } catch (cause) { error.value = cause.message || '图片上传失败，请重新选择'; }
  finally { uploading.value = false; emit('uploading', false); }
}
</script>

<template>
  <div class="approval-image-upload">
    <label class="ui-form-field">图片凭证（选填）
      <input type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/heif" multiple
        :disabled="disabled || uploading" @change="chooseImages" />
    </label>
    <p v-if="uploading" role="status">正在上传图片…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="modelValue.length" class="approval-image-grid">
      <div v-for="(file, index) in modelValue" :key="file.id" class="approval-image-item">
        <ApprovalEvidence :title="`图片 ${index + 1}`" :attachments="[file]" />
        <button type="button" :disabled="disabled || uploading"
          @click="emit('update:modelValue', modelValue.filter((entry) => entry.id !== file.id))">移除</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.approval-image-upload { display:grid; gap:10px; }
.approval-image-upload p { margin:0; }
.approval-image-grid { display:flex; flex-wrap:wrap; gap:12px; }
.approval-image-item { display:grid; gap:8px; width:142px; padding:8px; border:1px solid var(--ui-border); border-radius:var(--ui-radius-card); }
</style>
