<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { post } from '../api.js'
import { login } from '../auth.js'

const route = useRoute()
const router = useRouter()
const registering = computed(() => route.name === 'register')
const username = ref('')
const password = ref('')
const busy = ref(false)
const message = ref('')
const fields = ref({})
const success = ref('')
watch(() => route.name, () => {
  password.value = ''; message.value = ''; fields.value = {}; success.value = ''
})

async function submit() {
  if (busy.value) return
  message.value = ''; fields.value = {}; success.value = ''
  if (registering.value) {
    if ([...username.value.trim()].length > 50) { fields.value.username = '用户名不能超过50个字符'; return }
    if ([...password.value].length < 8 || new TextEncoder().encode(password.value).length > 72) {
      fields.value.password = '密码至少8个字符，且 UTF-8 编码不超过72字节'; return
    }
  }
  busy.value = true
  try {
    if (registering.value) {
      await post('/api/auth/register', { username: username.value, password: password.value })
      password.value = ''
      await router.replace('/login')
      success.value = '注册成功，请使用刚才的账号登录。'
    } else {
      await login(username.value, password.value)
      password.value = ''
      await router.replace('/')
    }
  } catch (error) { message.value = error.message; fields.value = error.fields || {} }
  finally { busy.value = false }
}
</script>

<template>
  <section class="auth-layout" aria-labelledby="auth-heading">
    <div class="auth-intro"><p class="eyebrow">我的求职记录</p>
      <h1>把岗位和面试，<br />记录在一起。</h1>
      <p class="page-description">登录后查看自己的投递记录，<br />为后续的面试复盘留下线索。</p>
      <div class="auth-intro-note"><span aria-hidden="true">↗</span> 手机和电脑，使用同一个账号。</div>
    </div>
    <div class="auth-panel"><span class="section-number">{{ registering ? '创建账号' : '欢迎回来' }}</span>
      <h2 id="auth-heading">{{ registering ? '注册' : '登录' }}</h2>
      <p class="muted">{{ registering ? '创建一个属于你的记录空间。' : '继续查看你的岗位投递记录。' }}</p>
      <form class="auth-form" @submit.prevent="submit"><fieldset :disabled="busy">
        <label for="username">用户名</label>
        <input id="username" v-model="username" name="username" autocomplete="username" required
               autocapitalize="none" :spellcheck="false" :aria-invalid="Boolean(fields.username)"
               :aria-describedby="fields.username ? 'username-error' : undefined" />
        <p v-if="fields.username" id="username-error" class="field-error">{{ fields.username }}</p>
        <label for="password">密码</label>
        <input id="password" v-model="password" type="password" name="password" required
               :autocomplete="registering ? 'new-password' : 'current-password'"
               :aria-invalid="Boolean(fields.password)" :aria-describedby="registering ? 'password-help password-error' : undefined" />
        <p v-if="registering" id="password-help" class="muted input-help">至少8个字符，最多72个 UTF-8 字节；常用汉字通常占3字节。</p>
        <p v-if="fields.password" id="password-error" class="field-error">{{ fields.password }}</p>
        <p v-if="message" class="form-message error" role="alert">{{ message }}</p>
        <p v-if="success" class="form-message success" role="status">{{ success }}</p>
        <button class="primary-button auth-submit" type="submit" :disabled="busy">{{ busy ? '正在提交…' : registering ? '注册账号' : '登录' }}</button>
      </fieldset></form>
      <p class="auth-switch">{{ registering ? '已有账号？' : '还没有账号？' }}
        <RouterLink :to="registering ? '/login' : '/register'">{{ registering ? '去登录' : '创建账号' }}</RouterLink></p>
    </div>
  </section>
</template>
