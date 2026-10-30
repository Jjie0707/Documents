<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api.js'
import { clearUser } from '../auth.js'

defineProps({ disabled: Boolean })
const router = useRouter()
const quota = ref(null)
const loading = ref(false)
const error = ref('')
let active = true

async function refresh() {
  if (loading.value) return
  loading.value = true
  quota.value = null
  error.value = ''
  try {
    const response = await request('/api/vision/quota')
    if (active) quota.value = response
  } catch (failure) {
    if (!active) return
    if (failure.status === 401) { clearUser(); await router.replace('/login') }
    else error.value = failure.message
  } finally {
    if (active) loading.value = false
  }
}

function resetTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(new Date(value))
}

onMounted(refresh)
onBeforeUnmount(() => { active = false })
</script>

<template>
  <div class="quota-panel" aria-label="每日识别额度" :aria-busy="loading">
    <strong>每日识别额度</strong>
    <div role="status" aria-live="polite">
      <p v-if="loading">正在查询额度…</p>
      <p v-else-if="error">额度查询失败：{{ error }} 可继续检查图片或手动填写。</p>
      <template v-else-if="quota">
        <p v-if="quota.available || quota.reason === 'EXHAUSTED'">
          当前IP今日剩余 {{ quota.remaining }} / {{ quota.dailyLimit }} 次（已使用 {{ quota.used }} 次）。
          <span v-if="quota.reason === 'EXHAUSTED'">今日额度已用完。</span>
        </p>
        <p v-else-if="quota.reason === 'NOT_CONFIGURED'">识别额度尚未配置，可继续检查图片或手动填写。</p>
        <p v-else>识别额度暂不可用，可稍后刷新；图片检查和手动填写仍可使用。</p>
        <p class="muted">重置时间：{{ resetTime(quota.resetAt) }}（北京时间）。刷新和检查图片都不扣次数。</p>
      </template>
    </div>
    <button type="button" class="secondary-button" :disabled="disabled || loading" @click="refresh">刷新额度</button>
  </div>
</template>

<style scoped>
.quota-panel { margin-block: 1rem; padding: 1rem; border: 1px solid var(--border, #d9e2d9); border-radius: 8px; }
.quota-panel p { margin-block: .6rem; overflow-wrap: anywhere; }
</style>
