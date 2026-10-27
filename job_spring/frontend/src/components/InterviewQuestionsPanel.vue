<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { post, put, remove, request } from '../api.js'
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
const editId = ref(null)
const panelElement = ref(null)
const cancelDeleteButton = ref(null)
const confirmingDelete = ref(null)
const deleteTarget = ref(null)
const deleting = ref(false)
const checkingDelete = ref(false)
const deleteUncertain = ref(false)
const deleteError = ref('')
const deleteNotice = ref('')
const deleteLocked = computed(() => Boolean(confirmingDelete.value) || deleting.value || checkingDelete.value || deleteUncertain.value)
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
let submittedQuestionId = null
watch(() => loading.value || editing.value || saving.value || uncertain.value || deleteLocked.value, value => emit('busy', value), { flush: 'sync' })
onBeforeUnmount(() => { active = false; requestNumber++; emit('busy', false) })

async function handleAccessFailure(failure) {
  if (failure.status === 401) { clearUser(); await router.replace('/login'); return true }
  if (failure.status === 404 && failure.code === 'QUESTION_NOT_FOUND') {
    editing.value = false; editId.value = null
    saveError.value = '面试问题不存在或无法访问，请核对最新列表。'
    await loadQuestions(page.value)
    return true
  }
  if (failure.status === 404) { emit('missing'); return true }
  return false
}

async function loadQuestions(requestedPage = page.value, questionId = null) {
  const number = ++requestNumber
  submittedPage = requestedPage; submittedQuestionId = questionId
  loading.value = true; loadError.value = ''; checked.value = false
  try {
    const query = new URLSearchParams({ page: String(requestedPage) })
    if (questionId !== null) query.set('questionId', String(questionId))
    const result = await request(`/api/interview-rounds/${props.roundId}/questions?${query}`)
    if (!active || number !== requestNumber) return
    items.value = result.items; page.value = result.page; total.value = result.total; totalPages.value = result.totalPages
    checked.value = true
    return result
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (!await handleAccessFailure(failure)) loadError.value = failure.message
  } finally {
    if (active && number === requestNumber) loading.value = false
  }
}

function changePage(next) {
  if (props.locked || deleteLocked.value || loading.value || saving.value || (editing.value && !uncertain.value)) return
  if (next < 1 || next > totalPages.value) return
  return loadQuestions(next)
}

async function openForm() {
  if (props.locked || deleteLocked.value || loading.value || saving.value || uncertain.value || editing.value || loadError.value) return
  editId.value = null; deleteError.value = ''; deleteNotice.value = ''
  Object.assign(form, { question: '', answerNotes: '', knowledgeTopic: '', reviewNotes: '' })
  fields.value = {}; saveError.value = ''; success.value = ''; editing.value = true
  await nextTick()
  formElement.value?.querySelector('textarea')?.focus()
}

async function cancel() {
  if (props.locked || deleteLocked.value || saving.value || uncertain.value) return
  const target = editId.value
  editing.value = false; editId.value = null; fields.value = {}; saveError.value = ''
  await nextTick()
  if (target !== null) panelElement.value?.querySelector(`[data-question-edit="${target}"]`)?.focus()
  else addButton.value?.focus()
}

async function submit() {
  if (props.locked || deleteLocked.value || saving.value || uncertain.value || loading.value || loadError.value) return
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
    const updating = editId.value !== null
    const saved = updating
      ? await put(`/api/interview-questions/${editId.value}`, { ...form })
      : await post(`/api/interview-rounds/${props.roundId}/questions`, { ...form })
    if (!active) return
    editing.value = false; editId.value = null
    success.value = updating ? '面试问题已更新。' : '面试问题已保存。'
    // 按 ID 倒序，新问题在首页；写入成功后的 GET 失败只重试读取。
    await loadQuestions(updating ? page.value : 1, updating ? saved.id : null)
  } catch (failure) {
    if (!active) return
    if (!await handleAccessFailure(failure)) {
      fields.value = failure.fields || {}
      uncertain.value = failure.status === 0; checked.value = false
      saveError.value = uncertain.value
        ? `${failure.message} 保存结果暂时无法确认，请先加载问题列表核对，确认后再继续操作。` : failure.message
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
  uncertain.value = false; editing.value = false; editId.value = null; saveError.value = ''; fields.value = {}
}

async function openEdit(item) {
  if (props.locked || loading.value || editing.value || saving.value || uncertain.value || deleteLocked.value) return
  editId.value = item.id
  for (const input of inputs) form[input.key] = item[input.key] || ''
  fields.value = {}; saveError.value = ''; success.value = ''; deleteError.value = ''; deleteNotice.value = ''
  editing.value = true
  await nextTick(); formElement.value?.querySelector('textarea')?.focus()
}

function retryLoad() {
  return loadQuestions(submittedPage, submittedQuestionId)
}

async function askToDelete(item) {
  if (props.locked || loading.value || editing.value || saving.value || uncertain.value || deleteLocked.value) return
  deleteTarget.value = { id: item.id, question: item.question }
  confirmingDelete.value = deleteTarget.value
  deleteError.value = ''; deleteNotice.value = ''; success.value = ''; saveError.value = ''
  await nextTick(); cancelDeleteButton.value?.focus()
}

async function cancelDelete() {
  if (deleting.value) return
  const id = deleteTarget.value?.id
  confirmingDelete.value = null; deleteTarget.value = null; deleteError.value = ''
  await nextTick(); panelElement.value?.querySelector(`[data-question-delete="${id}"]`)?.focus()
}

async function deleteQuestion() {
  if (!confirmingDelete.value || deleting.value || props.locked || saving.value || uncertain.value || deleteUncertain.value) return
  const number = requestNumber
  const target = deleteTarget.value
  deleting.value = true; deleteError.value = ''
  try {
    await remove(`/api/interview-questions/${target.id}`)
    if (!active || number !== requestNumber) return
    confirmingDelete.value = null; deleteTarget.value = null
    success.value = '面试问题已删除。'
    await loadQuestions(page.value)
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 404 && failure.code === 'QUESTION_NOT_FOUND') {
      confirmingDelete.value = null; deleteTarget.value = null
      deleteError.value = '面试问题不存在或无法访问，请核对最新列表。'
      await loadQuestions(page.value)
    } else if (!await handleAccessFailure(failure)) {
      deleteUncertain.value = failure.status === 0
      if (deleteUncertain.value) confirmingDelete.value = null
      deleteError.value = deleteUncertain.value ? `${failure.message} 删除结果暂时无法确认，请先核对。` : failure.message
    }
  } finally { if (active) deleting.value = false }
}

async function checkDeleteResult() {
  if (!deleteUncertain.value || !deleteTarget.value || checkingDelete.value || loading.value) return
  const target = deleteTarget.value
  checkingDelete.value = true
  try {
    const result = await loadQuestions(page.value, target.id)
    if (!active || !result) return
    deleteNotice.value = result.items.some(item => item.id === target.id)
      ? '问题仍然存在，已重新加载。如需删除，请重新确认。' : '问题已不存在或无法访问，列表已更新。'
    deleteUncertain.value = false; deleteTarget.value = null; deleteError.value = ''
  } finally { if (active) checkingDelete.value = false }
}

watch(() => props.roundId, () => loadQuestions(1), { immediate: true })
</script>

<template>
  <section ref="panelElement" class="interview-questions" aria-label="本轮面试问题">
    <h2 v-if="total">本轮问题 <span class="muted">（{{ total }}）</span></h2>
    <p v-if="total" class="muted input-help">记录问题、回答要点与复盘；最新添加的排在前面。</p>
    <p v-if="success" class="form-message success" role="status">{{ success }}</p>
    <p v-if="!editing && saveError" class="form-message error" role="alert">{{ saveError }}</p>
    <p v-if="deleteError" class="form-message error" role="alert">{{ deleteError }}</p>
    <p v-if="deleteNotice" class="muted" role="status">{{ deleteNotice }}</p>
    <button v-if="deleteUncertain" type="button" class="text-button" :disabled="checkingDelete || loading" @click="checkDeleteResult">核对问题删除结果</button>
    <p v-if="loading" role="status">正在加载面试问题…</p>
    <div v-else-if="loadError" role="alert">
      <p>{{ loadError }}</p>
      <button type="button" class="text-button" @click="retryLoad">重新加载问题</button>
    </div>
    <template v-else>
      <p v-if="!items.length" class="muted review-empty">无面试过程记录</p>
      <ol v-else class="interview-question-list" aria-label="本轮已保存的问题">
        <li v-for="(item, index) in items" :key="item.id" class="question-row">
          <div class="question-content-block">
          <p class="question-content"><span class="muted">{{ (page - 1) * 10 + index + 1 }}. </span>{{ item.question }}</p>
          <p class="question-topic">知识主题：{{ item.knowledgeTopic || '未填写' }}</p>
          <details>
            <summary>查看回答与复盘</summary>
            <dl>
              <div><dt>回答要点</dt><dd class="question-content">{{ item.answerNotes || '未填写' }}</dd></div>
              <div><dt>问题复盘</dt><dd class="question-content">{{ item.reviewNotes || '未填写' }}</dd></div>
            </dl>
          </details>
          </div>
          <div class="question-item-actions">
            <button type="button" class="secondary-button" :aria-label="`编辑第 ${(page - 1) * 10 + index + 1} 条问题`"
                    :data-question-edit="item.id" :disabled="locked || loading || editing || saving || uncertain || deleteLocked" @click="openEdit(item)">编辑</button>
            <button type="button" class="danger-button danger-outline" :aria-label="`删除第 ${(page - 1) * 10 + index + 1} 条问题`"
                    :data-question-delete="item.id" :disabled="locked || loading || editing || saving || uncertain || deleteLocked" @click="askToDelete(item)">删除</button>
          </div>
        </li>
      </ol>
      <nav v-if="totalPages" class="question-pagination" aria-label="面试问题分页">
        <p class="muted">问题第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} 条 · 每页 10 条</p>
        <div class="interview-pagination-actions">
          <button type="button" class="secondary-button" :disabled="page <= 1 || locked || saving || deleteLocked || (editing && !uncertain)"
                  @click="changePage(page - 1)">上一页问题</button>
          <button type="button" class="secondary-button" :disabled="page >= totalPages || locked || saving || deleteLocked || (editing && !uncertain)"
                  @click="changePage(page + 1)">下一页问题</button>
        </div>
      </nav>
    </template>
    <div v-if="confirmingDelete" class="delete-confirmation" role="group" aria-labelledby="question-delete-heading" @keydown.esc.prevent="cancelDelete">
      <h3 id="question-delete-heading">确认删除这个问题？</h3>
      <p class="delete-target question-delete-preview">{{ confirmingDelete.question }}</p>
      <p>该问题、回答要点、知识主题和复盘会一并删除，无法恢复。其他问题及面试轮次会保留。</p>
      <div class="delete-actions">
        <button ref="cancelDeleteButton" type="button" class="secondary-button" :disabled="deleting" @click="cancelDelete">取消删除问题</button>
        <button type="button" class="danger-button" :disabled="deleting" @click="deleteQuestion">{{ deleting ? '正在删除…' : '确认删除问题' }}</button>
      </div>
    </div>
    <button v-if="!editing" ref="addButton" type="button" class="secondary-button"
            :disabled="locked || loading || deleteLocked || Boolean(loadError)" @click="openForm">{{ total ? '新增问题' : '添加记录' }}</button>
    <form v-else ref="formElement" class="job-form interview-form" novalidate :aria-busy="saving" @submit.prevent="submit">
      <h3>{{ editId !== null ? '编辑面试问题' : '新增面试问题' }}</h3>
      <p v-if="editId !== null" class="muted input-help">选填项留空会清除原内容。</p>
      <fieldset :disabled="locked || saving || uncertain || deleteLocked">
        <legend class="visually-hidden">{{ editId !== null ? '编辑面试问题' : '新增面试问题' }}</legend>
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
          <button type="button" class="secondary-button" @click="cancel">{{ editId !== null ? '取消编辑问题' : '取消新增问题' }}</button>
          <button type="submit" class="primary-button" :disabled="loading || Boolean(loadError)">{{ saving ? '正在保存…' : (editId !== null ? '保存问题修改' : '保存问题') }}</button>
        </div>
      </fieldset>
      <p v-if="saveError" class="form-message error" role="alert">{{ saveError }}</p>
      <div v-if="uncertain" class="interview-notice">
        <button type="button" class="text-button" :disabled="loading" @click="loadQuestions(editId !== null ? page : 1, editId)">重新加载问题并核对</button>
        <button type="button" class="secondary-button" :disabled="!checked || loading || Boolean(loadError)" @click="acknowledgeResult">已核对问题，恢复操作</button>
      </div>
    </form>
  </section>
</template>
