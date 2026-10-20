<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { patch, remove, request } from '../api.js'
import { clearUser } from '../auth.js'
import { jobStatus, jobStatuses } from '../job-status.js'

const route = useRoute()
const router = useRouter()
const job = ref(null)
const loading = ref(false)
const error = ref('')
const selectedStatus = ref('')
const savingStatus = ref(false)
const statusError = ref('')
const statusSuccess = ref('')
const statusUncertain = ref(false)
const confirmingDelete = ref(false)
const deleting = ref(false)
const checkingDelete = ref(false)
const deleteError = ref('')
const deleteNotice = ref('')
const deleteUncertain = ref(false)
const deleteButton = ref(null)
const cancelDeleteButton = ref(null)
const actionsLocked = computed(() => savingStatus.value || confirmingDelete.value || deleting.value
  || statusUncertain.value || deleteUncertain.value || checkingDelete.value)
let requestNumber = 0
let active = true
onBeforeUnmount(() => { active = false; requestNumber++ })

async function loadJob() {
  const number = ++requestNumber
  loading.value = true; error.value = ''; job.value = null
  selectedStatus.value = ''; savingStatus.value = false
  statusError.value = ''; statusSuccess.value = ''; statusUncertain.value = false
  confirmingDelete.value = false; deleting.value = false; checkingDelete.value = false
  deleteError.value = ''; deleteNotice.value = ''; deleteUncertain.value = false
  try {
    const result = await request(`/api/jobs/${route.params.id}`)
    if (number === requestNumber) {
      job.value = result
      selectedStatus.value = result.status
    }
  } catch (failure) {
    if (number !== requestNumber) return
    if (failure.status === 401) { clearUser(); await router.replace('/login') }
    else error.value = failure.status === 404 ? '岗位不存在或无法访问。' : failure.message
  } finally { if (number === requestNumber) loading.value = false }
}

function clearStatusMessages() {
  statusError.value = ''; statusSuccess.value = ''
}

async function saveStatus() {
  if (!job.value || actionsLocked.value || selectedStatus.value === job.value.status) return
  const number = requestNumber
  const id = job.value.id
  const status = selectedStatus.value
  savingStatus.value = true
  clearStatusMessages()
  try {
    const updated = await patch(`/api/jobs/${id}/status`, { status })
    if (!active || number !== requestNumber) return
    // 选择值只是待提交内容；只有后端成功返回才更新当前状态。
    job.value = updated
    selectedStatus.value = updated.status
    statusSuccess.value = `状态已保存为“${jobStatus(updated.status).label}”。`
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 401) {
      clearUser()
      await router.replace('/login')
    } else if (failure.status === 404) {
      job.value = null
      error.value = '岗位不存在或无法访问。'
    } else {
      statusUncertain.value = failure.status === 0
      statusError.value = statusUncertain.value
        ? `${failure.message} 保存结果暂时无法确认，请重新加载岗位核对当前状态。`
        : failure.fields?.status || failure.message
    }
  } finally {
    if (active && number === requestNumber) savingStatus.value = false
  }
}

async function askToDelete() {
  if (!job.value || actionsLocked.value) return
  deleteError.value = ''; deleteNotice.value = ''
  clearStatusMessages()
  confirmingDelete.value = true
  await nextTick()
  cancelDeleteButton.value?.focus()
}

async function cancelDelete() {
  if (deleting.value) return
  confirmingDelete.value = false
  deleteError.value = ''
  await nextTick()
  deleteButton.value?.focus()
}

async function deleteJob() {
  if (!job.value || !confirmingDelete.value || deleting.value || deleteUncertain.value
      || savingStatus.value || statusUncertain.value) return
  const number = requestNumber
  const id = job.value.id
  deleting.value = true
  deleteError.value = ''; deleteNotice.value = ''
  try {
    // 成功响应是 204，没有岗位 JSON；确认完成后再返回列表，由列表重新查询。
    await remove(`/api/jobs/${id}`)
    if (!active || number !== requestNumber) return
    await router.replace({ name: 'jobs' })
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 401) {
      clearUser()
      await router.replace('/login')
    } else if (failure.status === 404) {
      job.value = null
      error.value = '岗位不存在或无法访问，请返回列表核对。'
    } else {
      deleteUncertain.value = failure.status === 0
      if (deleteUncertain.value) confirmingDelete.value = false
      deleteError.value = deleteUncertain.value
        ? `${failure.message} 删除结果暂时无法确认，请先核对结果。`
        : failure.message
    }
  } finally {
    if (active && number === requestNumber) deleting.value = false
  }
}

async function checkDeleteResult() {
  if (!job.value || !deleteUncertain.value || checkingDelete.value) return
  const number = requestNumber
  const id = job.value.id
  checkingDelete.value = true
  deleteError.value = ''; deleteNotice.value = ''
  try {
    const result = await request(`/api/jobs/${id}`)
    if (!active || number !== requestNumber) return
    job.value = result
    selectedStatus.value = result.status
    deleteUncertain.value = false
    deleteNotice.value = '岗位仍然存在，信息已重新加载。如需删除，请重新确认。'
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 401) {
      clearUser()
      await router.replace('/login')
    } else if (failure.status === 404) {
      // 404 也可能代表无权访问，不能据此声称一定是本次删除成功。
      job.value = null
      error.value = '岗位不存在或无法访问，请返回列表核对。'
    } else {
      deleteError.value = `${failure.message} 暂时无法核对删除结果，请重试核对。`
    }
  } finally {
    if (active && number === requestNumber) checkingDelete.value = false
  }
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
        <article class="info-panel"><div class="panel-topline"><h2>岗位信息</h2>
          <button v-if="actionsLocked" type="button" class="secondary-button" disabled>编辑基本信息</button>
          <RouterLink v-else :to="{ name: 'job-edit', params: { id: String(job.id) } }"
                      class="secondary-button">编辑基本信息</RouterLink>
        </div>
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
            <h2 id="status-heading">投递状态</h2>
            <p class="status-current">当前状态：<strong>{{ jobStatus(job.status).label }}</strong></p>
            <form class="status-form" :aria-busy="savingStatus" @submit.prevent="saveStatus">
              <label for="job-status" class="field-label">选择新状态</label>
              <select id="job-status" v-model="selectedStatus" :disabled="actionsLocked"
                      aria-describedby="status-help" @change="clearStatusMessages">
                <option v-for="(value, key) in jobStatuses" :key="key" :value="key">{{ value.label }}</option>
              </select>
              <small id="status-help" class="muted">选择后点击保存，当前状态会在保存成功后更新。</small>
              <p v-if="statusError" class="form-message error" role="alert">{{ statusError }}</p>
              <p v-if="statusSuccess" class="form-message success" role="status">{{ statusSuccess }}</p>
              <button type="submit" class="secondary-button"
                      :disabled="actionsLocked || selectedStatus === job.status">
                {{ savingStatus ? '正在保存…' : '保存状态' }}
              </button>
              <button v-if="statusUncertain" type="button" class="text-button" @click="loadJob">重新加载岗位</button>
            </form>
          </section>
          <section class="action-panel delete-panel" aria-labelledby="delete-heading" :aria-busy="deleting || checkingDelete">
            <span class="section-number">03 / 管理</span>
            <h2 id="delete-heading">删除岗位</h2>
            <p>删除后无法恢复。有关联面试记录的岗位无法直接删除。</p>
            <div v-if="confirmingDelete" class="delete-confirmation" role="group"
                 aria-labelledby="delete-confirm-heading" @keydown.esc.prevent="cancelDelete">
              <h3 id="delete-confirm-heading">确认删除这个岗位？</h3>
              <p class="delete-target">{{ job.companyName }} · {{ job.jobTitle }}</p>
              <div class="delete-actions">
                <button ref="cancelDeleteButton" type="button" class="secondary-button" :disabled="deleting" @click="cancelDelete">取消</button>
                <button type="button" class="danger-button" :disabled="deleting" @click="deleteJob">
                  {{ deleting ? '正在删除…' : '确认删除' }}
                </button>
              </div>
              <p v-if="deleting" class="muted input-help" role="status">正在删除，请稍候。</p>
            </div>
            <button v-else ref="deleteButton" type="button" class="danger-button danger-outline"
                    :disabled="actionsLocked" @click="askToDelete">删除岗位</button>
            <p v-if="deleteError" class="form-message error" role="alert">{{ deleteError }}</p>
            <p v-if="deleteNotice" class="muted delete-notice" role="status">{{ deleteNotice }}</p>
            <button v-if="deleteUncertain" type="button" class="text-button" :disabled="checkingDelete" @click="checkDeleteResult">
              {{ checkingDelete ? '正在核对…' : '核对删除结果' }}
            </button>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>
