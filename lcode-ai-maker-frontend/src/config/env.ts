/**
 * 全局环境配置（统一收口，避免后端地址硬编码散落各处）
 *
 * - 开发环境：业务请求走相对路径 /api，由 Vite proxy 转发到后端，规避 CORS
 * - SSE（EventSource）无法走 Vite proxy 的 ws 之外场景时，仍需可拼出绝对地址，
 *   因此保留 BACKEND_HOST 兜底（取自 .env 的 VITE_BACKEND_TARGET）
 */

/** 请求基础路径（业务代码统一使用，默认相对路径走 proxy） */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/**
 * 后端绝对地址（仅 EventSource / 新窗口打开静态资源等无法走相对路径的场景使用）
 * 开发环境下若未配置，回退到当前页面 origin（配合 proxy 时即同源）
 */
export const BACKEND_HOST =
  import.meta.env.VITE_BACKEND_TARGET || window.location.origin

/** 后端 API 绝对前缀，如 http://localhost:8080/api */
export const BACKEND_API_PREFIX = `${BACKEND_HOST}${API_BASE_URL}`
