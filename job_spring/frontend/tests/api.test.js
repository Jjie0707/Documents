import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { test } from 'node:test'
import { ApiError, postMultipart, request } from '../src/api.js'

const uploadPath = '/api/vision/images/validate'
const jsonResponse = (value, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: new Headers({ 'Content-Type': 'application/json' }),
  json: async () => value,
})
const flushPromises = async () => {
  for (let index = 0; index < 12; index++) await Promise.resolve()
}
const waitForAbort = signal => new Promise((resolve, reject) => {
  signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true })
})

test('ordinary requests still abort after ten seconds', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  t.mock.method(globalThis, 'fetch', async (path, options) => waitForAbort(options.signal))
  const failure = assert.rejects(request('/api/auth/me'), error =>
    error instanceof ApiError && error.status === 0 && error.code === 'REQUEST_TIMEOUT')
  await flushPromises()
  t.mock.timers.tick(10000)
  await failure
})

test('a fifteen-second upload succeeds with CSRF and browser-generated multipart headers', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const token = randomUUID()
  let uploadOptions
  let csrfOptions
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    if (path === '/api/auth/csrf') {
      csrfOptions = options
      return jsonResponse({ headerName: 'X-CSRF-TOKEN', token })
    }
    assert.equal(path, uploadPath)
    uploadOptions = options
    return Promise.race([
      new Promise(resolve => setTimeout(() => resolve(jsonResponse({ format: 'PNG', width: 320, height: 240 })), 15000)),
      waitForAbort(options.signal),
    ])
  })
  const form = new FormData()
  form.append('image', new Blob(['synthetic test bytes'], { type: 'image/png' }), 'virtual.png')
  const upload = postMultipart(uploadPath, form)
  await flushPromises()
  t.mock.timers.tick(10001)
  assert.equal(uploadOptions.signal.aborted, false)
  t.mock.timers.tick(4999)
  assert.deepEqual(await upload, { format: 'PNG', width: 320, height: 240 })
  assert.equal(uploadOptions.headers['X-CSRF-TOKEN'], token)
  assert.equal(uploadOptions.headers['Content-Type'], undefined)
  assert.equal(uploadOptions.body, form)
  assert.equal(uploadOptions.method, 'POST')
  assert.equal(uploadOptions.credentials, 'same-origin')
  assert.equal('timeoutMs' in uploadOptions, false)
  // 请求完成后必须清理计时器，不能稍后再触发 AbortSignal。
  t.mock.timers.tick(60000)
  assert.equal(uploadOptions.signal.aborted, false)
  assert.equal(csrfOptions.signal.aborted, false)
})

test('upload still has a finite sixty-second timeout with a specific error', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  t.mock.method(globalThis, 'fetch', async (path, options) => path === '/api/auth/csrf'
    ? jsonResponse({ headerName: 'X-CSRF-TOKEN', token: randomUUID() })
    : waitForAbort(options.signal))
  const failure = assert.rejects(postMultipart(uploadPath, new FormData()), error =>
    error instanceof ApiError && error.status === 0 && error.code === 'UPLOAD_TIMEOUT' && error.message.includes('60秒'))
  await flushPromises()
  t.mock.timers.tick(60000)
  await failure
})

test('CSRF timeout says the image was not sent and does not start an upload', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const paths = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    paths.push(path)
    return waitForAbort(options.signal)
  })
  const failure = assert.rejects(postMultipart(uploadPath, new FormData()), error =>
    error instanceof ApiError && error.code === 'UPLOAD_PRECHECK_TIMEOUT' && error.message.includes('尚未发送'))
  await flushPromises()
  t.mock.timers.tick(10000)
  await failure
  assert.deepEqual(paths, ['/api/auth/csrf'])
})

test('image rejection remains a backend error rather than becoming a timeout', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  let signal
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    if (path === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: randomUUID() })
    signal = options.signal
    return jsonResponse({ code: 'UNSUPPORTED_IMAGE_FORMAT', message: '仅支持PNG或JPEG' }, 415)
  })
  await assert.rejects(postMultipart(uploadPath, new FormData()), error =>
    error instanceof ApiError && error.status === 415 && error.code === 'UNSUPPORTED_IMAGE_FORMAT')
  t.mock.timers.tick(60000)
  assert.equal(signal.aborted, false)
})

test('recognition waits up to 120 seconds and never retries an uncertain result', async t => {
  const { extractScreenshot } = await import('../src/api.js')
  t.mock.timers.enable({ apis: ['setTimeout'] })
  let calls = 0
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    if (path === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: randomUUID() })
    assert.equal(path, '/api/vision/extract')
    calls++
    return waitForAbort(options.signal)
  })
  const failure = assert.rejects(extractScreenshot(new FormData()), error =>
    error instanceof ApiError && error.code === 'RECOGNITION_TIMEOUT' && error.message.includes('次数消耗暂时无法确认'))
  await flushPromises()
  t.mock.timers.tick(60000)
  assert.equal(calls, 1)
  t.mock.timers.tick(60000)
  await failure
  assert.equal(calls, 1)
})
