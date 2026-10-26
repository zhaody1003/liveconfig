<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type FlagRow, type InstanceRow } from '../api'
import { useConsoleStore } from '../stores/console'
import ValueEditor from '../components/ValueEditor.vue'

const store = useConsoleStore()
const rows = ref<FlagRow[]>([])
const keyword = ref('')
const loading = ref(false)
const expanded = ref<string | null>(null)
const instances = ref<InstanceRow[]>([])

const edit = ref<{ open: boolean; flag: FlagRow | null; value: string }>({
  open: false, flag: null, value: ''
})
const creating = ref(false)
const form = ref({ flagKey: '', name: '', valueType: 'BOOLEAN', defaultValue: 'false' })
const instEdit = ref<{ open: boolean; ip: string; publicIp?: string; value: string; flag: FlagRow | null }>({
  open: false, ip: '', value: '', flag: null
})

async function load() {
  if (!store.currentApp || !store.currentEnv) return
  loading.value = true
  try {
    rows.value = await api.flags(store.currentApp, store.currentEnv, keyword.value || undefined)
  } finally {
    loading.value = false
  }
  expanded.value = null
}

async function toggleExpand(flag: FlagRow) {
  if (expanded.value === flag.flagKey) {
    expanded.value = null
    return
  }
  expanded.value = flag.flagKey
  instances.value = await api.instances(flag.id)
}

function openEdit(flag: FlagRow) {
  edit.value = { open: true, flag, value: flag.defaultValue }
}

async function saveDefault() {
  if (!edit.value.flag) return
  try {
    await api.updateDefault(edit.value.flag.id, edit.value.value)
    ElMessage.success('已保存并推送到该环境全部实例')
    edit.value.open = false
    await load()
    await store.refreshEnvs()
  } catch {
    /* 拦截器已提示 */
  }
}

async function create() {
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
  ElMessage.success('已创建并推送')
  await load()
  await store.refreshEnvs()
}

async function toggleEnabled(flag: FlagRow, enabled: boolean) {
  await api.toggleFlag(flag.id, enabled)
  flag.enabled = enabled
  flag.version += 1
  ElMessage.success(enabled ? '已开启并推送' : '已关闭并推送')
}

function openInst(flag: FlagRow, ip: InstanceRow) {
  instEdit.value = {
    open: true, ip: ip.privateIp, publicIp: ip.publicIp,
    value: ip.overrideValue ?? flag.defaultValue, flag
  }
}

async function saveInst() {
  const { flag, ip, value, publicIp } = instEdit.value
  if (!flag) return
  await api.setInstance(flag.id, { privateIp: ip, publicIp, value })
  ElMessage.success(`已对 ${ip} 单独设值`)
  instEdit.value.open = false
  instances.value = await api.instances(flag.id)
}

async function clearInst() {
  const { flag, ip } = instEdit.value
  if (!flag) return
  await api.clearInstance(flag.id, ip)
  ElMessage.success(`已清除 ${ip} 的设值，恢复跟随默认`)
  instEdit.value.open = false
  instances.value = await api.instances(flag.id)
}

onMounted(load)
defineExpose({ load })
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>开关管理</h2>
        <p>{{ store.currentApp }} · {{ store.currentEnv }} 环境下的全部功能开关</p>
      </div>
      <div style="display:flex;gap:10px;align-items:center">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
          <input v-model="keyword" class="input" placeholder="搜索 key 或名称" @input="load" />
        </div>
        <button class="btn btn-ghost" @click="load">刷新</button>
        <button class="btn btn-primary" @click="creating = true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
          新建开关
        </button>
      </div>
    </div>

    <div class="card">
      <table>
        <thead>
          <tr><th>开关</th><th>类型</th><th>状态</th><th>灰度比例</th><th>版本</th><th>启用</th><th style="text-align:right">操作</th></tr>
        </thead>
        <tbody>
          <tr v-if="!rows.length"><td colspan="7" class="table-empty">{{ loading ? '加载中…' : '该环境下暂无开关' }}</td></tr>
          <template v-for="f in rows" :key="f.flagKey">
            <tr>
              <td>
                <div class="mono">{{ f.flagKey }}</div>
                <div class="sub">
                  {{ f.name }}
                  <span v-if="f.overrideCount" style="color:var(--violet);font-weight:600"> · {{ f.overrideCount }} 台按IP设值</span>
                </div>
                <div class="sub" style="margin-top:3px">默认值 <span class="mono" style="color:var(--text-2)">{{ f.defaultValue }}</span></div>
              </td>
              <td><span class="pill" :class="'pill-' + f.valueType.toLowerCase()">{{ f.valueType }}</span></td>
              <td>
                <span class="pill" :class="!f.enabled ? 'pill-off' : f.rolloutPercent === 100 ? 'pill-on' : 'pill-gray'">
                  {{ !f.enabled ? '关闭' : f.rolloutPercent === 100 ? '全量开' : f.rolloutPercent != null ? '灰度中' : '固定值' }}
                </span>
              </td>
              <td>
                <span v-if="f.rolloutPercent == null" class="sub">—</span>
                <div v-else class="rollout">
                  <div class="bar"><div class="fill" :style="{ width: f.rolloutPercent + '%' }"></div></div>
                  <span class="val">{{ f.rolloutPercent }}%</span>
                </div>
              </td>
              <td class="sub">v{{ f.version }}</td>
              <td>
                <label class="switch">
                  <input type="checkbox" :checked="f.enabled" @change="toggleEnabled(f, ($event.target as HTMLInputElement).checked)" />
                  <span class="track"></span>
                </label>
              </td>
              <td style="text-align:right;white-space:nowrap">
                <button class="btn btn-ghost btn-icon" title="编辑默认值 · 推送到所有实例" @click="openEdit(f)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z" /></svg>
                </button>
                <button
                  class="btn btn-ghost btn-icon"
                  :class="{ 'has-ov': f.overrideCount > 0 }"
                  title="展开服务器列表"
                  @click="toggleExpand(f)"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9" /></svg>
                </button>
              </td>
            </tr>
            <tr v-if="expanded === f.flagKey">
              <td colspan="7" style="background:#f6f7fc;padding:14px 22px 18px 34px">
                <div class="child-panel">
                  <div class="child-tab">
                    服务器实例值
                    <span class="sub" style="font-weight:400">默认值 {{ f.defaultValue }} 已在上方开关列显示</span>
                  </div>
                  <table class="subtable">
                    <thead><tr><th>服务器</th><th style="width:240px">当前值</th><th style="width:90px;text-align:right">操作</th></tr></thead>
                    <tbody>
                      <tr v-if="!instances.length"><td colspan="3" class="sub" style="text-align:center;padding:18px">该环境还没有 SDK 上报过实例</td></tr>
                      <tr v-for="i in instances" :key="i.privateIp">
                        <td>
                          <span class="mono">{{ i.privateIp }}</span> <span class="sub">内网</span>
                          <template v-if="i.publicIp"> · <span class="mono sub">{{ i.publicIp }}</span> <span class="sub">外网</span></template>
                        </td>
                        <td>
                          <span v-if="i.overrideValue != null" class="pill pill-on">{{ i.overrideValue }}</span>
                          <span v-else class="sub">跟随默认 · <span class="mono">{{ f.defaultValue }}</span></span>
                        </td>
                        <td style="text-align:right">
                          <button class="btn btn-ghost btn-sm" @click="openInst(f, i)">编辑</button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <el-dialog v-model="creating" title="新建开关" width="480px">
      <div class="field"><label>开关 Key *</label><input v-model="form.flagKey" class="input" placeholder="new_checkout（创建后不可改）" /></div>
      <div class="field"><label>名称</label><input v-model="form.name" class="input" placeholder="新结算页" /></div>
      <div class="field">
        <label>值类型 *</label>
        <select v-model="form.valueType" class="input">
          <option>BOOLEAN</option><option>STRING</option><option>NUMBER</option><option>JSON</option>
        </select>
      </div>
      <div class="field"><label>默认值 *</label><input v-model="form.defaultValue" class="input" /></div>
      <div class="hint">类型会被强校验：JSON 只接受对象/数组，NUMBER 只接受数字。创建即推送到该环境全部实例。</div>
      <template #footer>
        <button class="btn btn-ghost" @click="creating = false">取消</button>
        <button class="btn btn-primary" @click="create">创建</button>
      </template>
    </el-dialog>

    <el-dialog v-model="edit.open" :title="`编辑默认值 · ${edit.flag?.flagKey || ''}`" width="460px">
      <div class="hint" style="margin:0 0 14px">
        修改默认值并保存，将<b>推送到该应用该环境的所有实例</b>（未命中任何灰度规则或服务器定向的请求都用这个值）。
      </div>
      <div class="field">
        <label>默认值</label>
        <ValueEditor v-if="edit.flag" :value-type="edit.flag.valueType" v-model="edit.value" />
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="edit.open = false">取消</button>
        <button class="btn btn-primary" @click="saveDefault">保存并全量推送</button>
      </template>
    </el-dialog>

    <el-dialog v-model="instEdit.open" title="编辑服务器实例值" width="460px">
      <div class="sub" style="margin-bottom:14px">
        开关 <span class="mono" style="color:var(--accent)">{{ instEdit.flag?.flagKey }}</span> · 服务器 <span class="mono">{{ instEdit.ip }}</span>
      </div>
      <div class="field">
        <label>该服务器的值</label>
        <ValueEditor v-if="instEdit.flag" :value-type="instEdit.flag.valueType" v-model="instEdit.value" />
      </div>
      <div class="hint">此值仅作用于该台服务器，优先级高于所有灰度规则与默认值。</div>
      <template #footer>
        <button class="btn btn-danger-ghost" style="margin-right:auto" @click="clearInst">清除（跟随默认）</button>
        <button class="btn btn-ghost" @click="instEdit.open = false">取消</button>
        <button class="btn btn-primary" @click="saveInst">保存并推送</button>
      </template>
    </el-dialog>
  </div>
</template>
