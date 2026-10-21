<script setup>
import { nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { post, request } from '../api.js'
import { clearUser } from '../auth.js'

const props = defineProps({ jobId: { type: Number, required: true }, locked: Boolean })
const emit = defineEmits(['busy', 'missing'])
const router = useRouter()
const rounds = ref([])
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
const form = reactive({ roundNo: '', interviewTime: '', roundType: '', result: '', review: '' })
let active = true
let requestNumber = 0
watch(() => saving.value || uncertain.value, value => emit('busy', value), { flush: 'sync' })
onBeforeUnmount(() => { active = false; requestNumber++ })

async function handleAccessFailure(failure) {
  if (failure.status === 401) { clearUser(); await router.replace('/login'); return true }
  if (failure.status === 404) { emit('missing'); return true }
  return false
}

async function loadRounds() {
  const number = ++requestNumber
  loading.value = true; loadError.value = ''; checked.value = false
  try {
    const result = await request(`/api/jobs/${props.jobId}/interview-rounds`)
    if (!active || number !== requestNumber) return
    rounds.value = result
    checked.value = true
  } catch (failure) {
    if (!active || number !== requestNumber) return
    if (!await handleAccessFailure(failure)) loadError.value = failure.message
  } finally {
    if (active && number === requestNumber) loading.value = false
  }
}

function resetForm() {
  Object.assign(form, { roundNo: '', interviewTime: '', roundType: '', result: '', review: '' })
  fields.value = {}; saveError.value = ''
}

async function focusInvalid() {
  await nextTick()
  formElement.value?.querySelector('[aria-invalid="true"]')?.focus()
}

async function openForm() {
  if (props.locked || loading.value || saving.value || uncertain.value) return
  resetForm(); success.value = ''; editing.value = true
  await nextTick()
  formElement.value?.querySelector('input')?.focus()
}

function cancel() {
  if (props.locked || saving.value || uncertain.value) return
  editing.value = false; resetForm()
}

async function submit() {
  if (props.locked || saving.value || uncertain.value || loading.value) return
  fields.value = {}; saveError.value = ''; success.value = ''
  const roundNo = Number(form.roundNo)
  if (!/^\d+$/.test(String(form.roundNo)) || !Number.isInteger(roundNo) || roundNo < 1 || roundNo > 2147483647) {
    fields.value.roundNo = '面试轮次必须是1到2147483647之间的整数'
  }
  if (form.interviewTime && (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(form.interviewTime)
      || Number(form.interviewTime.slice(0, 4)) < 1000)) {
    fields.value.interviewTime = '请选择1000到9999年之间的有效日期和时间'
  }
  for (const [key, label, limit] of [['roundType', '轮次类型', 50], ['result', '面试结果', 100], ['review', '整体复盘', 2000]]) {
    if ([...form[key].trim()].length > limit) fields.value[key] = `${label}不能超过${limit}个字符`
  }
  if (Object.keys(fields.value).length) { await focusInvalid(); return }
  saving.value = true
  try {
    await post(`/api/jobs/${props.jobId}/interview-rounds`, {
      roundNo, interviewTime: form.interviewTime ? `${form.interviewTime}:00` : null,
      roundType: form.roundType, result: form.result, review: form.review,
    })
    if (!active) return
    editing.value = false; resetForm()
    success.value = '面试轮次已保存。'
    // POST 已成功；随后 GET 失败只表示列表加载失败，不应再次发送 POST。
    await loadRounds()
  } catch (failure) {
    if (!active) return
    if (!await handleAccessFailure(failure)) {
      fields.value = failure.fields || {}
      uncertain.value = failure.status === 0
      checked.value = false
      saveError.value = uncertain.value
        ? `${failure.message} 保存结果暂时无法确认，请先重新加载轮次并核对，避免重复新增。`
        : failure.message
      if (failure.status === 409 && failure.fields?.roundNo) {
        // 拒绝重复并保留草稿，只刷新列表；绝不自动重发 POST 或覆盖原记录。
        await loadRounds()
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

function displayTime(value) {
  return value ? value.replace('T', ' ') : '未填写'
}

watch(() => props.jobId, loadRounds, { immediate: true })
</script>

<template>
  <section class="action-panel interview-panel" aria-labelledby="interview-heading">
    <span class="section-number">01 / 面试</span>
    <h2 id="interview-heading">面试轮次</h2>
    <p>记录每轮面试的时间、结果与整体复盘。</p>
    <p v-if="success" class="form-message success" role="status">{{ success }}</p>
    <p v-if="loading" class="interview-notice" role="status">正在加载面试轮次…</p>
    <div v-else-if="loadError" class="interview-notice" role="alert">
      <p>{{ loadError }}</p><button type="button" class="text-button" @click="loadRounds">重新加载轮次</button>
    </div>
    <template v-else>
      <p v-if="!rounds.length" class="interview-notice">暂无面试轮次，可以先添加第一轮。</p>
      <ol v-else class="interview-round-list" aria-label="已保存的面试轮次">
        <li v-for="round in rounds" :key="round.id" class="interview-round">
          <h3>第 {{ round.roundNo }} 轮<span v-if="round.roundType"> · {{ round.roundType }}</span></h3>
          <dl>
            <div><dt>面试时间（上海时间）</dt><dd>{{ displayTime(round.interviewTime) }}</dd></div>
            <div><dt>面试结果</dt><dd>{{ round.result || '未填写' }}</dd></div>
            <div><dt>整体复盘</dt><dd class="interview-review">{{ round.review || '未填写' }}</dd></div>
          </dl>
        </li>
      </ol>
    </template>
    <button v-if="!editing" type="button" class="secondary-button"
            :disabled="locked || loading || saving || uncertain || Boolean(loadError)" @click="openForm">新增面试轮次</button>
    <form v-if="editing" ref="formElement" class="job-form interview-form" novalidate :aria-busy="saving" @submit.prevent="submit">
      <fieldset :disabled="locked || saving || uncertain">
        <legend class="visually-hidden">新增面试轮次</legend>
        <div class="job-form-field">
          <label for="round-no">第几轮 <span class="required-mark">*</span></label>
          <input id="round-no" v-model="form.roundNo" name="roundNo" type="number" min="1" max="2147483647" step="1" required
                 :aria-invalid="Boolean(fields.roundNo)" :aria-describedby="fields.roundNo ? 'round-no-error' : undefined" />
          <p v-if="fields.roundNo" id="round-no-error" class="field-error">{{ fields.roundNo }}</p>
        </div>
        <div class="job-form-field">
          <label for="round-time">面试时间 <span class="optional-label">（选填）</span></label>
          <input id="round-time" v-model="form.interviewTime" name="interviewTime" type="datetime-local" step="60"
                 min="1000-01-01T00:00" max="9999-12-31T23:59"
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
          <button type="submit" class="primary-button" :disabled="loading || Boolean(loadError)">{{ saving ? '正在保存…' : '保存轮次' }}</button>
        </div>
      </fieldset>
      <p v-if="saveError" class="form-message error" role="alert">{{ saveError }}</p>
      <div v-if="uncertain" class="interview-notice">
        <button type="button" class="text-button" :disabled="loading" @click="loadRounds">重新加载轮次并核对</button>
        <button type="button" class="secondary-button" :disabled="!checked || loading || Boolean(loadError)"
                @click="acknowledgeResult">已核对，恢复操作</button>
      </div>
    </form>
  </section>
</template>
