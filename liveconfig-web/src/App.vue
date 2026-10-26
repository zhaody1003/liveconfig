<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppSidebar from './components/AppSidebar.vue'
import AppTopbar from './components/AppTopbar.vue'
import { useConsoleStore } from './stores/console'
import { api } from './api'

const route = useRoute()
const router = useRouter()
const store = useConsoleStore()
const menuOpen = ref<'app' | 'env' | 'profile' | null>(null)

const bare = computed(() => !!route.meta.bare)

onMounted(async () => {
  document.addEventListener('click', closeMenus)
  if (localStorage.getItem('liveconfig_token') && !store.apps.length) {
    try {
      await store.loadApps()
      if (store.apps.length) await store.enter(store.apps[0].appKey)
    } catch {
      /* 会话失效由拦截器处理 */
    }
  } else if (!bare.value && store.currentApp) {
    await store.refreshEnvs()
  }
})

onBeforeUnmount(() => document.removeEventListener('click', closeMenus))

function closeMenus() {
  menuOpen.value = null
}

function toggleMenu(which: 'app' | 'env' | 'profile') {
  menuOpen.value = menuOpen.value === which ? null : which
}

async function logout() {
  try {
    await api.logout()
  } finally {
    localStorage.removeItem('liveconfig_token')
    store.setUser(null)
    store.leaveApp()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

</script>

<template>
  <div class="app" :class="{ landing: route.path === '/apps' }">
    <AppSidebar v-if="!bare" />
    <div v-if="!bare" class="main">
      <AppTopbar :menu-open="menuOpen" @toggle="toggleMenu" @close-menu="closeMenus" @logout="logout" />
      <div class="content">
        <div class="proto-note">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
          Liveconfig · 前后端分离，数据来自 MySQL
        </div>
        <router-view />
      </div>
    </div>
    <router-view v-else />
  </div>
</template>
