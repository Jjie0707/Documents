<script setup>
defineProps({ draft: { type: Object, required: true } })
const fields = [
  ['companyName', '公司名'], ['jobTitle', '岗位名'], ['city', '城市'], ['salary', '薪资'],
  ['responsibilities', '岗位职责'], ['requirements', '任职要求'], ['educationRequirement', '学历要求'],
  ['sourcePlatform', '来源平台'], ['originalUrl', '原始链接'], ['applicationDeadline', '投递截止日期'],
]
</script>

<template>
  <section class="recognition-draft" aria-labelledby="recognition-draft-heading">
    <h3 id="recognition-draft-heading">识别草稿</h3>
    <p class="muted">请对照截图检查，模型可能识别错误或遗漏。草稿尚未保存，也不会覆盖下方已填写内容；目前可对照草稿手动填写。</p>
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
