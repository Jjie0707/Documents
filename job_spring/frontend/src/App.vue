<script setup>
import { ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import ConnectionStatus from './components/ConnectionStatus.vue'
import { logout, user } from './auth.js'

const router = useRouter()
const leaving = ref(false)
const logoutError = ref('')
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
    <header class="site-header">
      <RouterLink to="/" class="brand" aria-label="岗位投递记录主页">
        <span class="brand-mark" aria-hidden="true">职</span>
        <span>岗位投递记录<small>JOB TRACKER</small></span>
      </RouterLink>
      <div v-if="user" class="profile-placeholder" aria-label="当前登录账号">
        <span class="avatar" aria-hidden="true">我</span>
        <span class="account-name">{{ user.username }}<small>当前账号</small></span>
        <button class="text-button" :disabled="leaving" @click="signOut">{{ leaving ? '退出中…' : '退出' }}</button>
      </div>
      <RouterLink v-else to="/login" class="header-login">登录账号</RouterLink>
    </header>
    <p v-if="logoutError" class="form-message error" role="alert">{{ logoutError }}</p>

    <main id="main-content">
      <RouterView />
    </main>

    <footer class="site-footer">
      <span class="phase-label">岗位投递与面试记录</span>
      <ConnectionStatus />
    </footer>
  </div>
</template>
