<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type EvalResult, type FlagRow } from '../api'
import { useConsoleStore } from '../stores/console'

const store = useConsoleStore()
const flags = ref<FlagRow[]>([])
const flagKey = ref('')
const userId = ref('2088')
const privateIp = ref('')
const attributes = ref('{\n  "region": "hangzhou"\n}')
const result = ref<EvalResult | null>(null)
const error = ref('')

const STEP_LABEL: Record<string, string> = {
  INSTANCE_OVERRIDE: '服务器定向',
  WHITELIST: '白名单',
  BLACKLIST: '黑名单',
  ATTRIBUTE: '属性定向',
  ROLLOUT: '百分比灰度',
  DEFAULT: '默认值',
  FLAG_DISABLED: '开关已关闭',
  FLAG_NOT_FOUND: '开关不存在'
}

async function run() {
  error.value = ''
  let attrs: Record<string, string> = {}
  if (attributes.value.trim()) {
    try {
      attrs = JSON.parse(attributes.value)
    } catch {
      error.value = '用户属性不是合法 JSON'
      return
    }
  }
  try {
    result.value = await api.evaluate({
      appKey: store.currentApp,
      envKey: store.currentEnv,
      flagKey: flagKey.value,
      userId: userId.value || null,
      instance: privateIp.value ? { privateIp: privateIp.value } : null,
      attributes: attrs
    })
  } catch (e) {
    error.value = (e as Error).message
  }
}

const steps = () => ['INSTANCE_OVERRIDE', 'WHITELIST', 'BLACKLIST', 'ATTRIBUTE', 'ROLLOUT', 'DEFAULT']

onMounted(async () => {
  if (!store.currentApp || !store.currentEnv) return
  flags.value = await api.flags(store.currentApp, store.currentEnv)
  flagKey.value = flags.value[0]?.flagKey || ''
  await run()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h2>分流调试</h2><p>模拟一次服务端分流请求，看命中哪条规则、桶号多少</p></div>
    </div>

    <div class="debug-grid">
      <div class="card" style="padding:20px">
        <div class="field">
          <label>开关 Key</label>
          <select v-model="flagKey" class="input">
            <option v-for="f in flags" :key="f.flagKey" :value="f.flagKey">{{ f.flagKey }}（{{ f.valueType }}）</option>
          </select>
        </div>
        <div class="field"><label>用户 ID</label><input v-model="userId" class="input" @keyup.enter="run" /></div>
        <div class="field">
          <label>请求实例（内网 IP，可留空）</label>
          <input v-model="privateIp" class="input" placeholder="10.0.0.11" />
        </div>
        <div class="field"><label>用户属性 (JSON)</label><textarea v-model="attributes" class="input" rows="5"></textarea></div>
        <button class="btn btn-primary" style="width:100%;justify-content:center" @click="run">执行分流</button>
        <div v-if="error" class="hint" style="color:var(--red)">{{ error }}</div>
      </div>

      <div class="card">
        <div class="result-hero">
          <div class="result-val" :class="result?.value === 'true' ? 'on' : 'off'">
            {{ result ? (result.value ?? '—') : '—' }}
          </div>
          <div>
            <div style="font-weight:600">{{ result?.value === 'true' ? '走新逻辑' : '走老逻辑 / 无值' }}</div>
            <div class="sub">
              命中规则：<b style="color:var(--text-2)">{{ STEP_LABEL[result?.hitRule || ''] || '—' }}</b>
              <span v-if="result?.bucket != null"> · 桶号 {{ result.bucket }}</span>
              <span v-if="result"> · configVersion {{ result.configVersion }}</span>
            </div>
          </div>
        </div>
        <div class="steps">
          <div
            v-for="(s, i) in steps()"
            :key="s"
            class="step"
            :class="result ? (result.hitRule === s ? 'hit' : 'skip') : 'skip'"
          >
            <span class="n">{{ i + 1 }}</span>
            <span>
              {{ STEP_LABEL[s] }}
              <template v-if="result && result.hitRule === s">：{{ result.reason }}</template>
              <template v-else>· 未命中</template>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
