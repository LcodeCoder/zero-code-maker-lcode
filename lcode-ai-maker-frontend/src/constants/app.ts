/**
 * 应用相关常量
 */
import { API_BASE_URL } from '@/config/env.ts'

/**
 * 代码生成类型枚举(对应后端 CodeGenTypeEnum)
 */
export enum CodeGenType {
  HTML = 'html',
  MULTI_FILE = 'multi_file',
}

/**
 * 代码生成类型可选项
 */
export const CODE_GEN_TYPE_OPTIONS = [
  { label: '原生 HTML 模式', value: CodeGenType.HTML },
  { label: '原生多文件模式', value: CodeGenType.MULTI_FILE },
]

/**
 * 代码生成类型的简短标签（用于卡片角标等紧凑展示位）
 * 新增类型时只需在此追加一项即可，无需改动使用方。
 * 未命中时回退为：将原始值（如 'vue_spa'）转成 'Vue Spa'。
 */
export const CODE_GEN_TYPE_SHORT_LABELS: Record<string, string> = {
  [CodeGenType.HTML]: 'HTML',
  [CodeGenType.MULTI_FILE]: '多文件',
}

export const getCodeGenTypeShortLabel = (value?: string) => {
  if (!value) return ''
  if (CODE_GEN_TYPE_SHORT_LABELS[value]) return CODE_GEN_TYPE_SHORT_LABELS[value]
  return value
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim()
}

/**
 * 精选应用优先级
 */
export const FEATURED_PRIORITY = 99

/**
 * 用户端分页每页最大条数
 */
export const USER_PAGE_MAX_SIZE = 20

/**
 * 获取应用生成网站的预览地址
 * 规则:{apiBase}/static/{codeGenType}_{appId}/
 * 走相对路径，由 Vite proxy 转发到后端，免 CORS
 */
export const getAppPreviewUrl = (codeGenType: string, appId: number | string) => {
  return `${API_BASE_URL}/static/${codeGenType}_${appId}/`
}

/**
 * 获取已部署应用的访问地址
 * 规则:{apiBase}/static/{deployKey}/
 */
export const getDeployUrl = (deployKey: string) => {
  return `${API_BASE_URL}/static/${deployKey}/`
}
