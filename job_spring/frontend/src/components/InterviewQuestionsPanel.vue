<script setup>
import { nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { post, request } from '../api.js'
import { clearUser } from '../auth.js'

const props = defineProps({ roundId: { type: Number, required: true }, locked: Boolean })
const emit = defineEmits(['busy', 'missing'])
const router = useRouter()
const items = ref([])
const page = ref(1)
const total = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loadError = ref('')
const editing = ref(false)
const saving = ref(false)
const uncertain = ref(false)
const checked = ref(false)
const success = ref('')
const saveError = ref('')
const fields = ref({})
const formElement = ref(null)
const addButton = ref(null)
const form = reactive({ question: '', answerNotes: '', knowledgeTopic: '', reviewNotes: '' })
const inputs = [
  { key: 'question', label: '问题内容', limit: 2000, required: true },
  { key: 'answerNotes', label: '回答要点', limit: 4000 },
  { key: 'knowledgeTopic', label: '知识主题', limit: 100 },
  { key: 'reviewNotes', label: '问题复盘', limit: 2000 },
]
let active = true
let requestNumber = 0
let submittedPage = 1
watch(() => loading.value || editing.value || saving.value || uncertain.value, value => emit('busy', value), { flush: 'sync' })
onBeforeUnmount(() => { active = false; requestNumber++; emit('busy', false) })

async function handleAccessFailure(failure) {
  if (failure.status === 401) { clearUser(); await router.replace('/login'); return true }
  if (failure.status === 404) { emit('missing'); return true }
  return false
}

async function loadQuestions(requestedPage = page.value) {
  const number = ++requestNumber
  submittedPage = requestedPage
  loading.value = true; loadError.value = ''; checked.value = false
  try {
    const result = await request(`/api/interview-rounds/${props.roundId}/questions?page=${requestedPage}`)
    if (!active || number !== requestNumber) return
    items.value = result.items; page.value = result.page; total.value = result.total; totalPages.value = result.totalPages
    checked.value = true
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (!await handleAccessFailure(failure)) loadError.value = failure.message
  } finally {
    if (active && number === requestNumber) loading.value = false
  }
}

function changePage(next) {
  if (props.locked || loading.value || saving.value || (editing.value && !uncertain.value)) return
  if (next < 1 || next > totalPages.value) return
  return loadQuestions(next)
}

async function openForm() {
  if (props.locked || loading.value || saving.value || uncertain.value || editing.value || loadError.value) return
  Object.assign(form, { question: '', answerNotes: '', knowledgeTopic: '', reviewNotes: '' })
  fields.value = {}; saveError.value = ''; success.value = ''; editing.value = true
  await nextTick()
  formElement.value?.querySelector('textarea')?.focus()
}

async function cancel() {
  if (props.locked || saving.value || uncertain.value) return
  editing.value = false; fields.value = {}; saveError.value = ''
  await nextTick()
  addButton.value?.focus()
}

async function submit() {
  if (props.locked || saving.value || uncertain.value || loading.value || loadError.value) return
  fields.value = {}; saveError.value = ''; success.value = ''
  for (const input of inputs) {
    const value = form[input.key].trim()
    if (input.required && !value) fields.value[input.key] = `${input.label}不能为空`
    else if ([...value].length > input.limit) fields.value[input.key] = `${input.label}不能超过${input.limit}个字符`
  }
  if (Object.keys(fields.value).length) {
    await nextTick(); formElement.value?.querySelector('[aria-invalid="true"]')?.focus(); return
  }
  saving.value = true
  try {
    await post(`/api/interview-rounds/${props.roundId}/questions`, { ...form })
    if (!active) return
    editing.value = false
    success.value = '面试问题已保存。'
    // 按 ID 倒序，新问题在首页；写入成功后的 GET 失败只重试读取。
    await loadQuestions(1)
  } catch (failure) {
    if (!active) return
    if (!await handleAccessFailure(failure)) {
      fields.value = failure.fields || {}
      uncertain.value = failure.status === 0; checked.value = false
      saveError.value = uncertain.value
        ? `${failure.message} 保存结果暂时无法确认，请先加载问题列表核对，避免重复添加。` : failure.message
    }
  } finally {
    if (active) saving.value = false
  }
  if (active && Object.keys(fields.value).length) {
    await nextTick(); formElement.value?.querySelector('[aria-invalid="true"]')?.focus()
  }
}

function acknowledgeResult() {
  if (!uncertain.value || !checked.value || loading.value || loadError.value) return
  uncertain.value = false; editing.value = false; saveError.value = ''; fields.value = {}
}

watch(() => props.roundId, () => loadQuestions(1), { immediate: true })
</script>

<template>
  <section class="interview-questions" aria-label="本轮面试问题">
    <h2 v-if="total">本轮问题 <span class="muted">（{{ total }}）</span></h2>
    <p v-if="total" class="muted input-help">记录问题、回答要点与复盘；最新添加的排在前面。</p>
    <p v-if="success" class="form-message success" role="status">{{ success }}</p>
    <p v-if="loading" role="status">正在加载面试问题…</p>
    <div v-else-if="loadError" role="alert">
      <p>{{ loadError }}</p>
      <button type="button" class="text-button" @click="loadQuestions(submittedPage)">重新加载问题</button>
    </div>
    <template v-else>
      <p v-if="!items.length" class="muted review-empty">无面试过程记录</p>
      <ol v-else class="interview-question-list" aria-label="本轮已保存的问题">
        <li v-for="(item, index) in items" :key="item.id">
          <p class="question-content"><span class="muted">{{ (page - 1) * 10 + index + 1 }}. </span>{{ item.question }}</p>
          <p class="question-topic">知识主题：{{ item.knowledgeTopic || '未填写' }}</p>
          <details>
            <summary>查看回答与复盘</summary>
            <dl>
              <div><dt>回答要点</dt><dd class="question-content">{{ item.answerNotes || '未填写' }}</dd></div>
              <div><dt>问题复盘</dt><dd class="question-content">{{ item.reviewNotes || '未填写' }}</dd></div>
            </dl>
          </details>
        </li>
      </ol>
      <nav v-if="totalPages" class="question-pagination" aria-label="面试问题分页">
        <p class="muted">问题第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} 条 · 每页 10 条</p>
        <div class="interview-pagination-actions">
          <button type="button" class="secondary-button" :disabled="page <= 1 || locked || saving || (editing && !uncertain)"
                  @click="changePage(page - 1)">上一页问题</button>
          <button type="button" class="secondary-button" :disabled="page >= totalPages || locked || saving || (editing && !uncertain)"
                  @click="changePage(page + 1)">下一页问题</button>
        </div>
      </nav>
    </template>
    <button v-if="!editing" ref="addButton" type="button" class="secondary-button"
            :disabled="locked || loading || Boolean(loadError)" @click="openForm">{{ total ? '新增问题' : '添加记录' }}</button>
    <form v-else ref="formElement" class="job-form interview-form" novalidate :aria-busy="saving" @submit.prevent="submit">
      <h3>新增面试问题</h3>
      <fieldset :disabled="locked || saving || uncertain">
        <legend class="visually-hidden">新增面试问题</legend>
        <div v-for="input in inputs" :key="input.key" class="job-form-field">
          <label :for="`question-${roundId}-${input.key}`">{{ input.label }} <span v-if="input.required" class="required-mark">*</span><span v-else class="optional-label">（选填）</span></label>
          <input v-if="input.key === 'knowledgeTopic'" :id="`question-${roundId}-${input.key}`" v-model="form[input.key]" type="text"
                 :aria-invalid="Boolean(fields[input.key])" :aria-describedby="`question-${roundId}-${input.key}-help`" />
          <textarea v-else :id="`question-${roundId}-${input.key}`" v-model="form[input.key]" rows="4" :required="input.required"
                    :aria-invalid="Boolean(fields[input.key])" :aria-describedby="`question-${roundId}-${input.key}-help`"></textarea>
          <p :id="`question-${roundId}-${input.key}-help`" class="input-help" :class="fields[input.key] ? 'field-error' : 'muted'">
            {{ fields[input.key] || `最多 ${input.limit} 个字符` }}
          </p>
        </div>
        <div class="job-form-actions">
          <button type="button" class="secondary-button" @click="cancel">取消新增问题</button>
          <button type="submit" class="primary-button" :disabled="loading || Boolean(loadError)">{{ saving ? '正在保存…' : '保存问题' }}</button>
        </div>
      </fieldset>
      <p v-if="saveError" class="form-message error" role="alert">{{ saveError }}</p>
      <div v-if="uncertain" class="interview-notice">
        <button type="button" class="text-button" :disabled="loading" @click="loadQuestions(1)">重新加载问题并核对</button>
        <button type="button" class="secondary-button" :disabled="!checked || loading || Boolean(loadError)" @click="acknowledgeResult">已核对问题，恢复操作</button>
      </div>
    </form>
  </section>
</template>
