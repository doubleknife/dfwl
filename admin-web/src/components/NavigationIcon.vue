<script setup>
defineProps({ name: { type: String, required: true } });
</script>

<template>
  <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === 'workbench'"><path d="m3 10 9-7 9 7v10H3z"/><path d="M9 20v-7h6v7"/></template>
    <template v-else-if="name === 'transport'"><path d="M2 7h12v10H2zM14 10h4l4 4v3h-8z"/><circle cx="6" cy="18" r="2"/><circle cx="18" cy="18" r="2"/></template>
    <template v-else-if="name === 'master'"><circle cx="8" cy="8" r="3"/><path d="M2 20v-2a6 6 0 0 1 12 0v2M17 10a3 3 0 1 0 0-6M16 13a6 6 0 0 1 6 6v1"/></template>
    <template v-else-if="name === 'expense-domain'"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/></template>
    <template v-else-if="name === 'approvals'"><path d="M4 3h12l4 4v14H4zM16 3v5h4M8 13l2 2 5-5"/></template>
    <template v-else-if="name === 'wages'"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h5M8 16h8"/></template>
    <template v-else-if="name === 'finance'"><path d="M3 20h18M5 16v-6M10 16V7M15 16v-4M20 16V4"/><circle cx="5" cy="6" r="2"/></template>
    <template v-else><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.9 4.9 7 7M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1"/></template>
  </svg>
</template>
