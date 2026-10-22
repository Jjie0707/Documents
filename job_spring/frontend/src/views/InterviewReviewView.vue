<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import InterviewQuestionsPanel from '../components/InterviewQuestionsPanel.vue'
import { request } from '../api.js'
import { clearUser } from '../auth.js'

const route = useRoute()
const router = useRouter()
const job = ref(null)
const round = ref(null)
const loading = ref(false)
const error = ref('')
const questionBusy = ref(false)
const returnTo = computed(() => ({ name: 'job-detail', params: { id: route.params.id }, query: { roundId: route.params.roundId } }))
let active = true
let requestNumber = 0
onBeforeUnmount(() => { active = false; requestNumber++ })

async function loadReview() {
  const number = ++requestNumber
  const jobId = route.params.id
  const roundId = route.params.roundId
  loading.value = true; error.value = ''; job.value = null; round.value = null; questionBusy.value = false
  try {
    const [jobResult, roundsResult] = await Promise.all([
      request(`/api/jobs/${jobId}`),
      request(`/api/jobs/${jobId}/interview-rounds?roundId=${roundId}`),
    ])
    if (!active || number !== requestNumber) return
    // roundId 定位失败时分页接口会回退到普通页，必须精确核对目标，不能展示另一轮的问题入口。
    const target = roundsResult.items.find(item => String(item.id) === roundId)
    if (!target) { error.value = '面试轮次不存在或无法访问。'; return }
    job.value = jobResult; round.value = target
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 401) { clearUser(); await router.replace('/login') }
    else error.value = failure.status === 404 ? '岗位或面试轮次不存在或无法访问。' : failure.message
  } finally {
    if (active && number === requestNumber) loading.value = false
  }
}

function missingRound() {
  requestNumber++
  round.value = null; questionBusy.value = false
  error.value = '面试轮次不存在或无法访问。'
}

watch(() => [route.params.id, route.params.roundId], loadReview, { immediate: true })
</script>

<template>
  <main class="interview-review-page" aria-labelledby="review-heading">
    <button v-if="questionBusy" type="button" class="back-link form-back" disabled>返回岗位详情</button>
    <RouterLink v-else :to="returnTo" class="back-link">返回岗位详情</RouterLink>
    <p v-if="loading" class="loading-state" role="status">正在加载面试回顾…</p>
    <div v-else-if="error" class="empty-placeholder" role="alert">
      <h1 id="review-heading">无法查看面试回顾</h1>
      <p>{{ error }}</p><button type="button" class="text-button" @click="loadReview">重新加载面试回顾</button>
    </div>
    <template v-else-if="job && round">
      <div class="page-heading"><div>
        <p class="eyebrow">{{ job.companyName }}</p>
        <h1 id="review-heading">面试回顾</h1>
        <p class="page-description">{{ job.jobTitle }} · 第 {{ round.roundNo }} 轮<span v-if="round.roundType"> · {{ round.roundType }}</span></p>
      </div></div>
      <div class="workspace-panel interview-review-panel">
        <InterviewQuestionsPanel :key="`${job.id}-${round.id}`" :round-id="round.id"
                                 @busy="questionBusy = $event" @missing="missingRound" />
      </div>
    </template>
  </main>
</template>
