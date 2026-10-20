<script setup>
import SummaryCards from './SummaryCards.vue';

defineProps({
  title: { type: String, default: '导入完成' },
  summary: { type: Object, required: true },
  unit: { type: String, default: '行' },
  totalLabel: { type: String, default: '本次导入' },
  successLabel: { type: String, default: '成功' },
  note: { type: String, default: '' }
});
</script>

<template>
  <section class="ui-import-completion" :class="{ 'ui-import-completion--failed': summary.failed }" role="status" aria-live="polite">
    <h3>{{ title }}</h3>
    <SummaryCards label="正式导入结果" :items="[
      { label: totalLabel, value: `${summary.total} ${unit}` },
      { label: successLabel, value: `${summary.success} ${unit}` },
      { label: '失败', value: `${summary.failed} ${unit}` },
      ...(summary.pending ? [{ label: '待处理', value: `${summary.pending} ${unit}` }] : [])
    ]" />
    <p v-if="note">{{ note }}</p>
    <div v-if="summary.failed" class="ui-import-completion-reasons">
      <strong>失败原因</strong>
      <ul><li v-for="(failure, index) in summary.failures || []" :key="`${failure.rowNo || 'batch'}-${index}`">
        {{ failure.rowNo ? `Excel 第 ${failure.rowNo} 行：` : '' }}{{ failure.reason }}
      </li></ul>
    </div>
  </section>
</template>

<style scoped>
.ui-import-completion { display:grid; gap:12px; padding:16px; margin:16px 0; border:1px solid #cde4d8; border-radius:12px; background:#f5faf7; }
.ui-import-completion--failed { border-color:#edc8c3; background:#fff8f7; }
.ui-import-completion h3, .ui-import-completion p { margin:0; }
.ui-import-completion-reasons { color:#b14436; }
.ui-import-completion-reasons ul { max-height:180px; overflow:auto; margin:8px 0 0; padding-left:22px; }
.ui-import-completion-reasons li + li { margin-top:6px; }
</style>
