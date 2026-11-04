<script setup>
import AppIcon from '../components/AppIcon.vue'
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="empty-placeholder">
    <AppIcon name="search" />
    <h1>页面不存在</h1>
    <p>可以返回岗位列表继续浏览。</p>
    <RouterLink to="/" class="back-link">返回岗位列表 <AppIcon name="arrow-right" /></RouterLink>
  </section>
</template>
