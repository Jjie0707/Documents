<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { put, request } from '../api.js'
import { clearUser } from '../auth.js'

const route = useRoute()
const router = useRouter()
const job = ref(null)
const formElement = ref(null)
const form = reactive({ companyName: '', jobTitle: '', city: '', salary: '' })
const loading = ref(false)
const loadError = ref('')
const fields = ref({})
const message = ref('')
const busy = ref(false)
const uncertain = ref(false)
let requestNumber = 0
let active = true
onBeforeUnmount(() => { active = false; requestNumber++ })

const inputs = [
  { key: 'companyName', label: '公司名', required: true, limit: 200 },
  { key: 'jobTitle', label: '岗位名', required: true, limit: 200 },
  { key: 'city', label: '城市', required: false, limit: 100 },
  { key: 'salary', label: '薪资', required: false, limit: 100 },
]
const hasChanges = computed(() => job.value && inputs.some(input =>
  form[input.key] !== (job.value[input.key] ?? '')))

async function loadJob() {
  const number = ++requestNumber
  const id = route.params.id
  loading.value = true; loadError.value = ''; job.value = null
  fields.value = {}; message.value = ''; busy.value = false; uncertain.value = false
  try {
    const result = await request(`/api/jobs/${id}`)
    if (!active || number !== requestNumber) return
    job.value = result
    // 仅将允许编辑的四个字段放入表单；null 在输入框中显示为空字符串。
    for (const input of inputs) form[input.key] = result[input.key] ?? ''
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 401) { clearUser(); await router.replace('/login') }
    else loadError.value = failure.status === 404 ? '岗位不存在或无法访问。' : failure.message
  } finally {
    if (active && number === requestNumber) loading.value = false
  }
}

async function focusInvalidField() {
  await nextTick()
  formElement.value?.querySelector('[aria-invalid="true"]')?.focus()
}

async function submit() {
  if (!job.value || loading.value || busy.value || uncertain.value || !hasChanges.value) return
  fields.value = {}; message.value = ''
  for (const input of inputs) {
    const value = form[input.key].trim()
    if (input.required && !value) fields.value[input.key] = `${input.label}不能为空`
    else if ([...value].length > input.limit) fields.value[input.key] = `${input.label}不能超过${input.limit}个字符`
  }
  if (Object.keys(fields.value).length) {
    await focusInvalidField()
    return
  }

  const number = requestNumber
  const id = job.value.id
  busy.value = true
  try {
    // PUT 提交四个字段的完整目标值，选填项留空会清空原值；不发送状态或用户 ID。
    const updated = await put(`/api/jobs/${id}`, { ...form })
    if (!active || number !== requestNumber) return
    await router.replace({ name: 'job-detail', params: { id: String(updated.id) } })
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 401) {
      clearUser()
      await router.replace('/login')
    } else if (failure.status === 404) {
      job.value = null
      loadError.value = '岗位不存在或无法访问。'
    } else {
      fields.value = failure.fields || {}
      uncertain.value = failure.status === 0
      message.value = uncertain.value
        ? `${failure.message} 保存结果暂时无法确认，请重新加载已保存信息后核对。`
        : failure.message
    }
  } finally {
    if (active && number === requestNumber) busy.value = false
  }
  if (active && number === requestNumber && Object.keys(fields.value).length) await focusInvalidField()
}

function cancel() {
  if (!busy.value) router.push({ name: 'job-detail', params: { id: route.params.id } })
}

watch(() => route.params.id, loadJob, { immediate: true })
</script>

<template>
  <section aria-labelledby="edit-heading">
    <button type="button" class="back-link form-back" :disabled="busy" @click="cancel">
      <span aria-hidden="true">←</span> 返回岗位详情
    </button>
    <div class="page-heading">
      <div>
        <p class="eyebrow">我的求职记录</p>
        <h1 id="edit-heading">编辑岗位</h1>
        <p class="page-description">修改基本信息，保存后返回岗位详情。</p>
      </div>
    </div>
    <p v-if="loading" class="loading-state" role="status">正在加载岗位…</p>
    <div v-else-if="loadError" class="empty-placeholder" role="alert">
      <h2>无法编辑岗位</h2>
      <p>{{ loadError }}</p>
      <button type="button" class="text-button" @click="loadJob">重新加载</button>
      <RouterLink to="/" class="text-button">返回岗位列表</RouterLink>
    </div>
    <div v-else-if="job" class="workspace-panel job-create-panel">
      <div class="panel-topline">
        <h2>基本信息</h2>
        <span class="muted"><span class="required-mark" aria-hidden="true">*</span> 为必填项</span>
      </div>
      <form ref="formElement" class="job-form" novalidate :aria-busy="busy" @submit.prevent="submit">
        <fieldset :disabled="busy || uncertain">
          <legend class="visually-hidden">编辑岗位基本信息</legend>
          <div class="job-form-grid">
            <div v-for="input in inputs" :key="input.key" class="job-form-field">
              <label :for="`edit-${input.key}`">
                {{ input.label }}
                <span v-if="input.required" class="required-mark" aria-hidden="true">*</span>
                <span v-else class="optional-label">（选填）</span>
              </label>
              <input :id="`edit-${input.key}`" v-model="form[input.key]" :name="input.key"
                     type="text" :required="input.required" :aria-invalid="Boolean(fields[input.key])"
                     :aria-describedby="`edit-${input.key}-help${fields[input.key] ? ` edit-${input.key}-error` : ''}`" />
              <p :id="`edit-${input.key}-help`" class="muted input-help">最多 {{ input.limit }} 个字符</p>
              <p v-if="fields[input.key]" :id="`edit-${input.key}-error`" class="field-error">{{ fields[input.key] }}</p>
            </div>
          </div>
          <p class="muted job-form-note">城市和薪资留空会清除原值。</p>
        </fieldset>
        <p v-if="message" class="form-message error" role="alert">{{ message }}</p>
        <div v-if="uncertain" class="job-edit-recovery">
          <p class="muted">重新加载会用服务器已保存的内容替换表单。</p>
          <button type="button" class="text-button" @click="loadJob">重新加载已保存信息</button>
        </div>
        <div class="job-form-actions">
          <button type="button" class="secondary-button" :disabled="busy" @click="cancel">取消</button>
          <button type="submit" class="primary-button" :disabled="busy || uncertain || !hasChanges">
            {{ busy ? '正在保存…' : '保存修改' }}
          </button>
        </div>
        <p v-if="busy" class="muted input-help" role="status">正在保存，请稍候。</p>
      </form>
    </div>
  </section>
</template>
