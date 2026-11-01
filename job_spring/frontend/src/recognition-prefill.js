// 仅提取岗位内容字段；识别结果不能决定归属、主键或状态。
export const recognitionFields = [
  ['companyName', '公司名'], ['jobTitle', '岗位名'], ['city', '城市'], ['salary', '薪资'],
  ['responsibilities', '岗位职责'], ['requirements', '任职要求'], ['educationRequirement', '学历要求'],
  ['sourcePlatform', '来源平台'], ['originalUrl', '原始链接'], ['applicationDeadline', '投递截止日期'],
]

export function snapshotRecognition(draft) {
  const snapshot = {}
  for (const [key] of recognitionFields) {
    if (typeof draft?.[key] === 'string' && draft[key].trim()) snapshot[key] = draft[key].trim()
  }
  return snapshot
}

export function recognitionConflicts(form, draft) {
  const snapshot = snapshotRecognition(draft)
  return recognitionFields.filter(([key]) => snapshot[key] && form[key]?.trim()
    && form[key].trim() !== snapshot[key])
}

// 返回补丁而不修改表单：取消确认时没有任何字段需要回滚。
export function recognitionPatch(form, draft, overwrite = false) {
  const patch = {}
  for (const [key, value] of Object.entries(snapshotRecognition(draft))) {
    if (!form[key]?.trim() || (overwrite && form[key].trim() !== value)) patch[key] = value
  }
  return patch
}
