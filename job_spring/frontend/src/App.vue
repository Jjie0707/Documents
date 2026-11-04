<script setup>
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import ConnectionStatus from './components/ConnectionStatus.vue'
import AppIcon from './components/AppIcon.vue'
import { logout, user } from './auth.js'

const route = useRoute()
const router = useRouter()
const leaving = ref(false)
const logoutError = ref('')
const isJobs = computed(() => ['jobs', 'job-detail', 'job-edit', 'interview-review'].includes(route.name))
const isCreate = computed(() => route.name === 'job-create')
const isAuthPage = computed(() => ['login', 'register'].includes(route.name))
const initial = computed(() => (user.value?.username || '我').slice(0, 1).toUpperCase())

async function signOut() {
  leaving.value = true
  logoutError.value = ''
  try {
    await logout()
    await router.replace('/login')
  } catch (error) {
    logoutError.value = error.message
  } finally {
    leaving.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <a href="#main-content" class="skip-link">跳至主要内容</a>

    <aside class="app-sidebar">
      <RouterLink to="/" class="brand" aria-label="岗位投递记录主页">
        <span class="brand-mark" aria-hidden="true"><AppIcon name="briefcase" /></span>
        <span class="brand-text">岗位投递记录<span class="brand-en">JOB TRACKER</span></span>
      </RouterLink>

      <nav v-if="!isAuthPage" class="app-nav" aria-label="主导航">
        <RouterLink to="/" class="nav-item" :class="{ active: isJobs }">
          <AppIcon name="list" /><span>岗位投递</span>
        </RouterLink>
        <RouterLink to="/jobs/new" class="nav-item" :class="{ active: isCreate }">
          <AppIcon name="plus" /><span>添加岗位</span>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <ConnectionStatus />
        <div v-if="user" class="account" aria-label="当前登录账号">
          <span class="avatar" aria-hidden="true">{{ initial }}</span>
          <span class="account-meta">
            <span class="account-name" :title="user.username">{{ user.username }}</span>
            <span class="account-note">当前账号</span>
          </span>
          <button class="text-button" :disabled="leaving" @click="signOut">{{ leaving ? '…' : '退出' }}</button>
        </div>
        <RouterLink v-else-if="!isAuthPage" to="/login" class="header-login">登录账号</RouterLink>
      </div>
    </aside>

    <div class="app-main">
      <p v-if="logoutError" class="form-message error" role="alert" style="margin: 16px 48px 0">{{ logoutError }}</p>

      <main id="main-content" tabindex="-1">
        <RouterView />
      </main>

      <footer class="site-footer">
        <span class="phase-label">岗位投递与面试记录</span>
      </footer>
    </div>
  </div>
</template>
