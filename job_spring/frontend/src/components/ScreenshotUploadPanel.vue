<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, postMultipart, extractScreenshot } from '../api.js'
import { clearUser } from '../auth.js'
import { hasDraggedFiles, readDroppedScreenshot, readPastedScreenshot, selectSingleScreenshot } from '../screenshot-input.js'
import VisionQuotaPanel from './VisionQuotaPanel.vue'
import RecognitionDraftPanel from './RecognitionDraftPanel.vue'

const props = defineProps({ disabled: Boolean })
const router = useRouter()
const inputArea = ref(null)
const fileInput = ref(null)
const cameraInput = ref(null)
const cameraAvailable = ref(false)
const file = ref(null)
const previewUrl = ref('')
const previewReady = ref(false)
const previewExpanded = ref(false)
const checking = ref(false)
const result = ref(null)
const error = ref('')
const draft = ref(null)
const recognizing = ref(false)
const recognitionError = ref('')
const quotaRevision = ref(0)
const dragDepth = ref(0)
let revision = 0
let active = true

function clearSelection() {
  revision++
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  file.value = null; previewUrl.value = ''; previewReady.value = false
  previewExpanded.value = false
  checking.value = false; result.value = null; error.value = ''
  draft.value = null; recognitionError.value = ''
  dragDepth.value = 0
}

function receiveSelection(selection) {
  if (props.disabled || recognizing.value) return
  clearSelection()
  if (selection.error) { error.value = selection.error; return }
  file.value = selection.file
  previewUrl.value = URL.createObjectURL(selection.file)
}

function selectFile(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = '' // 允许再次选择同一文件；取消选择保留当前图片。
  if (files.length) receiveSelection(selectSingleScreenshot(files))
}

function dragEnter(event) {
  if (!props.disabled && !recognizing.value && hasDraggedFiles(event.dataTransfer)) dragDepth.value++
}

function dragOver(event) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = props.disabled ? 'none' : 'copy'
}

function dropFile(event) {
  event.preventDefault()
  dragDepth.value = 0
  if (!props.disabled && !recognizing.value) receiveSelection(readDroppedScreenshot(event.dataTransfer))
}

function pasteImage(event) {
  if (props.disabled || recognizing.value) return
  const selection = readPastedScreenshot(event.clipboardData)
  if (!selection.handled) return
  event.preventDefault()
  receiveSelection(selection)
}

function guardOutsideDrag(event) {
  if (!inputArea.value?.contains(event.target) && hasDraggedFiles(event.dataTransfer)) {
    event.preventDefault()
    if (event.type === 'drop') {
      dragDepth.value = 0
      if (!props.disabled) error.value = '请将图片拖到上方的截图区域。'
    }
  }
}

function previewLoaded(event) {
  if (event.target.getAttribute('src') === previewUrl.value) previewReady.value = true
}

function previewFailed(event) {
  if (event.target.getAttribute('src') !== previewUrl.value) return
  previewReady.value = false
  error.value = '图片无法预览，请换一张PNG或JPEG截图。HEIC/HEIF照片请先导出为JPEG或PNG，不能只修改后缀。'
}

async function checkImage() {
  if (props.disabled || recognizing.value || !file.value || !previewReady.value || checking.value) return
  const number = revision
  const selected = file.value
  checking.value = true; result.value = null; error.value = ''
  const body = new FormData()
  body.append('image', selected)
  try {
    const response = await postMultipart('/api/vision/images/validate', body)
    if (!active || number !== revision) return
    if (!response || !['PNG', 'JPEG'].includes(response.format)
        || !Number.isInteger(response.width) || response.width < 1
        || !Number.isInteger(response.height) || response.height < 1) {
      throw new ApiError(0, '未能读取图片检查结果，请重试。')
    }
    result.value = response
  } catch (failure) {
    if (!active || number !== revision) return
    if (failure.status === 401) { clearUser(); await router.replace('/login') }
    else error.value = failure.message
  } finally {
    if (active && number === revision) checking.value = false
  }
}

async function recognizeImage() {
  if (props.disabled || recognizing.value || checking.value || !file.value || !previewReady.value) return
  const number = revision
  recognizing.value = true; draft.value = null; recognitionError.value = ''
  const body = new FormData()
  body.append('image', file.value)
  try {
    const response = await extractScreenshot(body)
    if (!active || number !== revision) return
    if (!response || typeof response !== 'object' || Array.isArray(response)) throw new ApiError(0, '未能读取识别草稿，请继续手动填写。')
    draft.value = response
  } catch (failure) {
    if (!active || number !== revision) return
    if (failure.status === 401) { clearUser(); await router.replace('/login') }
    else recognitionError.value = failure.message
  } finally {
    if (active) { recognizing.value = false; quotaRevision.value++ }
  }
}

onMounted(() => {
  // 这是浏览器能力提示，不代表设备一定有相机；普通选图入口始终保留。
  cameraAvailable.value = !!cameraInput.value && 'capture' in cameraInput.value
  window.addEventListener('dragover', guardOutsideDrag)
  window.addEventListener('drop', guardOutsideDrag)
})
onBeforeUnmount(() => {
  window.removeEventListener('dragover', guardOutsideDrag)
  window.removeEventListener('drop', guardOutsideDrag)
  active = false
  clearSelection()
})
</script>

<template>
  <section ref="inputArea" class="workspace-panel screenshot-panel" :class="{ 'is-dragging': dragDepth > 0 }"
           role="group" aria-labelledby="screenshot-heading" aria-describedby="screenshot-input-help screenshot-privacy"
           :tabindex="disabled ? -1 : 0" :aria-disabled="disabled" :aria-busy="checking || recognizing"
           @dragenter="dragEnter" @dragover="dragOver" @dragleave="dragDepth = Math.max(0, dragDepth - 1)"
           @drop="dropFile" @paste="pasteImage">
    <h2 id="screenshot-heading">招聘截图（可选）</h2>
    <p class="muted screenshot-help">选择一张PNG或JPEG截图，最多5 MiB。单边不超过10000像素，总像素不超过1200万。</p>
    <p class="muted screenshot-help">当前支持预览和图片检查，点击“识别岗位”获取草稿；你也可以继续在下方手动填写岗位。</p>
    <VisionQuotaPanel :key="quotaRevision" :disabled="disabled || recognizing" />
    <p id="screenshot-input-help" class="muted screenshot-help">可将一张图片文件拖到此区域；点击区域空白处后，按Ctrl+V或⌘V粘贴截图。也可使用下方按钮选图。</p>
    <p v-if="dragDepth > 0" class="screenshot-drop-hint" role="status">松开以导入这张截图</p>
    <div class="screenshot-actions screenshot-choose">
      <button type="button" class="secondary-button" :disabled="disabled || recognizing" @click="fileInput?.click()">
        {{ file ? '替换截图' : '选择截图' }}
      </button>
      <button v-if="cameraAvailable" type="button" class="secondary-button" :disabled="disabled || recognizing" @click="cameraInput?.click()">
        拍照选图
      </button>
    </div>
    <input id="job-screenshot" ref="fileInput" hidden type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" :disabled="disabled || recognizing"
           aria-describedby="screenshot-privacy" @change="selectFile" />
    <input id="job-camera" ref="cameraInput" hidden type="file" accept="image/*" capture="environment" :disabled="disabled || recognizing"
           aria-describedby="screenshot-mobile-help screenshot-privacy" @change="selectFile" />
    <p id="screenshot-mobile-help" class="muted screenshot-help">手机可从系统相册或文件中选图。HEIC/HEIF照片请先导出为JPEG或PNG；长截图过大时可裁去无关内容，保留清晰正文。</p>
    <p v-if="cameraAvailable" class="muted screenshot-help">拍照选图由系统决定是否打开相机；取消、拒绝或无法拍照时，可继续使用“{{ file ? '替换截图' : '选择截图' }}”。</p>
    <p id="screenshot-privacy" class="muted screenshot-help">“检查图片”仅上传到本站且不扣次数；“识别岗位”会将图片发送至智谱视觉模型，并消耗识别额度。请先裁去个人隐私；图片不在本站长期保存。</p>
    <template v-if="file">
      <p class="screenshot-name">{{ file.name }} · {{ (file.size / 1024).toFixed(1) }} KiB</p>
      <div id="screenshot-preview-frame" class="screenshot-preview-frame" :class="{ 'is-expanded': previewExpanded }"
           :tabindex="previewExpanded ? 0 : undefined" role="region" aria-label="截图预览">
        <img :key="previewUrl" :src="previewUrl" alt="待检查的招聘截图" class="screenshot-preview"
             @load="previewLoaded" @error="previewFailed" />
      </div>
      <div class="screenshot-actions">
        <button type="button" class="secondary-button" :disabled="disabled || recognizing || !previewReady" :aria-expanded="previewExpanded"
                aria-controls="screenshot-preview-frame" @click="previewExpanded = !previewExpanded">
          {{ previewExpanded ? '收起预览' : '展开预览' }}
        </button>
        <button type="button" class="secondary-button" :disabled="disabled || recognizing" @click="clearSelection">移除截图</button>
        <button type="button" class="primary-button" :disabled="disabled || recognizing || checking || !previewReady" @click="checkImage">
          {{ checking ? '正在检查…' : '检查图片' }}
        </button>
        <button type="button" class="primary-button" :disabled="disabled || recognizing || checking || !previewReady" @click="recognizeImage">
          {{ recognizing ? '正在识别…' : '识别岗位' }}
        </button>
      </div>
      <p class="muted screenshot-help">可展开预览并滚动查看长图；请确认文字方向和清晰度。检查返回文件原始像素尺寸，可能与按方向信息显示的宽高不同。</p>
    </template>
    <p v-if="checking" class="muted screenshot-help" role="status">正在上传并检查图片，网络较慢时请稍候。你仍可替换或移除截图。</p>
    <p v-if="result" class="form-message success" role="status">图片检查通过：{{ result.format }}，{{ result.width }} × {{ result.height }}像素。可以继续手动填写岗位。</p>
    <p v-if="error" class="form-message error" role="alert">{{ error }}</p>
    <p v-if="recognizing" class="muted screenshot-help" role="status">正在识别，请等待；不会自动重试。离开页面后结果可能丢失，已预占次数不会自动返还。</p>
    <p v-if="recognitionError" class="form-message error" role="alert">{{ recognitionError }}</p>
    <RecognitionDraftPanel v-if="draft" :draft="draft" />
    <button v-if="draft" type="button" class="secondary-button" :disabled="disabled || recognizing" @click="draft = null">清除识别草稿</button>
  </section>
</template>

<style scoped>
.screenshot-panel { max-width: 840px; margin-bottom: 28px; }
.screenshot-panel.is-dragging { border-color: var(--accent); background: #edf5ed; box-shadow: 0 0 0 3px #d9e8dd; }
.screenshot-drop-hint { margin-top: 14px; color: var(--accent); font-weight: 600; }
.screenshot-help { margin-top: 12px; }
.screenshot-choose { margin-top: 20px; }
.screenshot-name { margin-top: 18px; font-size: .84rem; overflow-wrap: anywhere; }
.screenshot-preview-frame { margin-top: 16px; background: #f4f7f1; }
.screenshot-preview { display: block; width: 100%; max-height: 320px; object-fit: contain; image-orientation: from-image; }
.screenshot-preview-frame.is-expanded { max-height: 70vh; overflow: auto; overscroll-behavior: contain; }
.screenshot-preview-frame.is-expanded .screenshot-preview { height: auto; max-height: none; }
.screenshot-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 18px; }
</style>
