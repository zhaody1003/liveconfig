<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useConsoleStore } from '../stores/console'

defineProps<{ menuOpen: 'app' | 'env' | 'profile' | null }>()
const emit = defineEmits<{
  (e: 'toggle', which: 'app' | 'env' | 'profile'): void
  (e: 'closeMenu'): void
  (e: 'logout'): void
}>()

const route = useRoute()
const router = useRouter()
const store = useConsoleStore()

/** 环境是「数据浏览上下文」，只在数据页显示；进管理页自动收起，避免被误认为影响权限范围 */
const ENV_PAGES = ['/flags', '/rules', '/debug', '/history', '/monitor']
const showEnv = computed(() => ENV_PAGES.includes(route.path))

async function pickApp(appKey: string) {
  await store.enter(appKey)
  emit('closeMenu')
  router.push('/flags')
}

function allApps() {
  store.leaveApp()
  emit('closeMenu')
  router.push('/apps')
}

function pickEnv(envKey: string) {
  store.setEnv(envKey)
  emit('closeMenu')
}
</script>

<template>
  <div class="topbar">
    <div class="crumb">
      <span>功能开关</span><span>/</span><b>{{ store.app?.name || '未选择应用' }}</b>
    </div>

    <div class="topbar-right">
      <div class="env-dd app-dd" @click.stop>
        <button class="env-btn" @click="emit('toggle', 'app')">
          <span class="env-dot" :style="{ background: store.app?.color || '#c7cbd6' }"></span>
          <span>{{ store.app?.name || '选择应用' }}</span>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9" /></svg>
        </button>
        <div v-if="menuOpen === 'app'" class="env-menu show">
          <div
            v-for="a in store.apps"
            :key="a.appKey"
            class="env-opt"
            :class="{ sel: a.appKey === store.currentApp }"
            @click="pickApp(a.appKey)"
          >
            <span class="env-dot" :style="{ background: a.color }"></span>
            {{ a.name }}
            <span class="cnt">{{ a.flagCount }} 开关</span>
          </div>
          <hr />
          <div class="env-manage" @click="allApps">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></svg>
            全部应用
          </div>
        </div>
      </div>

      <div v-if="showEnv && store.envs.length" class="env-dd" @click.stop>
        <button class="env-btn" @click="emit('toggle', 'env')">
          <span class="env-dot" :style="{ background: store.env?.color }"></span>
          <span>{{ store.currentEnv }}</span>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9" /></svg>
        </button>
        <div v-if="menuOpen === 'env'" class="env-menu show">
          <div
            v-for="e in store.envs"
            :key="e.envKey"
            class="env-opt"
            :class="{ sel: e.envKey === store.currentEnv }"
            @click="pickEnv(e.envKey)"
          >
            <span class="env-dot" :style="{ background: e.color }"></span>
            {{ e.envKey }}
            <span class="cnt">{{ e.flagCount }} 开关</span>
          </div>
          <hr />
          <div class="env-manage" @click="allApps(); router.push('/envs')">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>
            管理环境
          </div>
        </div>
      </div>

      <div class="profile-dd" @click.stop>
        <div class="avatar" @click="emit('toggle', 'profile')">L</div>
        <div v-if="menuOpen === 'profile'" class="profile-menu show">
          <div class="profile-hd">
            <div class="avatar">L</div>
            <div>
              <div class="nm">{{ store.user?.name || '当前用户' }}</div>
              <div class="em">{{ store.user?.email }}</div>
            </div>
          </div>
          <div class="profile-info">
            <div class="profile-row"><span class="k">平台角色</span><span class="v">{{ store.user?.platformRole === 'ADMIN' ? '平台管理员' : '普通用户' }}</span></div>
            <div class="profile-row"><span class="k">当前应用</span><span class="v">{{ store.app?.name || '—' }}</span></div>
          </div>
          <div class="profile-foot">
            <div class="profile-logout" @click="emit('logout')">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" /></svg>
              退出登录
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
