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
import { listAppChatHistory } from '@/api/chatHistoryController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { CodeGenType, getAppPreviewUrl, getDeployUrl } from '@/constants/app.ts'
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
  () =>
    !!app.value.userId &&
    !!loginUserStore.loginUser.id &&
    String(app.value.userId) === String(loginUserStore.loginUser.id),
)

// 消息列表
interface ChatMessage {
  key: string
  role: 'user' | 'ai'
  content: string
  createTime?: string
  loading?: boolean
}
const messages = reactive<ChatMessage[]>([])

const HISTORY_PAGE_SIZE = 10
const historyLoading = ref(true)
const loadingMoreHistory = ref(false)
const hasMoreHistory = ref(false)
const historyMessageCount = ref(0)
const totalHistoryCount = ref<number>()
const oldestCreateTime = ref<string>()
const loadedHistoryKeys = new Set<string>()

// 输入框
const userInput = ref('')
// 是否正在生成(SSE 进行中)
const generating = ref(false)
// 预览地址(生成完成后展示)
const previewUrl = ref('')
const previewError = ref('')
const previewBuilding = ref(false)
const previewStatusText = ref('')
const previewFingerprint = ref('')
// 部署相关
const deploying = ref(false)
const deployUrl = ref('')
const deployModalOpen = ref(false)

const messageListRef = ref<HTMLElement>()
let eventSource: EventSource | null = null
let localMessageSequence = 0
let previewCheckSequence = 0

const VUE_PREVIEW_CHECK_INTERVAL = 2000
const VUE_PREVIEW_CHECK_TIMEOUT = 8 * 60 * 1000

const createLocalMessageKey = () => `local-${Date.now()}-${localMessageSequence++}`

/** 等待指定时长，用于控制 Vue 构建产物的轮询频率。 */
const delay = (milliseconds: number) =>
  new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds))

/** 为预览地址增加时间戳，确保 iframe 加载最新一次生成的构建产物。 */
const withCacheBuster = (url: string) => {
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}t=${Date.now()}`
}

/** 终止当前预览检查，避免新一轮生成被旧的轮询结果覆盖。 */
const cancelPreviewCheck = () => {
  previewCheckSequence += 1
  previewBuilding.value = false
  previewStatusText.value = ''
}

/** 获取 Vue 构建入口内容，用于判断产物是否可用以及是否已更新。 */
const getPreviewFingerprint = async (url: string) => {
  try {
    const response = await fetch(withCacheBuster(url), {
      method: 'GET',
      credentials: 'include',
      cache: 'no-store',
    })
    return response.ok ? await response.text() : ''
  } catch {
    return ''
  }
}

/** 展示普通生成结果，或等待 Vue 工程异步构建完成后再展示 dist 产物。 */
const preparePreview = async (waitForVueBuild = false) => {
  const codeGenType = app.value.codeGenType
  if (!codeGenType) return

  const targetUrl = getAppPreviewUrl(codeGenType, appId.value)
  const currentCheck = ++previewCheckSequence
  previewUrl.value = ''
  previewError.value = ''

  if (codeGenType !== CodeGenType.VUE) {
    previewUrl.value = withCacheBuster(targetUrl)
    return
  }

  previewBuilding.value = true
  previewStatusText.value = waitForVueBuild
    ? 'Vue 源码已生成，正在安装依赖并构建工程...'
    : '正在检查 Vue 工程构建结果...'
  const previousFingerprint = waitForVueBuild ? previewFingerprint.value : ''
  const deadline = Date.now() + (waitForVueBuild ? VUE_PREVIEW_CHECK_TIMEOUT : 10000)

  while (currentCheck === previewCheckSequence && Date.now() < deadline) {
    const currentFingerprint = await getPreviewFingerprint(targetUrl)
    const buildIsReady =
      !!currentFingerprint && (!previousFingerprint || currentFingerprint !== previousFingerprint)
    if (buildIsReady) {
      if (currentCheck !== previewCheckSequence) return
      previewBuilding.value = false
      previewStatusText.value = ''
      previewFingerprint.value = currentFingerprint
      previewUrl.value = withCacheBuster(targetUrl)
      if (waitForVueBuild) message.success('Vue 工程构建完成')
      return
    }
    await delay(VUE_PREVIEW_CHECK_INTERVAL)
  }

  if (currentCheck !== previewCheckSequence) return
  previewBuilding.value = false
  previewStatusText.value = ''
  previewError.value = waitForVueBuild
    ? 'Vue 工程源码已生成，但构建产物暂不可用。请稍后重试预览，或检查后端构建日志。'
    : 'Vue 工程构建产物暂不可用，可点击下方按钮重新检查。'
}

/** 手动重新检查当前应用的预览产物。 */
const retryPreview = () => {
  void preparePreview(false)
}

const getHistoryMessageKey = (item: API.ChatHistory) => {
  if (item.id !== undefined && item.id !== null) {
    return `history-${item.id}`
  }
  return `history-${item.messageType || ''}-${item.createTime || ''}-${item.message || ''}`
}

const getMessageRole = (messageType?: string): ChatMessage['role'] => {
  const normalizedType = messageType?.toLowerCase()
  return normalizedType === 'ai' || normalizedType === 'assistant' ? 'ai' : 'user'
}

const toChatMessage = (item: API.ChatHistory): ChatMessage => ({
  key: getHistoryMessageKey(item),
  role: getMessageRole(item.messageType),
  content: item.message || '',
  createTime: item.createTime,
})

const sortHistoryByCreateTime = (records: API.ChatHistory[]) => {
  return [...records].sort((a, b) => {
    const timeCompare = (a.createTime || '').localeCompare(b.createTime || '')
    if (timeCompare !== 0) return timeCompare
    return String(a.id || '').localeCompare(String(b.id || ''))
  })
}

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
      return true
    } else {
      message.error(res.data.message || '应用不存在')
      router.push('/')
    }
  } catch {
    message.error('加载应用信息失败')
    router.push('/')
  }
  return false
}

// 游标加载对话历史。接口返回最近消息，前端统一按创建时间升序展示。
const loadChatHistory = async (loadMore = false) => {
  if (loadMore) {
    if (loadingMoreHistory.value || !hasMoreHistory.value || !oldestCreateTime.value) return false
    loadingMoreHistory.value = true
  } else {
    historyLoading.value = true
  }

  const previousCursor = oldestCreateTime.value
  const messageList = messageListRef.value
  const previousScrollHeight = messageList?.scrollHeight ?? 0
  const previousScrollTop = messageList?.scrollTop ?? 0

  try {
    const res = await listAppChatHistory({
      appId: appId.value as unknown as number,
      pageSize: HISTORY_PAGE_SIZE,
      lastCreateTime: loadMore ? oldestCreateTime.value : undefined,
    })
    if (res.data.code !== 0 || !res.data.data) {
      message.error(res.data.message || '加载对话历史失败')
      return false
    }

    const records = sortHistoryByCreateTime(res.data.data.records ?? [])
    const newMessages = records
      .map(toChatMessage)
      .filter((item) => !loadedHistoryKeys.has(item.key))

    newMessages.forEach((item) => loadedHistoryKeys.add(item.key))
    if (loadMore) {
      messages.unshift(...newMessages)
    } else {
      messages.splice(0, messages.length, ...newMessages)
    }

    historyMessageCount.value += newMessages.length
    if (!loadMore) {
      historyMessageCount.value = newMessages.length
    }

    const totalRow = Number(res.data.data.totalRow ?? 0)
    // 游标分页通常关闭 count 查询，此时 totalRow 会是 0，需要按本页数量判断是否还有数据。
    totalHistoryCount.value = totalRow > 0 ? totalRow : undefined
    const nextCursor = records[0]?.createTime
    oldestCreateTime.value = nextCursor

    if (totalHistoryCount.value !== undefined && Number.isFinite(totalHistoryCount.value)) {
      hasMoreHistory.value = historyMessageCount.value < totalHistoryCount.value
    } else {
      hasMoreHistory.value = records.length >= HISTORY_PAGE_SIZE
    }
    if (!records.length || !newMessages.length || (loadMore && nextCursor === previousCursor)) {
      hasMoreHistory.value = false
    }

    await nextTick()
    if (loadMore && messageList) {
      messageList.scrollTop = previousScrollTop + messageList.scrollHeight - previousScrollHeight
    } else {
      scrollToBottom()
    }
    return true
  } catch {
    message.error('加载对话历史失败，请稍后重试')
    return false
  } finally {
    historyLoading.value = false
    loadingMoreHistory.value = false
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
  if (previewBuilding.value) {
    message.warning('Vue 工程仍在构建中，请构建完成后再继续修改')
    return
  }
  if (!isOwner.value) {
    message.warning('只能与自己创建的应用对话')
    return
  }

  // 追加用户消息
  messages.push({ key: createLocalMessageKey(), role: 'user', content: text })
  // 追加一条 AI 占位消息
  const aiMessage = reactive<ChatMessage>({
    key: createLocalMessageKey(),
    role: 'ai',
    content: '',
    loading: true,
  })
  messages.push(aiMessage)
  scrollToBottom()

  generating.value = true
  cancelPreviewCheck()
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
  cancelPreviewCheck()
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

// 生成结束：普通模式直接预览，Vue 模式继续等待后端异步构建。
const finishGenerate = (aiMessage: ChatMessage) => {
  closeEventSource()
  aiMessage.loading = false
  generating.value = false
  void preparePreview(true)
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
  if (previewBuilding.value) {
    message.warning('Vue 工程仍在构建中，请构建完成后再部署')
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
  const [appLoaded, historyLoaded] = await Promise.all([loadApp(), loadChatHistory()])
  if (!appLoaded || !historyLoaded) return

  const historyCount = totalHistoryCount.value ?? historyMessageCount.value
  // 至少完成过一轮对话（用户消息 + AI 消息）时展示已生成网站。
  if (historyCount >= 2 && app.value.codeGenType) {
    void preparePreview(false)
  }

  // 仅自己的全新应用自动发送初始提示词，不再依赖 URL 查询参数。
  if (historyCount === 0 && app.value.initPrompt && isOwner.value) {
    sendMessage(app.value.initPrompt)
  }
})

onBeforeUnmount(() => {
  closeEventSource()
  cancelPreviewCheck()
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
          :disabled="generating || previewBuilding || deploying"
          @deleted="router.replace('/')"
        />
        <a-button
          type="primary"
          :loading="deploying"
          :disabled="!isOwner || generating || previewBuilding"
          class="deploy-btn"
          @click="handleDeploy"
        >
          <template #icon>
            <CloudUploadOutlined />
          </template>
          {{ app.codeGenType === CodeGenType.VUE ? '构建并部署' : '部署' }}
        </a-button>
      </div>
    </header>

    <!-- 核心内容区 -->
    <div class="chat-body">
      <!-- 左侧对话区 -->
      <section class="chat-panel">
        <div ref="messageListRef" class="message-list">
          <div v-if="historyLoading" class="history-status">
            <a-spin size="small" />
            <span>正在加载对话历史...</span>
          </div>
          <div v-else-if="hasMoreHistory" class="load-more-history">
            <a-button
              type="link"
              size="small"
              :loading="loadingMoreHistory"
              @click="loadChatHistory(true)"
            >
              加载更多
            </a-button>
          </div>
          <a-empty
            v-if="!historyLoading && !messages.length"
            description="开始和 AI 对话,生成你的网站应用吧"
            class="empty-tip"
          />
          <div
            v-for="msg in messages"
            :key="msg.key"
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
              <img v-else src="@/assets/code maker.png" alt="AI" class="ai-avatar-img" />
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
              :disabled="generating || previewBuilding || !isOwner"
              @keydown.enter.exact.prevent="handleSend"
            />
            <a-button
              type="primary"
              shape="circle"
              class="send-btn"
              :loading="generating"
              :disabled="!isOwner || previewBuilding || !userInput.trim()"
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
          <a-button v-if="previewUrl" type="link" size="small" @click="openPreviewUrl">
            新窗口打开
          </a-button>
        </div>
        <div class="preview-content">
          <div v-if="generating" class="preview-status">
            <LoadingOutlined spin class="status-icon" />
            <p>AI 正在生成网站文件,请稍候...</p>
          </div>
          <div v-else-if="previewBuilding" class="preview-status">
            <LoadingOutlined spin class="status-icon" />
            <p>{{ previewStatusText }}</p>
            <span class="preview-status-tip">首次构建可能需要几分钟，请稍候</span>
          </div>
          <a-result
            v-else-if="previewError"
            status="warning"
            title="预览暂不可用"
            :sub-title="previewError"
          >
            <template #extra>
              <a-button type="primary" @click="retryPreview">重新检查</a-button>
            </template>
          </a-result>
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
.header-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
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
  overflow-x: hidden;
  min-width: 0;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.empty-tip {
  margin: auto;
}

.history-status,
.load-more-history {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 28px;
  color: var(--c-text-tertiary);
  font-size: 13px;
}

.message-item {
  display: flex;
  gap: 10px;
  width: fit-content;
  min-width: 0;
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
  min-width: 0;
  max-width: 100%;
  padding: 10px 14px;
  border-radius: 12px;
  box-sizing: border-box;
  font-size: 14px;
  line-height: 1.6;
  color: #1f2937;
  overflow-wrap: anywhere;
  word-break: break-word;
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
  min-width: 0;
  max-width: 100%;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
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
  overflow-wrap: anywhere;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 行内代码 */
.markdown-body :deep(code) {
  max-width: 100%;
  padding: 2px 6px;
  font-size: 13px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  overflow-wrap: anywhere;
  word-break: break-word;
}

/* 代码块 */
.markdown-body :deep(pre) {
  max-width: 100%;
  margin: 8px 0;
  padding: 12px 14px;
  background: #f6f8fa;
  border-radius: 8px;
  box-sizing: border-box;
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
  max-width: 100%;
  table-layout: fixed;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 6px 10px;
  border: 1px solid #e5e7eb;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.markdown-body :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
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
  transition:
    border-color var(--t-base) var(--ease),
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
  transition:
    transform var(--t-base) var(--ease),
    box-shadow var(--t-base) var(--ease),
    opacity var(--t-base) var(--ease);
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

.preview-status p {
  margin: 12px 0 4px;
}

.preview-status-tip {
  color: #9ca3af;
  font-size: 12px;
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
