<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useConsoleStore } from '../stores/console'
import { api, type AppItem } from '../api'

const router = useRouter()
const store = useConsoleStore()
const keyword = ref('')
const apps = ref<AppItem[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    await store.loadApps(keyword.value || undefined)
    apps.value = store.apps
  } finally {
    loading.value = false
  }
}

async function enter(appKey: string) {
  await store.enter(appKey)
  router.push('/flags')
}

onMounted(load)
</script>

<template>
  <div class="landing-page">
    <div class="landing-top">
      <div class="brand">
        <div class="brand-mark">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" /><line x1="4" y1="22" x2="4" y2="15" /></svg>
        </div>
        <div>
          <div class="brand-name">Liveconfig</div>
          <div class="brand-sub">功能开关中心</div>
        </div>
      </div>
      <div class="avatar" title="退出登录" @click="router.push('/login')">L</div>
    </div>

    <div class="landing-wrap">
      <div class="page-head">
        <div>
          <h2>应用</h2>
          <p>接入功能开关的微服务应用 · 选择一个应用进入管理其开关</p>
        </div>
        <button class="btn btn-primary" @click="load">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
          接入新应用
        </button>
      </div>

      <div class="app-search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
        <input v-model="keyword" class="input" placeholder="搜索应用名称 / 标识 / 团队" @input="load" />
      </div>

      <div v-if="loading" class="table-empty">加载中…</div>
      <div v-else-if="!apps.length" class="table-empty">没有匹配的应用</div>
      <div v-else class="app-grid">
        <div v-for="a in apps" :key="a.appKey" class="app-card" @click="enter(a.appKey)">
          <div class="app-card-top">
            <div class="app-ico" :style="{ background: `linear-gradient(135deg, ${a.color}, ${a.color}b8)` }">
              {{ a.name.slice(0, 1) }}
            </div>
            <div class="nm"><b>{{ a.name }}</b><span class="mono">{{ a.appKey }}</span></div>
          </div>
          <div class="sub desc">{{ a.owner }}</div>
          <div class="app-card-foot">
            <span>开关 <b>{{ a.flagCount }}</b></span>
            <span>环境 <b>{{ a.envCount }}</b></span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.landing-page { width: 100%; }
</style>
