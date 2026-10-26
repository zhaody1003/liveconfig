<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type PermGroup, type RoleItem } from '../api'
import { useConsoleStore } from '../stores/console'

const store = useConsoleStore()
const roles = ref<RoleItem[]>([])
const catalog = ref<PermGroup[]>([])
const creating = ref(false)
const form = ref({ roleKey: '', name: '', perms: [] as string[] })

async function load() {
  if (!store.currentApp) return
  roles.value = await api.roles(store.currentApp)
  catalog.value = await api.perms()
}

async function create() {
  if (!store.currentApp) return
  if (!form.value.perms.length) {
    ElMessage.warning('至少选择一个权限点')
    return
  }
  await api.createRole({
    appKey: store.currentApp,
    roleKey: form.value.roleKey,
    name: form.value.name,
    perms: form.value.perms
  })
  creating.value = false
  form.value = { roleKey: '', name: '', perms: [] }
  ElMessage.success('自定义角色已创建')
  await load()
}

function labelOf(code: string) {
  for (const g of catalog.value) {
    const hit = g.items.find((i) => i.code === code)
    if (hit) return hit.label
  }
  return code
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>应用角色</h2>
        <p>{{ store.currentApp }} · 内置 + 自定义，角色就是一组权限点；作用范围到「应用 + 环境」</p>
      </div>
      <button class="btn btn-primary" @click="creating = true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
        新建自定义角色
      </button>
    </div>

    <div class="role-grid">
      <div v-for="r in roles" :key="r.id" class="role-card">
        <div class="role-card-top">
          <b>{{ r.name }}</b>
          <span class="role-tag" :class="r.builtin ? 'builtin' : 'custom'">{{ r.builtin ? '内置' : '自定义' }}</span>
          <span class="sub" style="margin-left:auto">{{ r.roleKey }}</span>
        </div>
        <div class="role-meta">{{ r.perms.length }} 个权限点 · {{ r.usedBy }} 位成员使用</div>
        <div class="perm-chips">
          <span v-for="p in r.perms" :key="p" class="perm-chip">{{ labelOf(p) }}</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="creating" title="新建自定义角色" width="520px">
      <div class="field"><label>角色标识 *</label><input v-model="form.roleKey" class="input" placeholder="oncall" /></div>
      <div class="field"><label>角色名称 *</label><input v-model="form.name" class="input" placeholder="灰度值班" /></div>
      <div class="field">
        <label>权限点</label>
        <div v-for="g in catalog" :key="g.group" class="perm-group">
          <div class="g-t">{{ g.group }}</div>
          <label v-for="i in g.items" :key="i.code" class="perm-opt">
            <input v-model="form.perms" type="checkbox" :value="i.code" /> {{ i.label }}
            <span class="sub mono" style="margin-left:6px">{{ i.code }}</span>
          </label>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="creating = false">取消</button>
        <button class="btn btn-primary" @click="create">创建角色</button>
      </template>
    </el-dialog>
  </div>
</template>
