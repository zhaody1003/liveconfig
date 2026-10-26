import { defineStore } from 'pinia'
import { api, type AppItem, type EnvItem, type LoginResult } from '../api'

export const useConsoleStore = defineStore('console', {
  state: () => ({
    user: null as LoginResult | null,
    apps: [] as AppItem[],
    envs: [] as EnvItem[],
    currentApp: null as string | null,
    currentEnv: null as string | null
  }),
  getters: {
    hasApp: (s) => !!s.currentApp,
    app: (s) => s.apps.find((a) => a.appKey === s.currentApp) || null,
    /** 成员矩阵只该列出当前应用的环境，别的应用的列是噪音 */
    appEnvs: (s) => s.envs,
    env: (s) => s.envs.find((e) => e.envKey === s.currentEnv) || null
  },
  actions: {
    setUser(user: LoginResult | null) {
      this.user = user
    },
    async loadApps(q?: string) {
      this.apps = await api.apps(q)
    },
    async enter(appKey: string) {
      this.currentApp = appKey
      this.envs = await api.envs(appKey)
      if (!this.envs.some((e) => e.envKey === this.currentEnv)) {
        this.currentEnv = this.envs[0]?.envKey || null
      }
    },
    leaveApp() {
      this.currentApp = null
      this.currentEnv = null
      this.envs = []
    },
    setEnv(envKey: string) {
      this.currentEnv = envKey
    },
    async refreshEnvs() {
      if (this.currentApp) this.envs = await api.envs(this.currentApp)
    }
  }
})
