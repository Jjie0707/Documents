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
const pageSize = ref(10)
const submittedPage = ref(1)
const page = ref(1)
const total = ref(0)
const totalPages = ref(0)
const resultSize = ref(10)
const sameSearchPending = computed(() => loading.value && keyword.value.trim() === submittedKeyword.value
  && status.value === submittedStatus.value && submittedPage.value === 1)
const hasAppliedFilters = computed(() => Boolean(appliedKeyword.value || appliedStatus.value))
const router = useRouter()
let requestNumber = 0
let active = true
onBeforeUnmount(() => { active = false; requestNumber++ })

async function loadJobs() {
  const number = ++requestNumber
  const queryKeyword = submittedKeyword.value
  const queryStatus = submittedStatus.value
  const queryPage = submittedPage.value
  const querySize = pageSize.value
  loading.value = true
  error.value = ''
  jobs.value = []
  try {
    const query = new URLSearchParams({ page: String(queryPage), size: String(querySize) })
    if (queryKeyword) query.set('keyword', queryKeyword)
    if (queryStatus) query.set('status', queryStatus)
    const result = await request(`/api/jobs${query.size ? `?${query}` : ''}`)
    if (!active || number !== requestNumber) return
    jobs.value = result.items
    page.value = result.page
    submittedPage.value = result.page
    resultSize.value = result.size
    total.value = result.total
    totalPages.value = result.totalPages
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
      error.value = failure.fields?.keyword || failure.fields?.status || failure.fields?.page || failure.fields?.size || failure.message
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
  if (loading.value && value === submittedKeyword.value && status.value === submittedStatus.value && submittedPage.value === 1) return
  keyword.value = value
  submittedKeyword.value = value
  submittedStatus.value = status.value
  submittedPage.value = 1
  await loadJobs()
}

function clearSearch() {
  keyword.value = ''; keywordError.value = ''; status.value = ''; statusError.value = ''
  // 全部列表已在请求中时，清空草稿即可，不重复发送同一次查询。
  if (loading.value && !submittedKeyword.value && !submittedStatus.value && submittedPage.value === 1) return
  submittedKeyword.value = ''
  submittedStatus.value = ''
  submittedPage.value = 1
  loadJobs()
}

function changePage(target) {
  if (loading.value || target < 1 || target > totalPages.value) return
  submittedPage.value = target
  loadJobs()
}

function changePageSize() {
  submittedPage.value = 1
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
        <span v-if="!loading && !error" class="muted" role="status">共 {{ total }} 条 · 本页 {{ jobs.length }} 条</span>
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
                    :disabled="!keyword && !submittedKeyword && !status && !submittedStatus && !keywordError && !statusError && submittedPage === 1"
                    @click="clearSearch">重置条件</button>
          </div>
        </div>
        <p id="job-search-help" class="muted input-help">关键词最多 100 个字符；选择状态后点击搜索，两个条件同时生效。</p>
        <p v-if="keywordError" id="job-search-error" class="field-error" role="alert">{{ keywordError }}</p>
        <p v-if="statusError" id="job-filter-status-error" class="field-error" role="alert">{{ statusError }}</p>
      </form>

      <div class="job-page-size">
        <label for="job-page-size">每页条数</label>
        <select id="job-page-size" v-model.number="pageSize" :disabled="loading" @change="changePageSize">
          <option :value="10">10 条</option>
          <option :value="20">20 条</option>
          <option :value="50">50 条</option>
        </select>
        <span class="muted">切换条数后回到第 1 页</span>
      </div>

      <p v-if="loading" class="loading-state" role="status">{{ submittedKeyword || submittedStatus ? '正在查询岗位…' : '正在加载岗位…' }}</p>
      <div v-else-if="error" class="empty-placeholder" role="alert">
        <h3>岗位暂时无法加载</h3><p>{{ error }}</p>
        <p v-if="submittedKeyword || submittedStatus" class="search-result-label">当前查询：{{ describeQuery(submittedKeyword, submittedStatus) }}</p>
        <p class="muted">请求第 {{ submittedPage }} 页，每页 {{ pageSize }} 条</p>
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

      <nav v-if="!loading && !error && total > 0" class="job-pagination" aria-label="岗位列表分页">
        <p class="muted" role="status">第 {{ page }} / {{ totalPages }} 页 · 第 {{ (page - 1) * resultSize + 1 }}–{{ (page - 1) * resultSize + jobs.length }} 条</p>
        <div class="job-pagination-actions">
          <button type="button" class="secondary-button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
          <button type="button" class="secondary-button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
        </div>
      </nav>

      <div class="panel-bottomline">
        <p class="muted">记下感兴趣的岗位，逐步整理求职进度。</p>
        <RouterLink class="primary-button" :to="{ name: 'job-create' }">
          <span aria-hidden="true">＋</span> 添加岗位
        </RouterLink>
      </div>
    </div>
  </section>
</template>
