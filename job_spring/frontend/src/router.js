import { createRouter, createWebHistory } from 'vue-router'
import JobsView from './views/JobsView.vue'
import JobDetailView from './views/JobDetailView.vue'
import AuthView from './views/AuthView.vue'
import NotFoundView from './views/NotFoundView.vue'
import { fetchCurrentUser } from './auth.js'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'jobs', component: JobsView, meta: { requiresAuth: true } },
    { path: '/jobs/:id(\\d+)', name: 'job-detail', component: JobDetailView, meta: { requiresAuth: true } },
    { path: '/login', name: 'login', component: AuthView, meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: AuthView, meta: { guestOnly: true } },
    { path: '/:pathMatch(.*)*', component: NotFoundView },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async to => {
  if (!to.meta.requiresAuth && !to.meta.guestOnly) return true
  try {
    const current = await fetchCurrentUser()
    if (to.meta.requiresAuth && !current) return { name: 'login' }
    if (to.meta.guestOnly && current) return { name: 'jobs' }
  } catch {
    if (to.meta.requiresAuth) return { name: 'login' }
  }
})
export default router
