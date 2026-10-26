import { get, post, put, del } from './request'

export interface AppItem {
  appKey: string
  name: string
  owner?: string
  color?: string
  flagCount: number
  envCount: number
}

export interface EnvItem {
  id: number
  envKey: string
  color: string
  highRisk: boolean
  configVersion: number
  flagCount: number
  onlineSdk: number
  serverKeyMasked?: string
  clientKeyMasked?: string
}

export interface CreatedEnv {
  env: EnvItem
  serverKeyPlain: string
  clientKeyPlain: string
}

export interface FlagRow {
  id: number
  envId: number
  flagKey: string
  name?: string
  valueType: 'BOOLEAN' | 'STRING' | 'NUMBER' | 'JSON'
  defaultValue: string
  enabled: boolean
  version: number
  rolloutPercent: number | null
  overrideCount: number
}

export interface RuleItem {
  id?: number
  kind: 'WHITELIST' | 'BLACKLIST' | 'ATTRIBUTE' | 'ROLLOUT'
  priority: number
  config: string
  value: string
}

export interface FlagDetail extends FlagRow {
  appKey: string
  envKey: string
  rules: RuleItem[]
  updatedAt: string
}

export interface InstanceRow {
  privateIp: string
  publicIp?: string
  overrideValue: string | null
  valueType: string
}

export interface MemberRow {
  userId: number
  email: string
  name: string
  platformRole: string
  status: string
  rolesByEnv: Record<number, number>
}

export interface RoleItem {
  id: number
  appId: number
  roleKey: string
  name: string
  builtin: boolean
  permCsv: string
  perms: string[]
  usedBy: number
}

export interface PermGroup {
  group: string
  items: { code: string; label: string }[]
}

export interface ChangeLog {
  id: number
  flagKey: string
  actor: string
  action: string
  configVersion: number
  createdAt: string
  beforeJson?: string
  afterJson?: string
}

export interface Page<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface EvalResult {
  key: string
  type: string | null
  value: string | null
  hitRule: string
  reason: string
  bucket: number | null
  flagVersion: number | null
  configVersion: number
  source: string
}

export interface LoginResult {
  token: string
  email: string
  name: string
  platformRole: string
}

export const api = {
  login: (email: string, password: string) => post<LoginResult>('/api/auth/login', { email, password }),
  logout: () => post<void>('/api/auth/logout'),

  apps: (q?: string) => get<AppItem[]>('/api/apps', { q }),
  createApp: (payload: Partial<AppItem>) => post<AppItem>('/api/apps', payload),

  envs: (appKey: string) => get<EnvItem[]>(`/api/apps/${appKey}/envs`),
  createEnv: (appKey: string, payload: { envKey: string; color?: string; highRisk?: boolean }) =>
    post<CreatedEnv>(`/api/apps/${appKey}/envs`, payload),
  deleteEnv: (appKey: string, envKey: string) => del<void>(`/api/apps/${appKey}/envs/${envKey}`),
  resetKeys: (appKey: string, envKey: string) =>
    post<CreatedEnv>(`/api/apps/${appKey}/envs/${envKey}/reset-keys`),

  flags: (appKey: string, envKey: string, q?: string) =>
    get<FlagRow[]>('/api/flags', { appKey, envKey, q }),
  flagDetail: (flagId: number) => get<FlagDetail>(`/api/flags/${flagId}`),
  createFlag: (payload: Record<string, unknown>) => post<FlagDetail>('/api/flags', payload),
  updateDefault: (flagId: number, value: string) =>
    put<FlagDetail>(`/api/flags/${flagId}/default`, { value }),
  toggleFlag: (flagId: number, enabled: boolean) =>
    put<FlagDetail>(`/api/flags/${flagId}/enabled?enabled=${enabled}`),
  saveRules: (flagId: number, rules: RuleItem[]) =>
    put<FlagDetail>(`/api/flags/${flagId}/rules`, { rules }),

  instances: (flagId: number) => get<InstanceRow[]>(`/api/flags/${flagId}/instances`),
  setInstance: (flagId: number, payload: { privateIp: string; publicIp?: string; value: string }) =>
    put<void>(`/api/flags/${flagId}/instances`, payload),
  clearInstance: (flagId: number, privateIp: string) =>
    del<void>(`/api/flags/${flagId}/instances/${privateIp}`),

  members: (appKey: string) => get<MemberRow[]>('/api/members', { appKey }),
  invite: (payload: Record<string, unknown>) => post<void>('/api/members', payload),
  assignRole: (payload: Record<string, unknown>) => put<void>('/api/members/role', payload),
  removeMember: (appKey: string, email: string) => del<void>('/api/members', { appKey, email }),

  roles: (appKey: string) => get<RoleItem[]>('/api/roles', { appKey }),
  createRole: (payload: Record<string, unknown>) => post<RoleItem>('/api/roles', payload),
  perms: () => get<PermGroup[]>('/api/perms'),

  history: (appKey: string, envKey: string, flagKey?: string, pageNum = 1, pageSize = 20) =>
    get<Page<ChangeLog>>('/api/history', { appKey, envKey, flagKey, pageNum, pageSize }),
  rollback: (logId: number) => post<ChangeLog>(`/api/history/${logId}/rollback`),

  evaluate: (payload: Record<string, unknown>) => post<EvalResult>('/api/flags/debug', payload)
}
