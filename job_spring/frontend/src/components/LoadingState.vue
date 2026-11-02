<script setup>
defineProps({ label: { type: String, default: '正在加载…' } })
</script>

<template>
  <div class="loading-state" role="status" aria-live="polite">
    <div class="loading-skeleton" aria-hidden="true"><span></span><span></span><span></span></div>
    <p>{{ label }}</p>
  </div>
</template>
