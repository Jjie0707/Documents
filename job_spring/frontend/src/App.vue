<script setup>
import { RouterLink, RouterView } from 'vue-router'
import ConnectionStatus from './components/ConnectionStatus.vue'
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink to="/" class="brand" aria-label="岗位投递记录主页">
        <span class="brand-mark" aria-hidden="true">职</span>
        <span>岗位投递记录<small>JOB TRACKER</small></span>
      </RouterLink>
      <div class="profile-placeholder" aria-label="个人昵称占位，登录功能尚未实现">
        <span class="avatar" aria-hidden="true">我</span>
        <span>个人昵称<small>登录后显示</small></span>
      </div>
    </header>

    <main id="main-content">
      <RouterView />
    </main>

    <footer class="site-footer">
      <span class="phase-label">阶段 1 · 基础页面与连接验证</span>
      <ConnectionStatus />
    </footer>
  </div>
</template>
