<script setup>
import { ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { request } from '../api.js'
import { clearUser } from '../auth.js'
import { jobStatus, jobStatuses } from '../job-status.js'

const route = useRoute()
const router = useRouter()
const job = ref(null)
const loading = ref(false)
const error = ref('')
let requestNumber = 0
async function loadJob() {
  const number = ++requestNumber
  loading.value = true; error.value = ''; job.value = null
  try {
    const result = await request(`/api/jobs/${route.params.id}`)
    if (number === requestNumber) job.value = result
  } catch (failure) {
    if (number !== requestNumber) return
    if (failure.status === 401) { clearUser(); await router.replace('/login') }
    else error.value = failure.status === 404 ? '岗位不存在或无法访问。' : failure.message
  } finally { if (number === requestNumber) loading.value = false }
}
watch(() => route.params.id, loadJob, { immediate: true })
</script>

<template>
  <section aria-labelledby="detail-heading">
    <RouterLink to="/" class="back-link"><span aria-hidden="true">←</span> 返回岗位列表</RouterLink>
    <p v-if="loading" class="loading-state" role="status">正在加载岗位…</p>
    <div v-else-if="error" class="empty-placeholder" role="alert"><h1 id="detail-heading">无法查看岗位</h1>
      <p>{{ error }}</p><button class="text-button" @click="loadJob">重新加载</button></div>
    <template v-else-if="job">
      <div class="page-heading"><div><p class="eyebrow">{{ job.companyName }}</p>
        <h1 id="detail-heading">{{ job.jobTitle }}</h1><p class="page-description">岗位信息与面试记录入口</p></div></div>
      <div class="detail-layout">
        <article class="info-panel"><div class="panel-topline"><h2>岗位信息</h2></div>
          <dl class="job-facts">
            <div><dt>公司</dt><dd>{{ job.companyName }}</dd></div>
            <div><dt>城市</dt><dd>{{ job.city || '未填写' }}</dd></div>
            <div><dt>薪资</dt><dd>{{ job.salary || '未填写' }}</dd></div>
            <div><dt>来源平台</dt><dd>{{ job.sourcePlatform || '未填写' }}</dd></div>
            <div><dt>原始链接</dt><dd>{{ job.originalUrl || '未填写' }}</dd></div>
            <div><dt>学历要求</dt><dd>{{ job.educationRequirement || '未填写' }}</dd></div>
            <div><dt>截止日期</dt><dd>{{ job.applicationDeadline || '未填写' }}</dd></div>
          </dl>
          <div class="description-block"><h3>岗位职责</h3><p>{{ job.responsibilities || '未填写' }}</p></div>
          <div class="description-block"><h3>任职要求</h3><p>{{ job.requirements || '未填写' }}</p></div>
        </article>
        <aside class="detail-sidebar" aria-label="岗位相关操作">
          <section class="action-panel" aria-labelledby="interview-heading"><span class="section-number">01 / 面试</span>
            <h2 id="interview-heading">面试记录</h2><p>按轮次整理问题、回答要点和复盘。</p>
            <button class="secondary-button" disabled>查看面试记录 ↗</button><small class="muted">阶段 4 开放</small></section>
          <section class="action-panel" aria-labelledby="status-heading"><span class="section-number">02 / 进度</span>
            <h2 id="status-heading">投递状态</h2><label for="job-status" class="field-label">当前状态：{{ jobStatus(job.status).label }}</label>
            <select id="job-status" :value="job.status" disabled aria-describedby="status-help">
              <option v-for="(value, key) in jobStatuses" :key="key" :value="key">{{ value.label }}</option>
            </select><small id="status-help" class="muted">阶段 3 接入状态修改</small></section>
        </aside>
      </div>
    </template>
  </section>
</template>
