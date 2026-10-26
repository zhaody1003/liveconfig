<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { api, type EnvItem, type FlagRow } from '../api'
import { useConsoleStore } from '../stores/console'
import http from '../api/request'

const store = useConsoleStore()
const flags = ref<FlagRow[]>([])
const creating = ref(false)
const form = ref({ flagKey: '', name: '', valueType: 'BOOLEAN', defaultValue: 'false' })
const envCreating = ref(false)
const envForm = ref({ envKey: '', color: '#0ea5e9', highRisk: false })
const revealed = ref<EnvItem & { serverKeyPlain?: string; clientKeyPlain?: string } | null>(null)
const online = ref(0)
const configVersion = ref(0)
const events = ref<{ t: string; ev: string; detail: string }[]>([])
let source: EventSource | null = null

const now = () => new Date().toTimeString().slice(0, 8)

async function load() {
  if (!store.currentApp || !store.currentEnv) return
  flags.value = await api.flags(store.currentApp, store.currentEnv)
}

async function create() {
  if (!store.currentApp || !store.currentEnv) return
  try {
    await api.createFlag({
      appKey: store.currentApp,
      envKey: store.currentEnv,
      flagKey: form.value.flagKey,
      name: form.value.name,
      valueType: form.value.valueType,
      defaultValue: form.value.defaultValue,
      enabled: true
    })
    creating.value = false
    form.value = { flagKey: '', name: '', valueType: 'BOOLEAN', defaultValue: 'false' }
    await load()
    await store.refreshEnvs()
  } catch {
    /* 拦截器已提示类型校验等原因 */
  }
}

function openStream() {
  if (!store.currentApp || !store.currentEnv) return
  const token = localStorage.getItem('liveconfig_token')
  const url = `/api/monitor/stream?appKey=${store.currentApp}&envKey=${store.currentEnv}&token=${token}`
  source?.close()
  source = new EventSource(url)
  source.addEventListener('hello', (e: MessageEvent) => {
    const data = JSON.parse(e.data)
    configVersion.value = data.configVersion
    events.value.unshift({ t: now(), ev: 'hello', detail: `已订阅 ${data.appKey}/${data.envKey} v${data.configVersion}` })
  })
  source.addEventListener('config-change', (e: MessageEvent) => {
    const data = JSON.parse(e.data)
    configVersion.value = data.configVersion
    events.value.unshift({ t: now(), ev: 'config-change', detail: `${data.changedKeys.join(', ')} → v${data.configVersion}` })
    load()
  })
  source.addEventListener('heartbeat', () => {
    events.value.unshift({ t: now(), ev: 'heartbeat', detail: 'keep-alive ping' })
  })
  source.onerror = () => {
    events.value.unshift({ t: now(), ev: 'error', detail: '连接中断，浏览器将自动重连' })
  }
  if (events.value.length > 8) events.value.length = 8
}

async function createEnv() {
  if (!store.currentApp) return
  const created = await api.createEnv(store.currentApp, envForm.value)
  envCreating.value = false
  envForm.value = { envKey: '', color: '#0ea5e9', highRisk: false }
  await store.refreshEnvs()
  revealed.value = { ...created.env, serverKeyPlain: created.serverKeyPlain, clientKeyPlain: created.clientKeyPlain }
}

async function revealKeys(envKey: string) {
  if (!store.currentApp) return
  const created = await api.resetKeys(store.currentApp, envKey)
  await store.refreshEnvs()
  revealed.value = { ...created.env, serverKeyPlain: created.serverKeyPlain, clientKeyPlain: created.clientKeyPlain }
}

async function removeEnv(envKey: string) {
  if (!store.currentApp) return
  try {
    await api.deleteEnv(store.currentApp, envKey)
    await store.refreshEnvs()
    if (store.currentEnv === envKey) store.setEnv(store.envs[0]?.envKey || '')
  } catch {
    /* 有开关时后端返回 409，拦截器已提示 */
  }
}

async function loadOnline() {
  if (!store.currentApp || !store.currentEnv) return
  const data = await http.get('/api/monitor/overview', {
    params: { appKey: store.currentApp, envKey: store.currentEnv }
  })
  online.value = (data.data as { onlineSdk: number }).onlineSdk
  configVersion.value = (data.data as { configVersion: number }).configVersion
}

let timer: number | undefined

onMounted(async () => {
  await load()
  openStream()
  await loadOnline()
  timer = window.setInterval(loadOnline, 5000)
})

onBeforeUnmount(() => {
  source?.close()
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h2>环境管理</h2><p>{{ store.currentApp }} · 环境为可创建资源，每个环境有独立 SDK Key</p></div>
    </div>

    <div class="stats">
      <div class="stat"><div class="n">{{ flags.length }}</div><div class="l">开关总数</div></div>
      <div class="stat"><div class="n" style="color:var(--green)">{{ flags.filter(f => f.enabled).length }}</div><div class="l">已启用</div></div>
      <div class="stat"><div class="n" style="color:var(--accent)">{{ online }}</div><div class="l">在线 SDK 连接</div></div>
      <div class="stat"><div class="n" style="color:var(--amber)">{{ configVersion }}</div><div class="l">环境配置版本</div></div>
    </div>

    <div class="env-cards" style="margin-bottom:18px">
      <div v-for="e in store.appEnvs" :key="e.envKey" class="env-card">
        <div class="env-card-top">
          <span class="env-dot" :style="{ background: e.color }"></span><b>{{ e.envKey }}</b>
          <span v-if="e.highRisk" class="pill pill-num" style="margin-left:auto">高危</span>
        </div>
        <div class="sub">v{{ e.configVersion }} · {{ e.flagCount }} 开关 · 在线 SDK {{ e.onlineSdk }}</div>
        <div class="keyrow"><span class="lbl">Server</span><span class="k">{{ e.serverKeyMasked || '—' }}</span></div>
        <div class="keyrow"><span class="lbl">Client</span><span class="k">{{ e.clientKeyMasked || '—' }}</span></div>
        <div class="env-stat">
          <button class="btn btn-ghost btn-sm" @click="revealKeys(e.envKey)">重置 SDK Key</button>
          <button class="btn btn-danger-ghost btn-sm" @click="removeEnv(e.envKey)">删除</button>
        </div>
      </div>
      <div class="env-card" style="display:flex;align-items:center;justify-content:center;border-style:dashed;cursor:pointer" @click="envCreating = true">
        <span class="sub">+ 新建环境</span>
      </div>
    </div>

    <div class="card">
      <div style="padding:13px 18px;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center">
        <span style="font-weight:600;font-size:13.5px">SSE 事件流</span>
        <span class="live"><span class="pulse"></span>已连接 · text/event-stream</span>
      </div>
      <div>
        <div v-if="!events.length" class="table-empty">等待事件…</div>
        <div v-for="(e, i) in events" :key="i" class="logline">
          <span class="t">{{ e.t }}</span><span class="ev">{{ e.ev }}</span><span>{{ e.detail }}</span>
        </div>
      </div>
    </div>

  </div>
</template>
