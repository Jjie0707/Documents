<script setup>
import { recognitionFields as fields } from '../recognition-prefill.js'
defineProps({ draft: { type: Object, required: true } })
</script>

<template>
  <section class="recognition-draft" aria-labelledby="recognition-draft-heading">
    <h3 id="recognition-draft-heading">识别草稿</h3>
    <p class="muted">请对照截图检查，模型可能识别错误或遗漏。点击“填入岗位表单”后可继续修改；有冲突时需确认，未识别字段保留原输入。最后点击“保存岗位”才会保存。</p>
    <dl>
      <template v-for="[key, label] in fields" :key="key">
        <dt>{{ label }}</dt>
        <dd :class="{ muted: !draft[key] }">{{ draft[key] || '未识别，可自行补充' }}</dd>
      </template>
    </dl>
  </section>
</template>

<style scoped>
.recognition-draft { margin-top: 24px; border-top: 1px solid #d9e2d9; padding-top: 20px; }
.recognition-draft p { margin-top: 10px; }
dl { margin-top: 16px; }
dt { font-weight: 600; margin-top: 14px; }
dd { margin: 6px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
