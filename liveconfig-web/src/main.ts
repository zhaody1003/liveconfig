import { createApp, markRaw } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/prototype.css'
import './styles/app.css'
import router from './router'
import App from './App.vue'
import { api } from './api'
import { useConsoleStore } from './stores/console'

async function bootstrap() {
  const pinia = createPinia()
  pinia.use(({ store }) => {
    // store 里的数组会被路由与表格反复读取，整体标为原始对象避免逐层代理
    Object.keys(store.$state).forEach((key) => {
      const value = (store.$state as Record<string, unknown>)[key]
      if (Array.isArray(value)) (store.$state as Record<string, unknown>)[key] = markRaw(value)
    })
  })
  const app = createApp(App)
  app.use(pinia).use(router).use(ElementPlus)

  if (localStorage.getItem('liveconfig_token')) {
    const store = useConsoleStore(pinia)
    try {
      await store.loadApps()
      if (store.apps.length) {
        await store.enter(store.apps[0].appKey)
      }
    } catch {
      /* 会话过期时由 axios 拦截器跳登录 */
    }
  }

  app.mount('#app')
}

bootstrap()
