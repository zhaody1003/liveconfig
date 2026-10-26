<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type ChangeLog } from '../api'
import { useConsoleStore } from '../stores/console'

const store = useConsoleStore()
const records = ref<ChangeLog[]>([])
const total = ref(0)
const flagKey = ref<string | undefined>(undefined)
const loading = ref(false)

async function load() {
  if (!store.currentApp || !store.currentEnv) return
  loading.value = true
  try {
    const page = await api.history(store.currentApp, store.currentEnv, flagKey.value, 1, 50)
    records.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function rollback(log: ChangeLog) {
  await api.rollback(log.id)
  ElMessage.success(`已回滚到 v${log.configVersion}，并追加一条 ROLLBACK 记录`)
  await load()
  await store.refreshEnvs()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h2>变更历史</h2><p>{{ store.currentApp }} · {{ store.currentEnv }} · 只增不删，回滚也是追加</p></div>
      <select v-model="flagKey" class="input" @change="load">
        <option :value="undefined">全部开关</option>
      </select>
    </div>

    <div class="card tl">
      <div v-if="!records.length" class="table-empty">{{ loading ? '加载中…' : '暂无变更记录' }}</div>
      <div v-for="(r, i) in records" :key="r.id" class="tl-item">
        <div class="tl-rail">
          <div class="tl-dot" :style="{ background: r.action === 'ROLLBACK' ? 'var(--amber)' : 'var(--accent)' }"></div>
          <div v-if="i < records.length - 1" class="tl-line"></div>
        </div>
        <div class="tl-body">
          <div class="tl-title"><b>{{ r.actor }}</b> {{ r.action }} · <span class="mono">{{ r.flagKey }}</span></div>
          <div class="tl-meta">v{{ r.configVersion }} · {{ (r.createdAt || '').replace('T', ' ').slice(0, 19) }}</div>
          <div class="diff">
            <div v-if="r.beforeJson" class="del">- {{ r.beforeJson }}</div>
            <div v-if="r.afterJson" class="add">+ {{ r.afterJson }}</div>
          </div>
        </div>
        <button class="btn btn-ghost btn-sm" @click="rollback(r)">回滚到此版本</button>
      </div>
    </div>
  </div>
</template>
