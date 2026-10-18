import { createRouter, createWebHistory } from 'vue-router'
import JobsView from './views/JobsView.vue'
import JobPreviewView from './views/JobPreviewView.vue'
import NotFoundView from './views/NotFoundView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'jobs', component: JobsView },
    { path: '/jobs/preview', name: 'job-preview', component: JobPreviewView },
    { path: '/:pathMatch(.*)*', component: NotFoundView },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
