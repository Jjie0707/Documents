import { readonly, ref } from 'vue'
import { post, request } from './api.js'

const currentUser = ref(null)
let pending
export const user = readonly(currentUser)
export function clearUser() { currentUser.value = null }

export async function fetchCurrentUser() {
  if (!pending) {
    pending = request('/api/auth/me')
      .then(value => { currentUser.value = value; return value })
      .catch(error => {
        if (error.status === 401) { clearUser(); return null }
        throw error
      })
      .finally(() => { pending = null })
  }
  return pending
}

export async function login(username, password) {
  const result = await post('/api/auth/login', { username, password }, true)
  currentUser.value = result
  return result
}

export async function logout() {
  await post('/api/auth/logout')
  clearUser()
}
