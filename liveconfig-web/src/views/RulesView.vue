<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type FlagRow, type RuleItem } from '../api'
import { useConsoleStore } from '../stores/console'
import ValueEditor from '../components/ValueEditor.vue'

const store = useConsoleStore()
const flags = ref<FlagRow[]>([])
const flagId = ref<number | null>(null)
const detail = ref<Awaited<ReturnType<typeof api.flagDetail>> | null>(null)
const rules = ref<RuleItem[]>([])
const saving = ref(false)

const KIND_LABEL: Record<string, string> = {
  WHITELIST: '白名单 · 强制命中',
  BLACKLIST: '黑名单 · 强制排除',
  ATTRIBUTE: '属性定向 · 按用户属性命中',
  ROLLOUT: '百分比灰度 · 一致性哈希放量'
}

async function loadFlags() {
  if (!store.currentApp || !store.currentEnv) return
  flags.value = await api.flags(store.currentApp, store.currentEnv)
  if (!flagId.value || !flags.value.some((f) => f.id === flagId.value)) {
    flagId.value = flags.value[0]?.id || null
  }
}

async function loadRules() {
  if (!flagId.value) {
    detail.value = null
    rules.value = []
    return
  }
  detail.value = await api.flagDetail(flagId.value)
  rules.value = detail.value.rules.map((r) => ({ ...r }))
}

function addRule(kind: RuleItem['kind']) {
  const defaults: Record<string, string> = {
    WHITELIST: '{"userIds":[""]}',
    BLACKLIST: '{"userIds":[""]}',
    ATTRIBUTE: '{"conditions":[{"attr":"region","op":"EQ","value":"hangzhou"}]}',
    ROLLOUT: '{"percent":30,"bucketBy":"USER_ID"}'
  }
  rules.value.push({ kind, priority: 0, config: defaults[kind], value: detail.value?.enabled ? 'true' : 'false' })
}

function percentOf(rule: RuleItem): number {
  try {
    return JSON.parse(rule.config).percent ?? 0
  } catch {
    return 0
  }
}

function setPercent(rule: RuleItem, percent: number) {
  try {
    const config = JSON.parse(rule.config)
    config.percent = percent
    rule.config = JSON.stringify(config)
  } catch {
    rule.config = JSON.stringify({ percent, bucketBy: 'USER_ID' })
  }
}

function userIdsOf(rule: RuleItem): string[] {
  try {
    return JSON.parse(rule.config).userIds ?? []
  } catch {
    return []
  }
}

function setUserIds(rule: RuleItem, text: string) {
  try {
    const config = JSON.parse(rule.config)
    config.userIds = text.split(',').map((s) => s.trim()).filter(Boolean)
    rule.config = JSON.stringify(config)
  } catch {
    rule.config = JSON.stringify({ userIds: [] })
  }
}

async function save() {
  if (!flagId.value) return
  saving.value = true
  try {
    await api.saveRules(flagId.value, rules.value)
    ElMessage.success('规则已保存并通过 SSE 推送到该环境全部实例')
    await loadRules()
    await store.refreshEnvs()
  } finally {
    saving.value = false
  }
}

watch(() => [store.currentApp, store.currentEnv], loadFlags)
loadFlags().then(loadRules)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>灰度规则</h2>
        <p>规则自上而下按优先级匹配，命中即返回。服务器定向在「开关管理」按 IP 设值，优先级最高</p>
      </div>
      <div style="display:flex;gap:10px;align-items:center">
        <select v-model="flagId" class="input" @change="loadRules">
          <option v-for="f in flags" :key="f.id" :value="f.id">{{ f.flagKey }}（{{ f.valueType }}）</option>
        </select>
        <button class="btn btn-primary" :disabled="!flagId || saving" @click="save">保存并推送</button>
      </div>
    </div>

    <div v-if="!flagId" class="card"><div class="table-empty">该环境下暂无开关</div></div>

    <template v-else>
      <div class="rule">
        <div class="rule-head">
          <span class="rule-idx">0</span>
          <span class="rule-name">服务器定向 · 按 IP 单独设值</span>
          <span class="rule-prio">优先级 最高 · 运维用</span>
        </div>
        <div class="rule-body">
          <div class="frow">
            <span class="flabel">当前设值</span>
            <span class="sub">{{ detail?.overrideCount ? `${detail.overrideCount} 台已单独设值` : '暂无' }}</span>
          </div>
          <div class="hint">在「开关管理」对应行点展开图标，为指定内网 IP 设值；命中后不再走下面任何规则。仅用于主机金丝雀 / 单节点排障。</div>
        </div>
      </div>

      <div v-for="(r, idx) in rules" :key="idx" class="rule">
        <div class="rule-head">
          <span class="rule-idx">{{ idx + 1 }}</span>
          <span class="rule-name">{{ KIND_LABEL[r.kind] || r.kind }}</span>
          <span class="rule-prio">kind {{ r.kind }}</span>
          <button class="btn btn-ghost btn-sm" style="margin-left:12px" @click="rules.splice(idx, 1)">删除</button>
        </div>
        <div class="rule-body">
          <template v-if="r.kind === 'WHITELIST' || r.kind === 'BLACKLIST'">
            <div class="frow">
              <span class="flabel">用户 ID</span>
              <input
                class="input"
                style="flex:1"
                :value="userIdsOf(r).join(', ')"
                placeholder="逗号分隔，如 1001, test_admin"
                @input="setUserIds(r, ($event.target as HTMLInputElement).value)"
              />
            </div>
          </template>

          <template v-else-if="r.kind === 'ROLLOUT'">
            <div class="frow">
              <span class="flabel">放量比例</span>
              <div style="flex:1;max-width:420px">
                <input type="range" min="0" max="100" :value="percentOf(r)" @input="setPercent(r, Number(($event.target as HTMLInputElement).value))" />
              </div>
              <span class="pct">{{ percentOf(r) }}%</span>
            </div>
            <div class="hint">按 <code>md5(flagKey + ":" + userId) % 100</code> 稳定分桶，落在 <code>[0, 比例)</code> 命中；放量时已命中用户不会跳走。</div>
          </template>

          <template v-else>
            <div class="frow">
              <span class="flabel">条件 JSON</span>
              <textarea class="input" rows="3" style="flex:1" v-model="r.config"></textarea>
            </div>
            <div class="hint">conditions 内多个条件为 AND 关系；op 支持 EQ / NE / IN / GT / LT。</div>
          </template>

          <div class="frow">
            <span class="flabel">命中返回</span>
            <div style="flex:1;max-width:280px">
              <ValueEditor :value-type="detail?.valueType || 'BOOLEAN'" v-model="r.value" />
            </div>
          </div>
        </div>
      </div>

      <div class="rule">
        <div class="rule-head">
          <span class="rule-idx">{{ rules.length + 1 }}</span>
          <span class="rule-name">默认值 · 以上均未命中</span>
          <span class="rule-prio">优先级 最低</span>
        </div>
        <div class="rule-body">
          <div class="frow"><span class="flabel">返回值</span><span class="pill pill-gray">{{ detail?.defaultValue }}</span></div>
        </div>
      </div>

      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="btn btn-ghost" @click="addRule('WHITELIST')">+ 白名单</button>
        <button class="btn btn-ghost" @click="addRule('BLACKLIST')">+ 黑名单</button>
        <button class="btn btn-ghost" @click="addRule('ATTRIBUTE')">+ 属性定向</button>
        <button class="btn btn-ghost" @click="addRule('ROLLOUT')">+ 百分比灰度</button>
      </div>
    </template>
  </div>
</template>
