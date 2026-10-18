<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const state = ref('checking')
let controller
let timeout

const message = computed(() => ({
  checking: '正在检查后端连接…',
  up: '后端与数据库已连通',
  down: '后端或数据库暂未就绪',
}[state.value]))

async function checkConnection() {
  controller?.abort()
  controller = new AbortController()
  state.value = 'checking'
  timeout = window.setTimeout(() => controller.abort(), 8000)

  try {
    const response = await fetch('/api/health', {
      signal: controller.signal,
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) throw new Error('Health check failed')
    const result = await response.json()
    state.value = result.status === 'UP' && result.database === 'UP' ? 'up' : 'down'
  } catch {
    state.value = 'down'
  } finally {
    window.clearTimeout(timeout)
  }
}

onMounted(checkConnection)
onUnmounted(() => {
  controller?.abort()
  window.clearTimeout(timeout)
})
</script>

<template>
  <div class="connection-status">
    <span class="connection-message" role="status" aria-live="polite">
      <span class="connection-dot" :class="state" aria-hidden="true"></span>
      {{ message }}
    </span>
    <button class="text-button" :disabled="state === 'checking'" @click="checkConnection">
      重新检测
    </button>
    <span v-if="state === 'down'" class="connection-help">请确认 IDEA 中的后端已启动。</span>
  </div>
</template>
