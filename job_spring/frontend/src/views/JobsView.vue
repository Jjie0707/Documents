<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { request } from '../api.js'
import { clearUser } from '../auth.js'
import { jobStatus } from '../job-status.js'

const jobs = ref([])
const loading = ref(true)
const error = ref('')
const router = useRouter()
async function loadJobs() {
  loading.value = true
  error.value = ''
  jobs.value = []
  try {
    jobs.value = await request('/api/jobs')
  } catch (failure) {
    if (failure.status === 401) {
      clearUser()
      await router.replace('/login')
    } else {
      error.value = failure.message
    }
  } finally {
    loading.value = false
  }
}
onMounted(loadJobs)
</script>

<template>
  <section aria-labelledby="jobs-heading">
    <div class="page-heading">
      <div>
        <p class="eyebrow">我的求职记录</p>
        <h1 id="jobs-heading">岗位投递</h1>
        <p class="page-description">查看属于你的岗位记录与投递进度。</p>
      </div>
    </div>

    <div class="workspace-panel">
      <div class="panel-topline">
        <h2>岗位记录</h2>
        <span v-if="!loading && !error" class="muted">{{ jobs.length }} 条记录</span>
      </div>

      <p v-if="loading" class="loading-state" role="status">正在加载岗位…</p>
      <div v-else-if="error" class="empty-placeholder" role="alert">
        <h3>岗位暂时无法加载</h3><p>{{ error }}</p>
        <button class="text-button" @click="loadJobs">重新加载</button>
      </div>
      <template v-else>
      <RouterLink v-for="job in jobs" :key="job.id" :to="`/jobs/${job.id}`" class="job-card">
        <div class="job-card-content">
          <p class="company-name">{{ job.companyName }}</p>
          <h3>{{ job.jobTitle }}</h3>
          <p class="job-meta">{{ job.city || '城市未填写' }} <span aria-hidden="true">·</span> {{ job.salary || '薪资未填写' }}</p>
          <span class="card-hint">查看岗位详情 <span aria-hidden="true">↗</span></span>
        </div>
        <div class="job-status">
          <span class="status-dot" :style="{ background: jobStatus(job.status).color }" aria-hidden="true"></span>
          <span>{{ jobStatus(job.status).label }}</span>
        </div>
      </RouterLink>

      <div v-if="jobs.length === 0" class="empty-placeholder">
        <span class="empty-icon" aria-hidden="true">＋</span>
        <h3>还没有岗位记录</h3>
        <p>你的记录会显示在这里。新增岗位将在下一阶段接入。</p>
      </div>
      </template>

      <div class="panel-bottomline">
        <p class="muted">当前可查看已有记录，新增及编辑功能尚未开放。</p>
        <button class="primary-button" disabled title="阶段 3 实现新增岗位">
          <span aria-hidden="true">＋</span> 添加岗位
        </button>
      </div>
    </div>
  </section>
</template>
