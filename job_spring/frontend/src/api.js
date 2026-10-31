export class ApiError extends Error {
  constructor(status, message, fields = {}, code = null) {
    super(message)
    this.status = status
    this.fields = fields
    this.code = code
  }
}

export async function request(path, options = {}, requestSettings = {}) {
  const {
    timeoutMs = 10000,
    timeoutMessage = '请求超时，请检查连接后重试。',
    timeoutCode = 'REQUEST_TIMEOUT',
  } = requestSettings
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(path, {
      ...options,
      credentials: 'same-origin',
      cache: 'no-store',
      signal: controller.signal,
      headers: { Accept: 'application/json', ...options.headers },
    })
    const body = response.status !== 204 && response.headers.get('content-type')?.includes('application/json')
      ? await response.json() : null
    if (!response.ok) throw new ApiError(response.status, body?.message || `请求失败（${response.status}）`, body?.fields, body?.code)
    return body
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error.name === 'AbortError') throw new ApiError(0, timeoutMessage, {}, timeoutCode)
    throw new ApiError(0, '暂时无法连接服务，请稍后重试。')
  } finally {
    clearTimeout(timer)
  }
}

async function sendWithCsrf(method, path, values, form = false) {
  // 登录和退出会更新 CSRF 状态，每次提交前获取当前会话的 Token。
  const csrf = await request('/api/auth/csrf')
  return request(path, {
    method,
    headers: {
      [csrf.headerName]: csrf.token,
      ...(values ? { 'Content-Type': form ? 'application/x-www-form-urlencoded' : 'application/json' } : {}),
    },
    body: values ? (form ? new URLSearchParams(values).toString() : JSON.stringify(values)) : undefined,
  })
}

export function post(path, values, form = false) {
  return sendWithCsrf('POST', path, values, form)
}

export function patch(path, values) {
  return sendWithCsrf('PATCH', path, values)
}

export function put(path, values) {
  return sendWithCsrf('PUT', path, values)
}

export function remove(path) {
  return sendWithCsrf('DELETE', path)
}

export async function postMultipart(path, formData, requestSettings = {}) {
  const csrf = await request('/api/auth/csrf', {}, {
    timeoutMessage: '上传前的连接校验超时，图片尚未发送。请检查网络后重试。',
    timeoutCode: 'UPLOAD_PRECHECK_TIMEOUT',
  })
  // 浏览器自动生成带 boundary 的 Content-Type，不能手工指定 application/json 或 multipart/form-data。
  return request(path, { method: 'POST', headers: { [csrf.headerName]: csrf.token }, body: formData }, {
    // 图片经过外网/隧道上传，比普通 JSON 请求需要更长的传输时间；仍保留有限超时，不自动重发。
    timeoutMs: 60000,
    timeoutMessage: '图片上传或检查超过60秒，请检查网络后重试，或继续手动填写。',
    timeoutCode: 'UPLOAD_TIMEOUT',
    ...requestSettings,
  })
}

export function extractScreenshot(formData) {
  return postMultipart('/api/vision/extract', formData, {
    timeoutMs: 120000,
    timeoutMessage: '识别请求超过120秒，结果和次数消耗暂时无法确认。请刷新额度后再决定是否重试，也可继续手动填写。',
    timeoutCode: 'RECOGNITION_TIMEOUT',
  })
}
