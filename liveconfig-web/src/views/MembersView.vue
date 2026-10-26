<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type MemberRow, type RoleItem } from '../api'
import { useConsoleStore } from '../stores/console'

const store = useConsoleStore()
const members = ref<MemberRow[]>([])
const roles = ref<RoleItem[]>([])
const inviting = ref(false)
const form = ref({ email: '', name: '', roleKey: 'dev', envKeys: [] as string[] })

function roleKeyOf(m: MemberRow, envId: number) {
  const raw = (m.rolesByEnv as Record<string, number | string>)[String(envId)]
  const roleId = Number(raw)
  if (!roleId) return ''
  return roles.value.find((r) => r.id === roleId)?.roleKey || ''
}

async function load() {
  if (!store.currentApp) return
  members.value = await api.members(store.currentApp)
  roles.value = await api.roles(store.currentApp)
  form.value.roleKey = roles.value.find((r) => r.roleKey === 'dev')?.roleKey || roles.value[0]?.roleKey || 'dev'
}

async function assign(email: string, envId: number, roleKey: string) {
  const env = store.appEnvs.find((e) => e.id === envId)
  if (!env || !store.currentApp) return
  await api.assignRole({ appKey: store.currentApp, email, envKey: env.envKey, roleKey })
  ElMessage.success('角色已更新')
  await load()
}

async function invite() {
  if (!store.currentApp) return
  await api.invite({
    appKey: store.currentApp,
    email: form.value.email,
    name: form.value.name,
    roleKey: form.value.roleKey,
    envKeys: form.value.envKeys.length ? form.value.envKeys : store.appEnvs.map((e) => e.envKey)
  })
  inviting.value = false
  form.value = { email: '', name: '', roleKey: 'dev', envKeys: [] }
  ElMessage.success('已加入成员')
  await load()
}

async function remove(email: string) {
  if (!store.currentApp) return
  await api.removeMember(store.currentApp, email)
  ElMessage.success('已移除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h2>成员与权限</h2><p>{{ store.currentApp }} · 按环境为成员分配应用角色</p></div>
      <button class="btn btn-primary" @click="inviting = true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
        邀请成员
      </button>
    </div>

    <div class="card" style="overflow-x:auto">
      <table>
        <thead>
          <tr>
            <th>成员</th>
            <th v-for="e in store.appEnvs" :key="e.envKey" style="min-width:150px">
              <span class="env-dot" :style="{ background: e.color, display: 'inline-block', width: 7, height: 7, borderRadius: '50%', marginRight: 6 }"></span>
              {{ e.envKey }}<span v-if="e.highRisk" class="sub"> · 高危</span>
            </th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!members.length"><td :colspan="store.appEnvs.length + 2" class="table-empty">暂无成员</td></tr>
          <tr v-for="m in members" :key="m.userId">
            <td>
              <div class="mname">{{ m.name }}</div>
              <div class="sub">{{ m.email }} · {{ m.platformRole === 'ADMIN' ? '平台管理员' : '普通用户' }}</div>
            </td>
            <td v-for="e in store.appEnvs" :key="e.envKey">
              <select
                class="input"
                style="width:100%"
                :value="roleKeyOf(m, e.id)"
                @change="assign(m.email, e.id, ($event.target as HTMLSelectElement).value)"
              >
                <option value="" disabled>未分配</option>
                <option v-for="r in roles" :key="r.id" :value="r.roleKey">{{ r.name }}</option>
              </select>
            </td>
            <td style="text-align:right"><button class="btn btn-ghost btn-sm" @click="remove(m.email)">移除</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="hint" style="margin-top:10px">
      同一成员在不同环境可持不同角色（如开发环境可写、生产只读）。改高危环境的值需要「改高危环境值」权限。
    </div>

    <el-dialog v-model="inviting" title="邀请成员" width="480px">
      <div class="field">
        <label>平台账号邮箱 *</label>
        <input v-model="form.email" class="input" placeholder="user@example.com" />
        <div class="hint">已存在则直接加入；不存在则用下面的姓名新建平台账号（初始口令 demo1234，仅演示）。</div>
      </div>
      <div class="field"><label>姓名</label><input v-model="form.name" class="input" placeholder="选填" /></div>
      <div class="field">
        <label>应用角色 *</label>
        <select v-model="form.roleKey" class="input">
          <option v-for="r in roles" :key="r.id" :value="r.roleKey">{{ r.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>应用到哪些环境</label>
        <div style="display:flex;gap:14px;flex-wrap:wrap">
          <label v-for="e in store.appEnvs" :key="e.envKey" class="env-check">
            <input v-model="form.envKeys" type="checkbox" :value="e.envKey" /> {{ e.envKey }}
          </label>
        </div>
        <div class="hint">不勾选表示全部环境。</div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="inviting = false">取消</button>
        <button class="btn btn-primary" @click="invite">确认</button>
      </template>
    </el-dialog>
  </div>
</template>
