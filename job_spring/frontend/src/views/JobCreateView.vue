<script setup>
import { nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '../api.js'
import { clearUser } from '../auth.js'

const router = useRouter()
const formElement = ref(null)
const form = reactive({ companyName: '', jobTitle: '', city: '', salary: '' })
const fields = ref({})
const message = ref('')
const busy = ref(false)
let active = true
onBeforeUnmount(() => { active = false })

const inputs = [
  { key: 'companyName', label: '公司名', required: true, limit: 200, placeholder: '请输入公司名称' },
  { key: 'jobTitle', label: '岗位名', required: true, limit: 200, placeholder: '例如：Java 后端实习' },
  { key: 'city', label: '城市', required: false, limit: 100, placeholder: '例如：上海、远程' },
  { key: 'salary', label: '薪资', required: false, limit: 100, placeholder: '例如：15–20K·13薪、面议' },
]

async function focusInvalidField() {
  await nextTick()
  formElement.value?.querySelector('[aria-invalid="true"]')?.focus()
}

async function submit() {
  if (busy.value) return
  fields.value = {}
  message.value = ''
  for (const input of inputs) {
    const value = form[input.key].trim()
    if (input.required && !value) fields.value[input.key] = `${input.label}不能为空`
    else if ([...value].length > input.limit) fields.value[input.key] = `${input.label}不能超过${input.limit}个字符`
  }
  if (Object.keys(fields.value).length) {
    await focusInvalidField()
    return
  }

  busy.value = true
  try {
    // 只发送四个表单字段；归属、初始状态和主键继续由后端决定。
    const created = await post('/api/jobs', { ...form })
    if (active) await router.replace({ name: 'job-detail', params: { id: String(created.id) } })
  } catch (error) {
    if (!active) return
    if (error.status === 401) {
      clearUser()
      await router.replace('/login')
    } else {
      fields.value = error.fields || {}
      message.value = error.status === 0
        ? `${error.message} 保存结果暂时无法确认，请先返回列表检查是否已新增，避免重复提交。`
        : error.message
    }
  } finally {
    busy.value = false
  }
  if (active && Object.keys(fields.value).length) await focusInvalidField()
}

function cancel() {
  if (!busy.value) router.push({ name: 'jobs' })
}
</script>

<template>
  <section aria-labelledby="create-heading">
    <button type="button" class="back-link form-back" :disabled="busy" @click="cancel">
      <span aria-hidden="true">←</span> 返回岗位列表
    </button>
    <div class="page-heading">
      <div>
        <p class="eyebrow">我的求职记录</p>
        <h1 id="create-heading">添加岗位</h1>
        <p class="page-description">先记下岗位的基本信息，城市和薪资可以留空。</p>
      </div>
    </div>

    <div class="workspace-panel job-create-panel">
      <div class="panel-topline">
        <h2>基本信息</h2>
        <span class="muted"><span class="required-mark" aria-hidden="true">*</span> 为必填项</span>
      </div>
      <form ref="formElement" class="job-form" novalidate :aria-busy="busy" @submit.prevent="submit">
        <fieldset :disabled="busy">
          <legend class="visually-hidden">新增岗位信息</legend>
          <div class="job-form-grid">
            <div v-for="input in inputs" :key="input.key" class="job-form-field">
              <label :for="`job-${input.key}`">
                {{ input.label }}
                <span v-if="input.required" class="required-mark" aria-hidden="true">*</span>
                <span v-else class="optional-label">（选填）</span>
              </label>
              <input :id="`job-${input.key}`" v-model="form[input.key]" :name="input.key"
                     type="text" :required="input.required" :placeholder="input.placeholder"
                     :aria-invalid="Boolean(fields[input.key])"
                     :aria-describedby="`job-${input.key}-help${fields[input.key] ? ` job-${input.key}-error` : ''}`" />
              <p :id="`job-${input.key}-help`" class="muted input-help">最多 {{ input.limit }} 个字符</p>
              <p v-if="fields[input.key]" :id="`job-${input.key}-error`" class="field-error">{{ fields[input.key] }}</p>
            </div>
          </div>
          <p class="muted job-form-note">新岗位会以“待投递”状态保存。</p>
          <p v-if="message" class="form-message error" role="alert">{{ message }}</p>
          <div class="job-form-actions">
            <button type="button" class="secondary-button" @click="cancel">取消</button>
            <button type="submit" class="primary-button">{{ busy ? '正在保存…' : '保存岗位' }}</button>
          </div>
          <p v-if="busy" class="muted input-help" role="status">正在保存，请稍候。</p>
        </fieldset>
      </form>
    </div>
  </section>
</template>
