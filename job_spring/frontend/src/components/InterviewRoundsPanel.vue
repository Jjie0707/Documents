<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { post, put, remove, request } from '../api.js'
import { clearUser } from '../auth.js'

const props = defineProps({ jobId: { type: Number, required: true }, initialRoundId: { type: Number, default: null }, locked: Boolean })
const emit = defineEmits(['busy', 'missing'])
const router = useRouter()
const rounds = ref([])
const page = ref(1)
const total = ref(0)
let submittedPage = 1
let submittedRoundId = null
const loading = ref(false)
const loadError = ref('')
const editing = ref(false)
const editRoundId = ref(null)
const saving = ref(false)
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
const uncertain = ref(false)
const checked = ref(false)
const success = ref('')
const saveError = ref('')
const fields = ref({})
const formElement = ref(null)
const form = reactive({ roundNo: '', interviewTime: '', roundType: '', result: '', review: '' })
let active = true
let requestNumber = 0
watch(() => editing.value || saving.value || uncertain.value || deleteLocked.value, value => emit('busy', value), { flush: 'sync' })
onBeforeUnmount(() => { active = false; requestNumber++ })

async function handleAccessFailure(failure) {
  if (failure.status === 401) { clearUser(); await router.replace('/login'); return true }
  if (failure.status === 404 && failure.code === 'ROUND_NOT_FOUND') {
    editing.value = false; resetForm()
    saveError.value = '面试轮次不存在或无法访问，请核对最新列表。'
    await loadRounds()
    return true
  }
  if (failure.status === 404) { emit('missing'); return true }
  return false
}

async function loadRounds(requestedPage = page.value, roundId = null) {
  submittedPage = requestedPage; submittedRoundId = roundId
  const number = ++requestNumber
  loading.value = true; loadError.value = ''; checked.value = false
  try {
    const query = new URLSearchParams({ page: String(requestedPage) })
    if (roundId !== null) query.set('roundId', String(roundId))
    const result = await request(`/api/jobs/${props.jobId}/interview-rounds?${query}`)
    if (!active || number !== requestNumber) return
    rounds.value = result.items
    page.value = result.page
    total.value = result.total
    checked.value = true
    return result
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (!await handleAccessFailure(failure)) loadError.value = failure.message
  } finally {
    if (active && number === requestNumber) loading.value = false
  }
}

function retryLoad() {
  if (uncertain.value && editRoundId.value !== null) return loadRounds(page.value, editRoundId.value)
  return loadRounds(submittedPage, submittedRoundId)
}

function changePage(nextPage) {
  if (loading.value || saving.value || props.locked || deleteLocked.value || (editing.value && !uncertain.value)) return
  if (nextPage < 1 || nextPage > total.value) return
  return loadRounds(nextPage)
}

function resetForm() {
  editRoundId.value = null
  Object.assign(form, { roundNo: '', interviewTime: '', roundType: '', result: '', review: '' })
  fields.value = {}; saveError.value = ''
}

async function focusInvalid() {
  await nextTick()
  formElement.value?.querySelector('[aria-invalid="true"]')?.focus()
}

async function openForm() {
  if (props.locked || loading.value || saving.value || uncertain.value || deleteLocked.value) return
  resetForm(); success.value = ''; deleteError.value = ''; deleteNotice.value = ''; editing.value = true
  await nextTick()
  formElement.value?.querySelector('input')?.focus()
}

async function openEdit(round) {
  if (props.locked || loading.value || saving.value || uncertain.value || editing.value || deleteLocked.value) return
  resetForm(); success.value = ''; deleteError.value = ''; deleteNotice.value = ''; editRoundId.value = round.id
  Object.assign(form, {
    roundNo: String(round.roundNo), interviewTime: round.interviewTime || '',
    roundType: round.roundType || '', result: round.result || '', review: round.review || '',
  })
  editing.value = true
  await nextTick()
  formElement.value?.querySelector('input')?.focus()
}

function cancel() {
  if (props.locked || saving.value || uncertain.value || deleteLocked.value) return
  editing.value = false; resetForm()
}

async function submit() {
  if (props.locked || saving.value || uncertain.value || loading.value || deleteLocked.value) return
  fields.value = {}; saveError.value = ''; success.value = ''
  const roundNo = Number(form.roundNo)
  if (!/^\d+$/.test(String(form.roundNo)) || !Number.isInteger(roundNo) || roundNo < 1 || roundNo > 2147483647) {
    fields.value.roundNo = '面试轮次必须是1到2147483647之间的整数'
  }
  if (form.interviewTime && (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?$/.test(form.interviewTime)
      || Number(form.interviewTime.slice(0, 4)) < 1000)) {
    fields.value.interviewTime = '请选择1000到9999年之间的有效日期和时间'
  }
  for (const [key, label, limit] of [['roundType', '轮次类型', 50], ['result', '面试结果', 100], ['review', '整体复盘', 2000]]) {
    if ([...form[key].trim()].length > limit) fields.value[key] = `${label}不能超过${limit}个字符`
  }
  if (Object.keys(fields.value).length) { await focusInvalid(); return }
  const updating = editRoundId.value !== null
  const values = {
    roundNo, interviewTime: form.interviewTime
      ? (form.interviewTime.length === 16 ? `${form.interviewTime}:00` : form.interviewTime) : null,
    roundType: form.roundType, result: form.result, review: form.review,
  }
  saving.value = true
  try {
    const saved = updating
      ? await put(`/api/interview-rounds/${editRoundId.value}`, values)
      : await post(`/api/jobs/${props.jobId}/interview-rounds`, values)
    if (!active) return
    editing.value = false; resetForm()
    success.value = updating ? '面试轮次已更新。' : '面试轮次已保存。'
    // 写请求已成功；随后 GET 失败只表示列表加载失败，重试不能再次发送写请求。
    await loadRounds(page.value, saved.id)
  } catch (failure) {
    if (!active) return
    if (!await handleAccessFailure(failure)) {
      fields.value = failure.fields || {}
      uncertain.value = failure.status === 0
      checked.value = false
      saveError.value = uncertain.value
        ? `${failure.message} 保存结果暂时无法确认，请先重新加载轮次并核对保存结果。`
        : failure.message
      if (failure.status === 409 && failure.fields?.roundNo) {
        // 拒绝重复并保留草稿，只刷新列表；绝不自动重发 POST 或覆盖原记录。
        await loadRounds(page.value, editRoundId.value)
      }
    }
  } finally {
    if (active) saving.value = false
  }
  if (active && Object.keys(fields.value).length) await focusInvalid()
}

function acknowledgeResult() {
  if (!uncertain.value || !checked.value || loading.value || loadError.value) return
  uncertain.value = false; saveError.value = ''; fields.value = {}
  editing.value = false; resetForm()
}

async function askToDelete(round) {
  if (props.locked || loading.value || saving.value || editing.value || uncertain.value || deleteLocked.value) return
  deleteError.value = ''; deleteNotice.value = ''; success.value = ''; saveError.value = ''
  deleteTarget.value = { id: round.id, roundNo: round.roundNo, roundType: round.roundType }
  confirmingDelete.value = deleteTarget.value
  await nextTick()
  cancelDeleteButton.value?.focus()
}

async function cancelDelete() {
  if (deleting.value) return
  confirmingDelete.value = null; deleteTarget.value = null; deleteError.value = ''
  await nextTick()
  panelElement.value?.querySelector('[data-round-delete]')?.focus()
}

async function deleteRound() {
  if (!confirmingDelete.value || props.locked || saving.value || uncertain.value || deleting.value || deleteUncertain.value) return
  const target = deleteTarget.value
  const number = requestNumber
  deleting.value = true; deleteError.value = ''; deleteNotice.value = ''
  try {
    await remove(`/api/interview-rounds/${target.id}`)
    if (!active || number !== requestNumber) return
    confirmingDelete.value = null; deleteTarget.value = null
    success.value = '面试轮次及其问题记录已删除。'
    // 删除最后一页时由后端修正到新末页；刷新失败只重试 GET。
    await loadRounds(page.value)
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (failure.status === 404 && failure.code === 'ROUND_NOT_FOUND') {
      confirmingDelete.value = null; deleteTarget.value = null
      deleteError.value = '该轮次不存在或无法访问，请核对最新列表。'
      await loadRounds(page.value)
    } else if (!await handleAccessFailure(failure)) {
      deleteUncertain.value = failure.status === 0
      if (deleteUncertain.value) confirmingDelete.value = null
      deleteError.value = deleteUncertain.value
        ? `${failure.message} 删除结果暂时无法确认，请先核对删除结果。` : failure.message
    }
  } finally {
    if (active) deleting.value = false
  }
}

async function checkDeleteResult() {
  if (!deleteUncertain.value || !deleteTarget.value || checkingDelete.value || loading.value) return
  const target = deleteTarget.value
  checkingDelete.value = true
  try {
    const result = await loadRounds(page.value, target.id)
    if (!active || !result) return
    const exists = result.items.some(round => round.id === target.id)
    deleteUncertain.value = false; deleteTarget.value = null; deleteError.value = ''
    deleteNotice.value = exists
      ? '轮次仍然存在，信息已重新加载。如需删除，请重新确认。'
      : '该轮次已不存在或无法访问，列表已更新。'
  } finally {
    if (active) checkingDelete.value = false
  }
}

function displayTime(value) {
  return value ? value.replace('T', ' ') : '未填写'
}

watch(() => [props.jobId, props.initialRoundId], () => loadRounds(1, props.initialRoundId), { immediate: true })
</script>

<template>
  <section ref="panelElement" class="action-panel interview-panel" aria-labelledby="interview-heading">
    <span class="section-number">01 / 面试</span>
    <h2 id="interview-heading">面试轮次</h2>
    <p>记录每轮面试的时间、结果与整体复盘。</p>
    <p v-if="success" class="form-message success" role="status">{{ success }}</p>
    <p v-if="!editing && saveError" class="form-message error" role="alert">{{ saveError }}</p>
    <p v-if="deleteError" class="form-message error" role="alert">{{ deleteError }}</p>
    <p v-if="deleteNotice" class="muted interview-notice" role="status">{{ deleteNotice }}</p>
    <button v-if="deleteUncertain" type="button" class="text-button" :disabled="checkingDelete || loading" @click="checkDeleteResult">
      {{ checkingDelete ? '正在核对…' : '核对轮次删除结果' }}
    </button>
    <p v-if="loading" class="interview-notice" role="status">正在加载面试轮次…</p>
    <div v-else-if="loadError" class="interview-notice" role="alert">
      <p>{{ loadError }}</p><button type="button" class="text-button" @click="retryLoad">重新加载轮次</button>
    </div>
    <template v-else>
      <p v-if="!rounds.length" class="interview-notice">暂无面试轮次，可以先添加第一轮。</p>
      <ol v-else class="interview-round-list" aria-label="已保存的面试轮次">
        <li v-for="round in rounds" :key="round.id" class="interview-round">
          <div class="round-summary">
            <div class="round-summary-info">
              <h3>第 {{ round.roundNo }} 轮<span v-if="round.roundType"> · {{ round.roundType }}</span></h3>
              <dl>
                <div><dt>面试时间（上海时间）</dt><dd>{{ displayTime(round.interviewTime) }}</dd></div>
                <div><dt>面试结果</dt><dd>{{ round.result || '未填写' }}</dd></div>
                <div><dt>整体复盘</dt><dd class="interview-review">{{ round.review || '未填写' }}</dd></div>
              </dl>
            </div>
            <button v-if="locked || saving || editing || uncertain || deleteLocked" type="button" class="secondary-button round-review-link" disabled>面试回顾</button>
            <RouterLink v-else :to="{ name: 'interview-review', params: { id: String(jobId), roundId: String(round.id) } }"
                        class="secondary-button round-review-link">面试回顾</RouterLink>
          </div>
          <button type="button" class="secondary-button"
                  :disabled="locked || saving || editing || uncertain || deleteLocked" @click="openEdit(round)">编辑本轮</button>
          <button type="button" class="danger-button danger-outline round-delete-button" data-round-delete
                  :disabled="locked || saving || editing || uncertain || deleteLocked" @click="askToDelete(round)">删除本轮</button>
        </li>
      </ol>
      <nav v-if="total" class="interview-pagination" aria-label="面试轮次分页">
        <p class="muted">第 {{ page }} / {{ total }} 页 · 每页一轮</p>
        <div class="interview-pagination-actions">
          <button type="button" class="secondary-button"
                  :disabled="page <= 1 || locked || saving || deleteLocked || (editing && !uncertain)" @click="changePage(page - 1)">上一轮</button>
          <button type="button" class="secondary-button"
                  :disabled="page >= total || locked || saving || deleteLocked || (editing && !uncertain)" @click="changePage(page + 1)">下一轮</button>
        </div>
      </nav>
    </template>
    <div v-if="confirmingDelete" class="delete-confirmation" role="group" aria-labelledby="round-delete-heading"
         :aria-busy="deleting" @keydown.esc.prevent="cancelDelete">
      <h3 id="round-delete-heading">确认删除本轮面试？</h3>
      <p class="delete-target">第 {{ confirmingDelete.roundNo }} 轮<span v-if="confirmingDelete.roundType"> · {{ confirmingDelete.roundType }}</span></p>
      <p>本轮及其问题、回答要点和复盘会一并删除，无法恢复。岗位和其他轮次会保留。</p>
      <div class="delete-actions">
        <button ref="cancelDeleteButton" type="button" class="secondary-button" :disabled="deleting" @click="cancelDelete">取消删除</button>
        <button type="button" class="danger-button" :disabled="deleting" @click="deleteRound">{{ deleting ? '正在删除…' : '确认删除本轮' }}</button>
      </div>
    </div>
    <button v-if="!editing" type="button" class="secondary-button"
            :disabled="locked || loading || saving || uncertain || deleteLocked || Boolean(loadError)" @click="openForm">新增面试轮次</button>
    <form v-if="editing" ref="formElement" class="job-form interview-form" novalidate :aria-busy="saving" @submit.prevent="submit">
      <h3 class="interview-form-heading">{{ editRoundId !== null ? '编辑本轮' : '新增面试轮次' }}</h3>
      <p v-if="editRoundId !== null" class="muted input-help">选填项留空会清除原内容；修改轮次号后，记录会按新编号排序。</p>
      <fieldset :disabled="locked || saving || uncertain || deleteLocked">
        <legend class="visually-hidden">{{ editRoundId !== null ? '编辑面试轮次' : '新增面试轮次' }}</legend>
        <div class="job-form-field">
          <label for="round-no">第几轮 <span class="required-mark">*</span></label>
          <input id="round-no" v-model="form.roundNo" name="roundNo" type="number" min="1" max="2147483647" step="1" required
                 :aria-invalid="Boolean(fields.roundNo)" :aria-describedby="fields.roundNo ? 'round-no-error' : undefined" />
          <p v-if="fields.roundNo" id="round-no-error" class="field-error">{{ fields.roundNo }}</p>
        </div>
        <div class="job-form-field">
          <label for="round-time">面试时间 <span class="optional-label">（选填）</span></label>
          <input id="round-time" v-model="form.interviewTime" name="interviewTime" type="datetime-local" step="0.001"
                 min="1000-01-01T00:00:00" max="9999-12-31T23:59:59.999"
                 :aria-invalid="Boolean(fields.interviewTime)"
                 :aria-describedby="`round-time-help${fields.interviewTime ? ' round-time-error' : ''}`" />
          <p id="round-time-help" class="muted input-help">按上海时间填写，可以记录过去或未来的面试。</p>
          <p v-if="fields.interviewTime" id="round-time-error" class="field-error">{{ fields.interviewTime }}</p>
        </div>
        <div v-for="input in [{ key: 'roundType', label: '轮次类型', limit: 50 }, { key: 'result', label: '面试结果', limit: 100 }]"
             :key="input.key" class="job-form-field">
          <label :for="`round-${input.key}`">{{ input.label }} <span class="optional-label">（选填）</span></label>
          <input :id="`round-${input.key}`" v-model="form[input.key]" :name="input.key" type="text"
                 :aria-invalid="Boolean(fields[input.key])"
                 :aria-describedby="`round-${input.key}-help${fields[input.key] ? ` round-${input.key}-error` : ''}`" />
          <p :id="`round-${input.key}-help`" class="muted input-help">最多 {{ input.limit }} 个字符</p>
          <p v-if="fields[input.key]" :id="`round-${input.key}-error`" class="field-error">{{ fields[input.key] }}</p>
        </div>
        <div class="job-form-field">
          <label for="round-review">整体复盘 <span class="optional-label">（选填）</span></label>
          <textarea id="round-review" v-model="form.review" name="review" rows="5" :aria-invalid="Boolean(fields.review)"
                    :aria-describedby="`round-review-help${fields.review ? ' round-review-error' : ''}`"></textarea>
          <p id="round-review-help" class="muted input-help">最多 2000 个字符</p>
          <p v-if="fields.review" id="round-review-error" class="field-error">{{ fields.review }}</p>
        </div>
        <div class="job-form-actions">
          <button type="button" class="secondary-button" @click="cancel">取消</button>
          <button type="submit" class="primary-button" :disabled="loading || Boolean(loadError)">{{ saving ? '正在保存…' : (editRoundId !== null ? '保存修改' : '保存轮次') }}</button>
        </div>
      </fieldset>
      <p v-if="saveError" class="form-message error" role="alert">{{ saveError }}</p>
      <div v-if="uncertain" class="interview-notice">
        <button type="button" class="text-button" :disabled="loading" @click="retryLoad">重新加载轮次并核对</button>
        <button type="button" class="secondary-button" :disabled="!checked || loading || Boolean(loadError)"
                @click="acknowledgeResult">已核对，恢复操作</button>
      </div>
    </form>
  </section>
</template>
