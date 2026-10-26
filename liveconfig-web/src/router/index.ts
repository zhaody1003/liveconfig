import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true, bare: true } },
    { path: '/apps', component: () => import('../views/AppsView.vue'), meta: { bare: true } },
    { path: '/flags', component: () => import('../views/FlagsView.vue') },
    { path: '/rules', component: () => import('../views/RulesView.vue') },
    { path: '/debug', component: () => import('../views/DebugView.vue') },
    { path: '/history', component: () => import('../views/HistoryView.vue') },
    { path: '/monitor', component: () => import('../views/MonitorView.vue') },
    { path: '/envs', component: () => import('../views/EnvsView.vue') },
    { path: '/members', component: () => import('../views/MembersView.vue') },
    { path: '/roles', component: () => import('../views/RolesView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/apps' }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('liveconfig_token')
  if (!token && !to.meta.public) return '/login'
  if (token && to.path === '/login') return '/apps'
  return true
})

export default router
