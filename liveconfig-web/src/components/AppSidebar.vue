<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useConsoleStore } from '../stores/console'

const router = useRouter()
const store = useConsoleStore()

const groups = [
  { label: '配置', items: [['flags', '开关管理'], ['rules', '灰度规则']] },
  { label: '验证', items: [['debug', '分流调试']] },
  { label: '运维', items: [['history', '变更历史'], ['monitor', '实时推送监控'], ['envs', '环境管理']] },
  { label: '管理', items: [['members', '成员与权限'], ['roles', '应用角色']] }
]

const icons: Record<string, string> = {
  flags: 'M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z',
  rules: 'M4 6h16M4 12h10M4 18h7',
  debug: 'M10 2v7.5L4.7 18.9A2 2 0 0 0 6.4 22h11.2a2 2 0 0 0 1.7-3.1L14 9.5V2',
  history: 'M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8',
  monitor: 'M22 12h-4l-3 9L9 3l-3 9H2',
  envs: 'M2 6h20v6H2zM2 14h20v6H2z',
  members: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2',
  roles: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path :d="icons.flags" /><line x1="4" y1="22" x2="4" y2="15" /></svg>
      </div>
      <div>
        <div class="brand-name">Liveconfig</div>
        <div class="brand-sub">功能开关中心</div>
      </div>
    </div>

    <nav class="nav">
      <template v-for="g in groups" :key="g.label">
        <div class="nav-label">{{ g.label }}</div>
        <div
          v-for="[key, name] in g.items"
          :key="key"
          class="nav-item"
          :class="{ active: $route.path === '/' + key }"
          @click="router.push('/' + key)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path :d="icons[key]" /></svg>
          {{ name }}
        </div>
      </template>
    </nav>

    <div class="sidebar-foot">
      {{ store.app?.name || '未选择应用' }}
      <span v-if="store.env"> · {{ store.env.envKey }}</span>
    </div>
  </aside>
</template>
