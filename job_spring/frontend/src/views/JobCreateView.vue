<script setup>
import { nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '../api.js'
import { clearUser } from '../auth.js'
import ScreenshotUploadPanel from '../components/ScreenshotUploadPanel.vue'

const router = useRouter()
const formElement = ref(null)
const form = reactive({ companyName: '', jobTitle: '', city: '', salary: '', responsibilities: '', requirements: '', educationRequirement: '', sourcePlatform: '', originalUrl: '', applicationDeadline: '' })
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
  { key: 'educationRequirement', label: '学历要求', required: false, limit: 100 },
  { key: 'sourcePlatform', label: '来源平台', required: false, limit: 100 },
  { key: 'originalUrl', label: '原始链接', required: false, limit: 2048, type: 'url', help: '完整 HTTP/HTTPS 网址，不含账号密码；最多 2048 个字符' },
  { key: 'applicationDeadline', label: '投递截止日期', required: false, type: 'date', help: '选填，1000～9999 年的有效日期，可补录过去日期' },
  { key: 'responsibilities', label: '岗位职责', required: false, limit: 10000, multiline: true, placeholder: '请输入岗位职责，可分行填写' },
  { key: 'requirements', label: '任职要求', required: false, limit: 10000, multiline: true, placeholder: '请输入任职要求，可分行填写' },
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
    else if (input.limit && [...value].length > input.limit) fields.value[input.key] = `${input.label}不能超过${input.limit}个字符`
    if (input.type === 'date' && !formElement.value?.elements.namedItem(input.key)?.validity.valid) {
      fields.value[input.key] = '请选择1000到9999年范围内的有效日期'
    }
  }
  if (Object.keys(fields.value).length) {
    await focusInvalidField()
    return
  }

  busy.value = true
  try {
    // 只发送十个表单字段；归属、初始状态和主键继续由后端决定。
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
        <p class="page-description">记下岗位信息，公司名和岗位名必填，其余可以留空。</p>
      </div>
    </div>

    <ScreenshotUploadPanel :disabled="busy" />
    <div class="workspace-panel job-create-panel">
      <div class="panel-topline">
        <h2>基本信息</h2>
        <span class="muted"><span class="required-mark" aria-hidden="true">*</span> 为必填项</span>
      </div>
      <form ref="formElement" class="job-form" novalidate :aria-busy="busy" @submit.prevent="submit">
        <fieldset :disabled="busy">
          <legend class="visually-hidden">新增岗位信息</legend>
          <div class="job-form-grid">
            <div v-for="input in inputs" :key="input.key" class="job-form-field" :class="{ 'job-form-field-wide': input.multiline }">
              <label :for="`job-${input.key}`">
                {{ input.label }}
                <span v-if="input.required" class="required-mark" aria-hidden="true">*</span>
                <span v-else class="optional-label">（选填）</span>
              </label>
              <textarea v-if="input.multiline" :id="`job-${input.key}`" v-model="form[input.key]" :name="input.key"
                        rows="6" :placeholder="input.placeholder" :aria-invalid="Boolean(fields[input.key])"
                        :aria-describedby="`job-${input.key}-help${fields[input.key] ? ` job-${input.key}-error` : ''}`"></textarea>
              <input v-else :id="`job-${input.key}`" v-model="form[input.key]" :name="input.key"
                     :type="input.type || 'text'" :required="input.required"
                     :min="input.type === 'date' ? '1000-01-01' : undefined"
                     :max="input.type === 'date' ? '9999-12-31' : undefined" :placeholder="input.placeholder"
                     :aria-invalid="Boolean(fields[input.key])"
                     :aria-describedby="`job-${input.key}-help${fields[input.key] ? ` job-${input.key}-error` : ''}`" />
              <p :id="`job-${input.key}-help`" class="muted input-help">{{ input.help || `最多 ${input.limit} 个字符` }}</p>
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
