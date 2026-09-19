<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  SendOutlined,
  UserOutlined,
  LoadingOutlined,
} from '@ant-design/icons-vue'
import { getAppById, deployApp } from '@/api/appController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { getAppPreviewUrl, getDeployUrl } from '@/constants/app.ts'
import { API_BASE_URL } from '@/config/env.ts'
import { renderMarkdown } from '@/utils/markdown.ts'
import 'highlight.js/styles/github.css'
import AppManageActions from '@/components/AppManageActions.vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = computed(() => route.params.id as string)

// 应用信息
const app = ref<API.AppVO>({})

// 是否为本人(仅本人可对话)
const isOwner = computed(
  () => !!app.value.userId && !!loginUserStore.loginUser.id &&
    String(app.value.userId) === String(loginUserStore.loginUser.id),
)

// 消息列表
interface ChatMessage {
  role: 'user' | 'ai'
  content: string
  loading?: boolean
}
const messages = reactive<ChatMessage[]>([])

// 输入框
const userInput = ref('')
// 是否正在生成(SSE 进行中)
const generating = ref(false)
// 预览地址(生成完成后展示)
const previewUrl = ref('')
const previewError = ref('')
// 部署相关
const deploying = ref(false)
const deployUrl = ref('')
const deployModalOpen = ref(false)

const messageListRef = ref<HTMLElement>()
let eventSource: EventSource | null = null

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    const el = messageListRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

// 加载应用信息
const loadApp = async () => {
  try {
    const res = await getAppById({ id: appId.value as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      app.value = res.data.data
    } else {
      message.error(res.data.message || '应用不存在')
      router.push('/')
    }
  } catch {
    message.error('加载应用信息失败')
    router.push('/')
  }
}

// 发送消息并通过 SSE 接收 AI 回复
const sendMessage = (content: string) => {
  const text = content.trim()
  if (!text) {
    message.warning('请输入消息内容')
    return
  }
  if (generating.value) {
    return
  }
  if (!isOwner.value) {
    message.warning('只能与自己创建的应用对话')
    return
  }

  // 追加用户消息
  messages.push({ role: 'user', content: text })
  // 追加一条 AI 占位消息
  const aiMessage = reactive<ChatMessage>({ role: 'ai', content: '', loading: true })
  messages.push(aiMessage)
  scrollToBottom()

  generating.value = true
  previewUrl.value = ''
  previewError.value = ''

  // 构造 SSE 请求地址（走相对路径，由 Vite proxy 转发，同源免 CORS）
  const url = `${API_BASE_URL}/app/chat/gen/code?appId=${encodeURIComponent(
    appId.value,
  )}&message=${encodeURIComponent(text)}`

  // 关闭可能存在的旧连接
  closeEventSource()
  eventSource = new EventSource(url, { withCredentials: true })

  // 默认消息事件:拼接流式内容
  eventSource.onmessage = (e) => {
    if (!e.data) {
      return
    }
    try {
      const parsed = JSON.parse(e.data)
      // 后端将每段内容包装为 { "l": "..." }
      const chunk = parsed.l ?? ''
      aiMessage.content += chunk
      aiMessage.loading = false
      scrollToBottom()
    } catch {
      // 非 JSON 数据直接拼接
      aiMessage.content += e.data
      aiMessage.loading = false
    }
  }

  // 结束事件:展示预览
  eventSource.addEventListener('finish', () => {
    finishGenerate(aiMessage)
  })

  // 后端解析或保存失败时，保留聊天内容并显示失败原因。
  eventSource.addEventListener('generation_error', (event) => {
    let reason = '网页生成失败，请稍后重试'
    try {
      const data = JSON.parse((event as MessageEvent).data)
      if (typeof data.message === 'string' && data.message) {
        reason = data.message
      }
    } catch {
      // 无法解析错误详情时使用默认提示。
    }
    failGenerate(aiMessage, reason)
  })

  // 只有收到 finish 才代表文件保存成功，已有文本不代表生成完成。
  eventSource.onerror = () => {
    if (generating.value) {
      failGenerate(aiMessage, '生成连接中断或请求失败，请重新生成')
    }
  }
}

const failGenerate = (aiMessage: ChatMessage, reason: string) => {
  closeEventSource()
  aiMessage.loading = false
  generating.value = false
  previewUrl.value = ''
  previewError.value = reason
  if (!aiMessage.content) {
    aiMessage.content = reason
  }
  message.error(reason)
  scrollToBottom()
}

// 生成结束:收尾并展示预览
const finishGenerate = (aiMessage: ChatMessage) => {
  closeEventSource()
  aiMessage.loading = false
  generating.value = false
  // 网站文件已生成完成,展示预览
  if (app.value.codeGenType) {
    previewUrl.value = getAppPreviewUrl(app.value.codeGenType, appId.value)
  }
  scrollToBottom()
}

const closeEventSource = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

// 点击发送
const handleSend = () => {
  sendMessage(userInput.value)
  userInput.value = ''
}

// 部署应用
const handleDeploy = async () => {
  if (!isOwner.value) {
    message.warning('只能部署自己创建的应用')
    return
  }
  deploying.value = true
  try {
    const res = await deployApp({ appId: appId.value as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      // 后端返回完整 URL，提取末段 deployKey 后用统一规则重建相对地址，
      // 兼容后端新旧地址格式，始终走 proxy 同源访问
      const raw = String(res.data.data)
      const deployKey = raw.replace(/\/+$/, '').split('/').pop() || ''
      deployUrl.value = deployKey ? getDeployUrl(deployKey) : raw
      deployModalOpen.value = true
      message.success('部署成功')
    } else {
      message.error(res.data.message || '部署失败')
    }
  } catch {
    message.error('部署失败,请稍后重试')
  } finally {
    deploying.value = false
  }
}

const openDeployUrl = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

const openPreviewUrl = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

onMounted(async () => {
  await loadApp()
  // 加载历史:若应用已生成过,默认展示预览
  if (app.value.codeGenType) {
    previewUrl.value = getAppPreviewUrl(app.value.codeGenType, appId.value)
  }
  // 自动发送初始提示词(从主页创建后跳转过来)
  if (route.query.auto === '1' && app.value.initPrompt && isOwner.value) {
    // 自动对话前先清空预览,等待重新生成
    previewUrl.value = ''
    sendMessage(app.value.initPrompt)
    // 移除 auto 标记,避免刷新重复触发
    router.replace({ path: route.path })
  }
})

onBeforeUnmount(() => {
  closeEventSource()
})
</script>

<template>
  <div class="chat-page">
    <!-- 顶部栏 -->
    <header class="chat-header">
      <div class="header-left">
        <a-button type="text" class="back-btn" @click="router.push('/')">
          <ArrowLeftOutlined />
        </a-button>
        <span class="app-name">{{ app.appName || '未命名应用' }}</span>
      </div>
      <div class="header-right">
        <AppManageActions
          :app="app"
          :admin-mode="!isOwner"
          :disabled="generating || deploying"
          @deleted="router.replace('/')"
        />
        <a-button
          type="primary"
          :loading="deploying"
          :disabled="!isOwner || generating"
          class="deploy-btn"
          @click="handleDeploy"
        >
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署
        </a-button>
      </div>
    </header>

    <!-- 核心内容区 -->
    <div class="chat-body">
      <!-- 左侧对话区 -->
      <section class="chat-panel">
        <div ref="messageListRef" class="message-list">
          <a-empty
            v-if="!messages.length"
            description="开始和 AI 对话,生成你的网站应用吧"
            class="empty-tip"
          />
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="msg.role === 'user' ? 'message-user' : 'message-ai'"
          >
            <div class="message-avatar">
              <a-avatar
                v-if="msg.role === 'user'"
                :size="34"
                :src="loginUserStore.loginUser.userAvatar"
                class="user-avatar-img"
              >
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <img
                v-else
                src="@/assets/code maker.png"
                alt="AI"
                class="ai-avatar-img"
              />
            </div>
            <div class="message-content">
              <LoadingOutlined v-if="msg.loading && !msg.content" spin />
              <!-- AI 消息渲染 markdown，用户消息纯文本展示 -->
              <div
                v-else-if="msg.role === 'ai'"
                class="markdown-body"
                v-html="renderMarkdown(msg.content)"
              ></div>
              <pre v-else class="message-text">{{ msg.content }}</pre>
            </div>
          </div>
        </div>

        <!-- 输入框 -->
        <div class="input-area">
          <div class="composer">
            <a-textarea
              v-model:value="userInput"
              class="composer-input"
              placeholder="描述你的需求，例如：再加一个联系我们的表单板块…"
              :auto-size="{ minRows: 4, maxRows: 10 }"
              :disabled="generating || !isOwner"
              @keydown.enter.exact.prevent="handleSend"
            />
            <a-button
              type="primary"
              shape="circle"
              class="send-btn"
              :loading="generating"
              :disabled="!isOwner || !userInput.trim()"
              @click="handleSend"
            >
              <template #icon>
                <SendOutlined />
              </template>
            </a-button>
          </div>
          <div class="composer-hint">
            <span class="hint-kbd">Enter</span> 发送
            <span class="hint-sep">·</span>
            <span class="hint-kbd">Shift + Enter</span> 换行
          </div>
        </div>
      </section>

      <!-- 右侧预览区 -->
      <section class="preview-panel">
        <div class="preview-header">
          <span class="preview-title">生成后的网页展示</span>
          <a-button
            v-if="previewUrl"
            type="link"
            size="small"
            @click="openPreviewUrl"
          >
            新窗口打开
          </a-button>
        </div>
        <div class="preview-content">
          <div v-if="generating" class="preview-status">
            <LoadingOutlined spin class="status-icon" />
            <p>AI 正在生成网站文件,请稍候...</p>
          </div>
          <a-result
            v-else-if="previewError"
            status="error"
            title="网页生成失败"
            :sub-title="previewError"
          />
          <iframe
            v-else-if="previewUrl"
            :src="previewUrl"
            class="preview-iframe"
            frameborder="0"
          ></iframe>
          <a-empty v-else description="网站文件生成完成后将在此展示" class="preview-empty" />
        </div>
      </section>
    </div>

    <!-- 部署成功弹窗 -->
    <a-modal
      v-model:open="deployModalOpen"
      title="部署成功 🎉"
      ok-text="访问网站"
      cancel-text="关闭"
      @ok="openDeployUrl"
    >
      <p>你的网站已成功部署,可通过以下地址访问:</p>
      <a-typography-link :href="deployUrl" target="_blank" copyable>
        {{ deployUrl }}
      </a-typography-link>
    </a-modal>
  </div>
</template>

<style scoped>
.header-right { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 48px);
}

/* ===== 顶部栏 ===== */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  height: 56px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  margin-bottom: 16px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.back-btn {
  font-size: 16px;
}

.app-name {
  font-size: 17px;
  font-weight: 700;
  color: #1f2937;
}

.deploy-btn {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  border: none;
  border-radius: 18px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25);
}

/* ===== 核心内容区 ===== */
.chat-body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

/* ===== 左侧对话区 ===== */
.chat-panel {
  display: flex;
  flex-direction: column;
  width: 42%;
  min-width: 360px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.empty-tip {
  margin: auto;
}

.message-item {
  display: flex;
  gap: 10px;
  max-width: 92%;
}

.message-user {
  flex-direction: row-reverse;
  align-self: flex-end;
}

.message-ai {
  align-self: flex-start;
}

.message-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

/* AI：网站 logo，配浅色磨砂底 + 品牌描边 */
.message-ai .message-avatar {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  box-shadow: var(--sh-sm);
  padding: 4px;
}

.ai-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 50%;
}

/* 用户：自己的头像；无头像时回退为主色底 + 图标 */
.message-user .message-avatar {
  background: var(--grad-primary);
  color: var(--c-text-inverse);
  font-size: 16px;
}

.user-avatar-img {
  width: 34px;
  height: 34px;
}

/* 有头像时去掉底色，让图片直显 */
.message-user .message-avatar :deep(.ant-avatar-image),
.message-user .message-avatar :deep(.ant-avatar img) {
  background: transparent;
}

.message-content {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  color: #1f2937;
}

.message-user .message-content {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  color: #fff;
  border-top-right-radius: 2px;
}

.message-ai .message-content {
  background: #f3f4f6;
  border-top-left-radius: 2px;
}

.message-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

/* ===== Markdown 渲染样式 ===== */
.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 14px 0 8px;
  font-weight: 600;
  line-height: 1.3;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 4px 0 8px;
  padding-left: 22px;
}

.markdown-body :deep(li) {
  margin: 2px 0;
}

.markdown-body :deep(a) {
  color: #4f46e5;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 行内代码 */
.markdown-body :deep(code) {
  padding: 2px 6px;
  font-size: 13px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

/* 代码块 */
.markdown-body :deep(pre) {
  margin: 8px 0;
  padding: 12px 14px;
  background: #f6f8fa;
  border-radius: 8px;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
  font-size: 13px;
  line-height: 1.5;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  color: #6b7280;
  border-left: 3px solid #d1d5db;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 6px 10px;
  border: 1px solid #e5e7eb;
}

/* ===== 输入框（composer） ===== */
.input-area {
  border-top: 1px solid var(--c-border);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.composer {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.composer-input {
  flex: 1;
  border-radius: var(--r-lg) !important;
  resize: none;
  padding: 14px 60px 14px 16px !important;
  font-size: 15px;
  line-height: 1.6;
  transition: border-color var(--t-base) var(--ease),
    box-shadow var(--t-base) var(--ease);
}

.composer-input:hover {
  border-color: var(--c-border-strong) !important;
}

.composer-input:focus,
.composer-input:focus-within {
  border-color: var(--c-primary) !important;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.18) !important;
}

/* 禁用态：保持可读但降饱和 */
.composer-input:disabled {
  background: var(--c-surface-alt) !important;
  color: var(--c-text-quaternary) !important;
  cursor: not-allowed;
}

.send-btn {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 42px;
  height: 42px;
  min-width: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--grad-primary) !important;
  border: none;
  box-shadow: var(--sh-primary);
  transition: transform var(--t-base) var(--ease),
    box-shadow var(--t-base) var(--ease), opacity var(--t-base) var(--ease);
}

.send-btn:not(:disabled):hover {
  transform: scale(1.08) translateY(-1px);
  box-shadow: var(--sh-primary-lg) !important;
}

.send-btn:not(:disabled):active {
  transform: scale(1) translateY(0);
}

.send-btn:disabled {
  opacity: 0.45;
  box-shadow: none;
}

.composer-hint {
  font-size: 12px;
  color: var(--c-text-quaternary);
  text-align: right;
  padding-right: 4px;
}

.hint-kbd {
  display: inline-block;
  padding: 1px 6px;
  border-radius: var(--r-xs);
  background: var(--c-surface-alt);
  border: 1px solid var(--c-border);
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
  font-size: 11px;
  color: var(--c-text-secondary);
}

.hint-sep {
  margin: 0 6px;
  opacity: 0.5;
}

/* ===== 右侧预览区 ===== */
.preview-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.preview-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.preview-content {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.preview-status {
  text-align: center;
  color: #6b7280;
}

.status-icon {
  font-size: 32px;
  color: #4f46e5;
  margin-bottom: 12px;
}

.preview-empty {
  margin: auto;
}

@media (max-width: 900px) {
  .chat-body {
    flex-direction: column;
  }

  .chat-panel {
    width: 100%;
    min-width: 0;
    height: 50%;
  }
}
</style>
