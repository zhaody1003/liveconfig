<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { useConsoleStore } from '../stores/console'

const router = useRouter()
const store = useConsoleStore()
const email = ref('zhangsan@example.com')
const password = ref('demo1234')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    const result = await api.login(email.value, password.value)
    localStorage.setItem('liveconfig_token', result.token)
    await store.loadApps()
    if (store.apps.length) await store.enter(store.apps[0].appKey)
    router.push('/apps')
  } catch (e) {
    ElMessage.error((e as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-screen">
    <div class="login-card">
      <div class="login-brand">
        <div class="brand-mark">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" /><line x1="4" y1="22" x2="4" y2="15" /></svg>
        </div>
        <div>
          <div class="brand-name">Liveconfig</div>
          <div class="brand-sub">功能开关中心</div>
        </div>
      </div>
      <h2 class="login-title">登录</h2>
      <div class="field">
        <label>邮箱</label>
        <input v-model="email" class="input" placeholder="admin@example.com" @keyup.enter="submit" />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="password" type="password" class="input" placeholder="••••••••" @keyup.enter="submit" />
      </div>
      <button class="btn btn-primary login-btn" :disabled="loading" @click="submit">
        {{ loading ? '登录中…' : '登录' }}
      </button>
      <div class="login-hint">演示账号 zhangsan@example.com / demo1234</div>
    </div>
  </div>
</template>
