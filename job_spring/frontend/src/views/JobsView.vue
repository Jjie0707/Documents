<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { request } from '../api.js'
import { clearUser } from '../auth.js'
import { jobStatus, jobStatuses } from '../job-status.js'

const jobs = ref([])
const loading = ref(true)
const error = ref('')
const keyword = ref('')
const submittedKeyword = ref('')
const appliedKeyword = ref('')
const status = ref('')
const submittedStatus = ref('')
const appliedStatus = ref('')
const keywordError = ref('')
const statusError = ref('')
const searchInput = ref(null)
const statusInput = ref(null)
const sameSearchPending = computed(() => loading.value && keyword.value.trim() === submittedKeyword.value
  && status.value === submittedStatus.value)
const hasAppliedFilters = computed(() => Boolean(appliedKeyword.value || appliedStatus.value))
const router = useRouter()
let requestNumber = 0
let active = true
onBeforeUnmount(() => { active = false; requestNumber++ })

async function loadJobs() {
  const number = ++requestNumber
  const queryKeyword = submittedKeyword.value
  const queryStatus = submittedStatus.value
  loading.value = true
  error.value = ''
  jobs.value = []
  try {
    const query = new URLSearchParams()
    if (queryKeyword) query.set('keyword', queryKeyword)
    if (queryStatus) query.set('status', queryStatus)
    const result = await request(`/api/jobs${query.size ? `?${query}` : ''}`)
    if (!active || number !== requestNumber) return
    jobs.value = result
    appliedKeyword.value = queryKeyword
    appliedStatus.value = queryStatus
    if (keyword.value.trim() === queryKeyword) keywordError.value = ''
    if (status.value === queryStatus) statusError.value = ''
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 401) {
      clearUser()
      await router.replace('/login')
    } else {
      error.value = failure.fields?.keyword || failure.fields?.status || failure.message
      if (failure.fields?.keyword && keyword.value.trim() === queryKeyword) {
        keywordError.value = failure.fields.keyword
        await nextTick()
        searchInput.value?.focus()
      } else if (failure.fields?.status && status.value === queryStatus) {
        statusError.value = failure.fields.status
        await nextTick()
        statusInput.value?.focus()
      }
    }
  } finally {
    if (active && number === requestNumber) loading.value = false
  }
}

async function searchJobs() {
  const value = keyword.value.trim()
  keywordError.value = ''
  statusError.value = ''
  if ([...value].length > 100) {
    keywordError.value = '搜索关键词不能超过100个字符'
    await nextTick()
    searchInput.value?.focus()
    return
  }
  if (loading.value && value === submittedKeyword.value && status.value === submittedStatus.value) return
  keyword.value = value
  submittedKeyword.value = value
  submittedStatus.value = status.value
  await loadJobs()
}

function clearSearch() {
  keyword.value = ''; keywordError.value = ''; status.value = ''; statusError.value = ''
  // 全部列表已在请求中时，清空草稿即可，不重复发送同一次查询。
  if (loading.value && !submittedKeyword.value && !submittedStatus.value) return
  submittedKeyword.value = ''
  submittedStatus.value = ''
  loadJobs()
}

function describeQuery(value, selectedStatus) {
  return [value ? `“${value}”的搜索结果` : '', selectedStatus ? `投递状态：${jobStatus(selectedStatus).label}` : '']
    .filter(Boolean).join(' · ')
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
        <span v-if="!loading && !error" class="muted" role="status">{{ hasAppliedFilters ? '找到 ' : '' }}{{ jobs.length }} 条记录</span>
      </div>

      <form class="job-search" role="search" aria-label="搜索岗位" novalidate @submit.prevent="searchJobs">
        <div class="job-search-controls">
          <div class="job-search-field">
            <label for="job-search-keyword">搜索岗位</label>
            <input id="job-search-keyword" ref="searchInput" v-model="keyword" type="search" name="keyword"
                   placeholder="公司名、岗位名或城市" enterkeyhint="search" autocomplete="off"
                   :aria-invalid="Boolean(keywordError)"
                   :aria-describedby="`job-search-help${keywordError ? ' job-search-error' : ''}`"
                   @input="keywordError = ''" />
          </div>
          <div class="job-status-filter">
            <label for="job-filter-status">投递状态</label>
            <select id="job-filter-status" ref="statusInput" v-model="status" name="status"
                    :aria-invalid="Boolean(statusError)"
                    :aria-describedby="`job-search-help${statusError ? ' job-filter-status-error' : ''}`"
                    @change="statusError = ''">
              <option value="">全部状态</option>
              <option v-for="(item, code) in jobStatuses" :key="code" :value="code">{{ item.label }}</option>
            </select>
          </div>
          <div class="job-search-actions">
            <button type="submit" class="primary-button" :disabled="sameSearchPending">搜索</button>
            <button type="button" class="secondary-button"
                    :disabled="!keyword && !submittedKeyword && !status && !submittedStatus && !keywordError && !statusError"
                    @click="clearSearch">重置条件</button>
          </div>
        </div>
        <p id="job-search-help" class="muted input-help">关键词最多 100 个字符；选择状态后点击搜索，两个条件同时生效。</p>
        <p v-if="keywordError" id="job-search-error" class="field-error" role="alert">{{ keywordError }}</p>
        <p v-if="statusError" id="job-filter-status-error" class="field-error" role="alert">{{ statusError }}</p>
      </form>

      <p v-if="loading" class="loading-state" role="status">{{ submittedKeyword || submittedStatus ? '正在查询岗位…' : '正在加载岗位…' }}</p>
      <div v-else-if="error" class="empty-placeholder" role="alert">
        <h3>岗位暂时无法加载</h3><p>{{ error }}</p>
        <p v-if="submittedKeyword || submittedStatus" class="search-result-label">当前查询：{{ describeQuery(submittedKeyword, submittedStatus) }}</p>
        <button class="text-button" @click="loadJobs()">重新加载</button>
      </div>
      <template v-else>
      <p v-if="hasAppliedFilters" class="muted search-result-label">{{ describeQuery(appliedKeyword, appliedStatus) }}</p>
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

      <div v-if="jobs.length === 0 && hasAppliedFilters" class="empty-placeholder">
        <h3>没有匹配的岗位</h3>
        <p>试试其他关键词或状态，也可以重置条件查看全部岗位。</p>
        <button type="button" class="text-button" @click="clearSearch">查看全部岗位</button>
      </div>
      <div v-else-if="jobs.length === 0" class="empty-placeholder">
        <span class="empty-icon" aria-hidden="true">＋</span>
        <h3>还没有岗位记录</h3>
        <p>点击下方“添加岗位”，记下第一条求职记录。</p>
      </div>
      </template>

      <div class="panel-bottomline">
        <p class="muted">记下感兴趣的岗位，逐步整理求职进度。</p>
        <RouterLink class="primary-button" :to="{ name: 'job-create' }">
          <span aria-hidden="true">＋</span> 添加岗位
        </RouterLink>
      </div>
    </div>
  </section>
</template>
