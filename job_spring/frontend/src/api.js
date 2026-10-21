export class ApiError extends Error {
  constructor(status, message, fields = {}, code = null) {
    super(message)
    this.status = status
    this.fields = fields
    this.code = code
  }
}

export async function request(path, options = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 10000)
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
    throw new ApiError(0, error.name === 'AbortError' ? '请求超时，请检查连接后重试。' : '暂时无法连接服务，请稍后重试。')
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
