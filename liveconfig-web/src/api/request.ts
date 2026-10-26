import axios from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应体。Result 会同时序列化 isSuccess 与 success，前端只认 isSuccess */
export interface ApiResult<T> {
  isSuccess: boolean
  code: number
  message: string
  data: T
}

const http = axios.create({ baseURL: '/', timeout: 10000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('liveconfig_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'
    const isSdkCall = String(error.config?.url || '').startsWith('/sdk/')
    if (status === 401 && !isSdkCall) {
      localStorage.removeItem('liveconfig_token')
      if (!location.hash.startsWith('#/login')) {
        ElMessage.warning('登录已过期，请重新登录')
        location.hash = '#/login'
      }
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  }
)

async function unwrap<T>(promise: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const body = (await promise).data
  return body.data
}

export const get = <T>(url: string, params?: Record<string, unknown>) => unwrap(http.get<ApiResult<T>>(url, { params }))
export const post = <T>(url: string, payload?: unknown) => unwrap(http.post<ApiResult<T>>(url, payload))
export const put = <T>(url: string, payload?: unknown) => unwrap(http.put<ApiResult<T>>(url, payload))
export const del = <T>(url: string, params?: Record<string, unknown>) => unwrap(http.delete<ApiResult<T>>(url, { params }))

export default http
