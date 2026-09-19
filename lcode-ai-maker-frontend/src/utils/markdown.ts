import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

/**
 * markdown-it 实例,支持代码高亮
 */
const md = new MarkdownIt({
  html: false, // 不信任 AI 输出的原始 HTML,避免 XSS
  linkify: true,
  breaks: true,
  highlight(code: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const html = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        return `<pre class="hljs"><code>${html}</code></pre>`
      } catch {
        // 高亮失败时退回默认转义
      }
    }
    const escaped = md.utils.escapeHtml(code)
    return `<pre class="hljs"><code>${escaped}</code></pre>`
  },
})

/**
 * 将 markdown 文本渲染为 HTML
 */
export const renderMarkdown = (text: string): string => {
  if (!text) {
    return ''
  }
  return md.render(text)
}
