import test from 'node:test'
import assert from 'node:assert/strict'
import { recognitionConflicts, recognitionPatch, snapshotRecognition } from '../src/recognition-prefill.js'

test('only ten content fields enter a detached snapshot; absent and non-text values are ignored', () => {
  const draft = { companyName: ' 示例公司 ', jobTitle: null, city: '', salary: 123,
    userId: '99', id: '1', status: 'OFFER', responsibilities: ['bad'], applicationDeadline: '2026-12-01' }
  const snapshot = snapshotRecognition(draft)
  draft.companyName = 'later response'
  assert.deepEqual(snapshot, { companyName: '示例公司', applicationDeadline: '2026-12-01' })
  assert.deepEqual(snapshotRecognition(null), {})
})

test('partial recognition fills blanks and never erases manual fields', () => {
  const form = { companyName: '手动公司', jobTitle: ' ', city: '北京', originalUrl: 'https://example.com/job' }
  const patch = recognitionPatch(form, { companyName: null, jobTitle: 'Java', city: null, originalUrl: '' }, true)
  assert.deepEqual(patch, { jobTitle: 'Java' })
  assert.equal(form.jobTitle, ' ')
  assert.equal(form.originalUrl, 'https://example.com/job')
})

test('conflicts include only different nonempty fields; equal values preserve original formatting', () => {
  const form = { companyName: '我的公司', jobTitle: ' Java ', city: '' }
  const draft = { companyName: '模型公司', jobTitle: 'Java', city: '上海' }
  assert.deepEqual(recognitionConflicts(form, draft), [['companyName', '公司名']])
  assert.deepEqual(recognitionPatch(form, draft), { city: '上海' })
  assert.deepEqual(recognitionPatch(form, draft, true), { companyName: '模型公司', city: '上海' })
  assert.equal(form.companyName, '我的公司') // 展示确认/取消都不改动表单。
})

test('re-recognition protects edited fields and the first draft snapshot', () => {
  const first = snapshotRecognition({ jobTitle: 'Java', requirements: 'Java\nSQL' })
  const form = { ...recognitionPatch({}, first), jobTitle: '用户修改的岗位' }
  const second = { jobTitle: 'Python', requirements: null, salary: '面议' }
  assert.deepEqual(recognitionConflicts(form, second), [['jobTitle', '岗位名']])
  Object.assign(form, recognitionPatch(form, second))
  assert.equal(form.jobTitle, '用户修改的岗位')
  assert.equal(form.requirements, 'Java\nSQL')
  assert.equal(form.salary, '面议')
  assert.equal(first.jobTitle, 'Java')
})

test('all ten fields, multiline text, dates and literal HTML can be copied without execution', () => {
  const draft = { companyName: '公司', jobTitle: '岗位', city: '上海', salary: '面议',
    responsibilities: '开发接口\n编写测试', requirements: 'Java 😀', educationRequirement: '本科',
    sourcePlatform: '<script>text</script>', originalUrl: 'https://example.com/job', applicationDeadline: '2026-12-01' }
  assert.deepEqual(recognitionPatch({}, draft), draft)
  assert.deepEqual(recognitionConflicts(draft, draft), [])
  assert.deepEqual(recognitionPatch(draft, draft, true), {})
})
