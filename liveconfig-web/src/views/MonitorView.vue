<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useConsoleStore } from '../stores/console'
import http from '../api/request'

const store = useConsoleStore()
const online = ref(0)
const configVersion = ref(0)
const events = ref<{ t: string; ev: string; detail: string }[]>([])
let source: EventSource | null = null
let timer: number | undefined

const now = () => new Date().toTimeString().slice(0, 8)

function push(ev: string, detail: string) {
  events.value.unshift({ t: now(), ev, detail })
  if (events.value.length > 30) events.value.length = 30
}

function open() {
  if (!store.currentApp || !store.currentEnv) return
  const token = localStorage.getItem('liveconfig_token')
  source?.close()
  source = new EventSource(
    `/api/monitor/stream?appKey=${store.currentApp}&envKey=${store.currentEnv}&token=${token}`
  )
  source.addEventListener('hello', (e: MessageEvent) => {
    const d = JSON.parse(e.data)
    configVersion.value = d.configVersion
    push('hello', `已订阅 ${d.appKey}/${d.envKey} · v${d.configVersion}`)
  })
  source.addEventListener('config-change', (e: MessageEvent) => {
    const d = JSON.parse(e.data)
    configVersion.value = d.configVersion
    push('config-change', `${(d.changedKeys || []).join(', ')} → v${d.configVersion}（已推送到 ${online.value} 个连接）`)
  })
  source.addEventListener('heartbeat', () => push('heartbeat', 'keep-alive ping'))
  source.onerror = () => push('error', '连接中断，浏览器将自动重连')
}

async function loadOverview() {
  if (!store.currentApp || !store.currentEnv) return
  const res = await http.get('/api/monitor/overview', {
    params: { appKey: store.currentApp, envKey: store.currentEnv }
  })
  online.value = (res.data.data as { onlineSdk: number }).onlineSdk
  configVersion.value = (res.data.data as { configVersion: number }).configVersion
}

onMounted(async () => {
  await loadOverview()
  open()
  timer = window.setInterval(loadOverview, 5000)
})

onBeforeUnmount(() => {
  source?.close()
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h2>实时推送监控</h2><p>SSE 长连接与配置推送的实时状态</p></div>
      <span class="live"><span class="pulse"></span>已连接 · text/event-stream</span>
    </div>

    <div class="stats">
      <div class="stat"><div class="n" style="color:var(--accent)">{{ online }}</div><div class="l">在线 SDK 连接</div></div>
      <div class="stat"><div class="n" style="color:var(--amber)">{{ configVersion }}</div><div class="l">环境配置版本</div></div>
      <div class="stat"><div class="n">{{ store.envs.length }}</div><div class="l">环境数</div></div>
      <div class="stat"><div class="n" style="color:var(--green)">{{ store.app?.flagCount ?? 0 }}</div><div class="l">应用开关总数</div></div>
    </div>

    <div class="card">
      <div style="padding:13px 18px;border-bottom:1px solid var(--border);font-weight:600;font-size:13.5px">
        事件流（本浏览器连接）
      </div>
      <div v-if="!events.length" class="table-empty">等待事件…改一次开关默认值就会看到 config-change</div>
      <div v-for="(e, i) in events" :key="i" class="logline">
        <span class="t">{{ e.t }}</span><span class="ev">{{ e.ev }}</span><span>{{ e.detail }}</span>
      </div>
    </div>

    <div class="hint" style="margin-top:10px">
      在线数口径 = 该环境存活的 SSE 连接数（一个应用实例一条），通常≈机器数，但单机多进程、同宿主机多容器都算多个。
    </div>
  </div>
</template>
