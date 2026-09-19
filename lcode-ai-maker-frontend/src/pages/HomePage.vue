<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  BulbOutlined,
  CalendarOutlined,
  CodeOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  LayoutOutlined,
  RocketOutlined,
  StarFilled,
  ThunderboltFilled,
  UserOutlined,
} from '@ant-design/icons-vue'
import { addApp, listMyAppByPage, listFeaturedAppByPage } from '@/api/appController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import {
  CodeGenType,
  getAppPreviewUrl,
  getCodeGenTypeShortLabel,
  CODE_GEN_TYPE_OPTIONS,
} from '@/constants/app.ts'
import AppManageActions from '@/components/AppManageActions.vue'
import { useTypewriter } from '@/composables/useTypewriter'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 提示词输入
const prompt = ref('')
const creating = ref(false)
const promptInput = ref<{ focus: () => void } | null>(null)

// 打字机占位符：循环展示示例需求，逐字输入 + 闪烁光标
const { text: dynamicPlaceholder, start: startTypewriter } = useTypewriter(
  [
    '帮我做一个极简风格的个人摄影作品集',
    '生成一个企业官网首页',
    '做一个在线简历展示页',
    '帮我搭建一个产品介绍页',
    '创造一个个人博客网站',
  ],
  { typeSpeed: 70, deleteSpeed: 30, pause: 1700 },
)

// 代码生成类型（默认单页 HTML）
const selectedCodeGenType = ref<string>(CodeGenType.HTML)

// 分段控件的选项（去掉冗长后缀，界面更紧凑）
const codeGenTypeOptions = CODE_GEN_TYPE_OPTIONS.map((item) => ({
  payload: item.value === CodeGenType.HTML ? '单页 HTML' : '多文件',
  value: item.value,
  title: item.label,
}))

// 快捷提示词
const promptSuggestions = [
  { label: '个人博客', prompt: '帮我做一个个人博客网站', icon: FileTextOutlined },
  { label: '企业官网', prompt: '生成一个企业官网首页', icon: LayoutOutlined },
  { label: '在线简历', prompt: '做一个在线简历展示页', icon: UserOutlined },
  { label: '产品介绍', prompt: '帮我搭建一个产品介绍页', icon: RocketOutlined },
]

// 我的应用
const myApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const myLoading = ref(false)
let myRequestId = 0
const mySearchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 12,
})

// 精选应用
const featuredApps = ref<API.AppVO[]>([])
const featuredTotal = ref(0)
const featuredSearchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 12,
})

// 根据应用名生成稳定的渐变色(无封面时用作占位背景)
const coverGradients = [
  'linear-gradient(135deg, #ddd6fe, #a5b4fc)',
  'linear-gradient(135deg, #fce7f3, #e9b8d5)',
  'linear-gradient(135deg, #dbeafe, #a5c9ee)',
  'linear-gradient(135deg, #d1fae5, #a0cfc9)',
  'linear-gradient(135deg, #ffedd5, #f3cfac)',
  'linear-gradient(135deg, #e0e7ff, #bbb8e4)',
]
const gradientOf = (app: API.AppVO) => {
  const key = String(app.id ?? app.appName ?? '')
  let hash = 0
  for (let i = 0; i < key.length; i++) {
    hash = (hash * 31 + key.charCodeAt(i)) >>> 0
  }
  return coverGradients[hash % coverGradients.length]
}

// 应用已生成网站时返回首页预览地址（用作卡片缩略图来源）
const previewUrlOf = (app: API.AppVO) => {
  if (app.id && app.codeGenType) {
    return getAppPreviewUrl(app.codeGenType, app.id)
  }
  return ''
}

// 创建日期格式化为 YYYY-MM-DD
const formatDate = (t?: string) => {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 10)
}

// 应用描述：取初始提示词，去掉多余空白，过长截断
const descOf = (app: API.AppVO) => {
  const text = (app.initPrompt || '').replace(/\s+/g, ' ').trim()
  return text || '点击进入对话，继续完善你的应用'
}

// 生成类型文案（卡片角标）：按类型表查询，支持未来新增类型，未命中时回退为可读形式
const typeLabelOf = (app: API.AppVO) => getCodeGenTypeShortLabel(app.codeGenType)

// 创建应用并跳转到对话页
const handleCreateApp = async () => {
  if (creating.value) return
  const initPrompt = prompt.value.trim()
  if (!initPrompt) {
    message.warning('请先输入你的需求')
    return
  }
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    router.push('/user/login')
    return
  }
  creating.value = true
  try {
    const res = await addApp({ initPrompt, codeGenType: selectedCodeGenType.value })
    if (res.data.code === 0 && res.data.data) {
      // 对话页会根据历史记录判断是否自动发送初始提示词，无需 URL 标记。
      router.push(`/app/chat/${res.data.data}`)
    } else {
      message.error(res.data.message || '创建应用失败')
    }
  } catch {
    message.error('创建应用失败,请稍后重试')
  } finally {
    creating.value = false
  }
}

const fillPrompt = (text: string) => {
  prompt.value = text
  promptInput.value?.focus()
}

// 获取我的应用
const getMyApps = async () => {
  const requestId = ++myRequestId
  if (!loginUserStore.loginUser.id) return
  myLoading.value = true
  try {
    const res = await listMyAppByPage({ ...mySearchParams })
    if (requestId !== myRequestId) return
    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records ?? []
      myTotal.value = res.data.data.totalRow ?? 0
      const lastPage = Math.max(1, Math.ceil(myTotal.value / (mySearchParams.pageSize ?? 12)))
      if ((mySearchParams.pageNum ?? 1) > lastPage) {
        mySearchParams.pageNum = lastPage
        await getMyApps()
      }
    } else {
      message.error(res.data.message || '获取我的应用失败')
    }
  } catch {
    if (requestId === myRequestId) message.error('获取我的应用失败')
  } finally {
    if (requestId === myRequestId) myLoading.value = false
  }
}

const searchMyApps = () => {
  mySearchParams.pageNum = 1
  getMyApps()
}

const onAppDeleted = () => {
  getMyApps()
  getFeaturedApps()
}

// 获取精选应用
const getFeaturedApps = async () => {
  try {
    const res = await listFeaturedAppByPage({ ...featuredSearchParams })
    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records ?? []
      featuredTotal.value = res.data.data.totalRow ?? 0
    }
  } catch {
    message.error('获取精选应用失败')
  }
}

const onMyPageChange = (page: number, pageSize: number) => {
  mySearchParams.pageNum = page
  mySearchParams.pageSize = pageSize
  getMyApps()
}

const onFeaturedPageChange = (page: number, pageSize: number) => {
  featuredSearchParams.pageNum = page
  featuredSearchParams.pageSize = pageSize
  getFeaturedApps()
}

onMounted(() => {
  getFeaturedApps()
  startTypewriter()
})

// 登录态可能在首页挂载后才异步就绪（App.vue 的 fetchLoginUser 未 await），
// 这里监听 loginUser.id，一旦就绪立即拉取「我的应用」，避免竞态导致列表空白。
watch(
  () => loginUserStore.loginUser.id,
  (id) => {
    if (id) {
      mySearchParams.pageNum = 1
      getMyApps()
    } else {
      // 退出登录后清空，并忽略之前尚未完成的请求
      myRequestId++
      myLoading.value = false
      myApps.value = []
      myTotal.value = 0
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="home-page">
    <section class="hero" aria-labelledby="hero-title">
      <div class="hero-badge" v-reveal="{ delay: 0.02 }">
        <span class="badge-icon"><ThunderboltFilled /></span>
        从一个想法，到一个网站
        <span class="badge-tag">AI 驱动</span>
      </div>
      <h1 id="hero-title" class="hero-title" v-reveal="{ delay: 0.08 }">
        你的灵感，<br class="mobile-break" />😍<span class="gradient-text">即刻成真</span>
      </h1>
      <p class="hero-subtitle" v-reveal="{ delay: 0.14 }">
        描述你想要的网站，让 AI 帮你完成。<br class="mobile-break" />无需写代码，从这里开始创造。
      </p>

      <div class="creation-workspace" v-reveal="{ delay: 0.2, y: 20 }">
        <div class="prompt-box">
          <div class="prompt-toolbar">
            <span id="generation-mode-label" class="toolbar-label">生成模式</span>
            <a-segmented
              v-model:value="selectedCodeGenType"
              :options="codeGenTypeOptions"
              class="mode-switch"
              aria-labelledby="generation-mode-label"
            >
              <template #label="{ value, payload }">
                <span class="mode-option">
                  <FileTextOutlined v-if="value === CodeGenType.HTML" />
                  <FolderOpenOutlined v-else />
                  <span>{{ payload }}</span>
                </span>
              </template>
            </a-segmented>
            <span class="workspace-caption"><CodeOutlined /> 让创意自由生长</span>
          </div>
          <div class="prompt-editor">
            <a-textarea
              ref="promptInput"
              v-model:value="prompt"
              class="prompt-input"
              aria-label="描述你想创建的网站"
              :placeholder="dynamicPlaceholder"
              :auto-size="{ minRows: 3, maxRows: 6 }"
              :maxlength="1000"
              @keydown.enter.exact.prevent="handleCreateApp"
            />
            <a-button
              type="primary"
              class="send-btn"
              :loading="creating"
              :disabled="!prompt.trim()"
              @click="handleCreateApp"
            >
              <span>{{ creating ? '正在创建' : '开始生成' }}</span>
              <ArrowRightOutlined v-if="!creating" />
            </a-button>
          </div>
        </div>
        <div class="suggestions" aria-label="试试这些创作灵感">
          <span class="suggestions-label"><BulbOutlined /> 试试这些灵感</span>
          <button
            v-for="item in promptSuggestions"
            :key="item.label"
            type="button"
            class="suggestion-tag"
            @click="fillPrompt(item.prompt)"
          >
            <component :is="item.icon" />
            {{ item.label }}
            <span class="suggestion-arrow" aria-hidden="true">↗</span>
          </button>
        </div>
      </div>
      <div class="creation-steps" aria-label="创作流程">
        <span><span class="step-number">01</span> 描述想法</span>
        <span class="step-line" aria-hidden="true"></span>
        <span><span class="step-number">02</span> AI 生成</span>
        <span class="step-line" aria-hidden="true"></span>
        <span><span class="step-number">03</span> 预览与发布</span>
      </div>
    </section>

    <div class="apps-container">
      <section
        v-if="loginUserStore.loginUser.id"
        class="app-section"
        aria-labelledby="my-apps-title"
      >
        <div class="section-header">
          <div class="section-heading">
            <span class="section-eyebrow">YOUR WORKSPACE</span>
            <div class="section-title-row">
              <h2 id="my-apps-title" class="section-title">我的应用</h2>
              <span v-if="myTotal" class="section-count">{{ myTotal }}</span>
            </div>
            <p class="section-description">每一个想法，都值得继续打磨。</p>
          </div>
          <button type="button" class="section-action" @click="promptInput?.focus()">
            创建新应用 <ArrowRightOutlined />
          </button>
        </div>
        <a-input-search
          v-model:value="mySearchParams.appName"
          class="my-app-search"
          placeholder="搜索我的应用名称"
          allow-clear
          enter-button="搜索"
          @search="searchMyApps"
        />
        <a-spin :spinning="myLoading">
          <div v-if="!myApps.length" class="empty-state">
            <span class="empty-icon"><AppstoreOutlined /></span>
            <h3>{{ mySearchParams.appName ? '没有找到匹配的应用' : '你的创作空间，等你开启' }}</h3>
            <p>从一句简单的描述开始，创建第一个属于你的网站。</p>
            <button type="button" class="empty-action" @click="promptInput?.focus()">
              开始第一次创作 <ArrowRightOutlined />
            </button>
          </div>
          <div v-else class="app-grid">
            <article v-for="app in myApps" :key="app.id" class="app-card">
              <RouterLink :to="`/app/chat/${app.id}`" class="app-card-link">
                <div
                  class="app-cover"
                  :style="app.cover || previewUrlOf(app) ? {} : { background: gradientOf(app) }"
                >
                  <img v-if="app.cover" :src="app.cover" :alt="app.appName" loading="lazy" />
                  <iframe
                    v-else-if="previewUrlOf(app)"
                    :src="previewUrlOf(app)"
                    class="app-cover-thumb"
                    loading="lazy"
                    scrolling="no"
                    tabindex="-1"
                    title="应用预览"
                    aria-hidden="true"
                  />
                  <span v-else class="app-cover-letter">{{ app.appName?.[0] || 'A' }}</span>
                  <span class="type-tag"><CodeOutlined /> {{ typeLabelOf(app) }}</span>
                  <div class="cover-overlay">
                    <span class="open-btn">进入对话 <ArrowRightOutlined /></span>
                  </div>
                </div>
                <div class="app-info">
                  <div class="app-name">
                    {{ app.appName || '未命名应用' }} <ArrowRightOutlined />
                  </div>
                  <div class="app-desc">{{ descOf(app) }}</div>
                  <div class="app-footer is-solo">
                    <span class="app-stat"
                      ><CalendarOutlined /> {{ formatDate(app.createTime) }}</span
                    >
                  </div>
                </div>
              </RouterLink>
              <div class="app-management">
                <AppManageActions :app="app" @deleted="onAppDeleted" />
              </div>
            </article>
          </div>
        </a-spin>
        <div v-if="myTotal > (mySearchParams.pageSize ?? 12)" class="pagination">
          <a-pagination
            :current="mySearchParams.pageNum"
            :page-size="mySearchParams.pageSize"
            :total="myTotal"
            :show-size-changer="false"
            @change="onMyPageChange"
          />
        </div>
      </section>

      <section class="app-section" aria-labelledby="featured-apps-title">
        <div class="section-header">
          <div class="section-heading">
            <span class="section-eyebrow">MADE WITH LCODE</span>
            <div class="section-title-row">
              <h2 id="featured-apps-title" class="section-title">发现更多可能</h2>
              <span class="featured-label"><StarFilled /> 精选应用</span>
            </div>
            <p class="section-description">看看大家的创意，为下一个作品找到灵感。</p>
          </div>
          <span v-if="featuredTotal" class="section-meta">{{ featuredTotal }} 个精选作品</span>
        </div>
        <div v-if="!featuredApps.length" class="empty-state featured-empty">
          <span class="empty-icon"><BulbOutlined /></span>
          <h3>下一个灵感，由你创造</h3>
          <p>精选作品将在这里展示。现在，先让你的第一个想法成真。</p>
          <button type="button" class="empty-action" @click="promptInput?.focus()">
            去试试我的想法 <ArrowRightOutlined />
          </button>
        </div>
        <div v-else class="app-grid">
          <RouterLink
            v-for="app in featuredApps"
            :key="app.id"
            :to="`/app/chat/${app.id}`"
            class="app-card"
          >
            <div
              class="app-cover"
              :style="app.cover || previewUrlOf(app) ? {} : { background: gradientOf(app) }"
            >
              <img v-if="app.cover" :src="app.cover" :alt="app.appName" loading="lazy" />
              <iframe
                v-else-if="previewUrlOf(app)"
                :src="previewUrlOf(app)"
                class="app-cover-thumb"
                loading="lazy"
                scrolling="no"
                tabindex="-1"
                title="应用预览"
                aria-hidden="true"
              />
              <span v-else class="app-cover-letter">{{ app.appName?.[0] || 'A' }}</span>
              <span class="type-tag"><CodeOutlined /> {{ typeLabelOf(app) }}</span>
              <div class="featured-badge"><StarFilled /></div>
              <div class="cover-overlay">
                <span class="open-btn">查看应用 <ArrowRightOutlined /></span>
              </div>
            </div>
            <div class="app-info">
              <div class="app-name">{{ app.appName || '未命名应用' }} <ArrowRightOutlined /></div>
              <div class="app-desc">{{ descOf(app) }}</div>
              <div class="app-footer">
                <span class="app-author">
                  <a-avatar :size="22" :src="app.user?.userAvatar" class="author-avatar">
                    <template #icon><UserOutlined /></template>
                  </a-avatar>
                  <span class="author-name">{{ app.user?.userName || '创作者' }}</span>
                </span>
                <span class="app-stat"><CalendarOutlined /> {{ formatDate(app.createTime) }}</span>
              </div>
            </div>
          </RouterLink>
        </div>
        <div v-if="featuredTotal > (featuredSearchParams.pageSize ?? 12)" class="pagination">
          <a-pagination
            :current="featuredSearchParams.pageNum"
            :page-size="featuredSearchParams.pageSize"
            :total="featuredTotal"
            :show-size-changer="false"
            @change="onFeaturedPageChange"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.my-app-search {
  max-width: 360px;
  margin-bottom: 20px;
}
.app-card-link {
  display: block;
  color: inherit;
  text-decoration: none;
}
.app-management {
  display: flex;
  justify-content: flex-end;
  padding: 0 20px 16px;
}

.home-page {
  max-width: 1160px;
  margin: 0 auto;
  padding-bottom: 72px;
}

.hero {
  position: relative;
  padding: 42px 0 28px;
  text-align: center;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 6px 7px 6px 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-pill);
  color: var(--c-text-secondary);
  background: var(--c-surface-glass-strong);
  box-shadow: var(--sh-xs);
  font-size: 12px;
}

.badge-icon {
  color: var(--c-primary);
}

.badge-tag {
  padding: 3px 8px;
  border-radius: var(--r-pill);
  color: var(--c-primary);
  background: var(--c-info-bg);
  font-size: 10px;
  font-weight: 600;
}

.hero-title {
  margin: 24px 0 18px;
  color: var(--c-text);
  font-size: clamp(36px, 4.4vw, 58px);
  font-weight: 750;
  line-height: 1.25;
  letter-spacing: -2px;
}

.gradient-text {
  color: var(--c-primary);
  background: var(--grad-primary);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  margin: 0;
  color: var(--c-text-tertiary);
  font-size: 15px;
  line-height: 1.9;
}

.mobile-break {
  display: none;
}

.creation-workspace {
  max-width: 800px;
  margin: 34px auto 0;
}

.prompt-box {
  padding: 20px 22px 16px;
  border: 1px solid var(--c-border-strong);
  border-radius: 22px;
  background: var(--c-surface);
  box-shadow:
    0 16px 48px -20px var(--c-border-focus),
    var(--sh-sm);
  text-align: left;
  transition:
    border-color 200ms ease,
    box-shadow 200ms ease;
}

.prompt-box:focus-within {
  border-color: var(--c-border-focus);
  box-shadow:
    var(--ring-focus),
    0 16px 48px -20px var(--c-border-focus);
}

.workspace-caption {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  color: var(--c-text-quaternary);
  font-size: 11px;
}

.prompt-editor {
  position: relative;
  padding-bottom: 52px;
}

.prompt-input {
  padding: 8px 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: var(--c-text);
  font-size: 16px;
  line-height: 1.9;
  resize: none;
}

.prompt-input:hover,
.prompt-input:focus {
  border: none;
  box-shadow: none;
}

.prompt-input::placeholder {
  color: var(--c-text-quaternary);
}

.send-btn {
  position: absolute;
  right: 0;
  bottom: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 40px;
  padding-inline: 18px;
  border-radius: var(--r-md);
  font-size: 13px;
  font-weight: 600;
}

.send-btn:not(:disabled) {
  border: 0;
  background: var(--grad-primary);
  box-shadow: 0 4px 10px -4px var(--c-primary-soft);
}

.send-btn:not(:disabled):hover {
  background: var(--grad-primary-hover);
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
}

.suggestions-label {
  margin-right: 4px;
  color: var(--c-text-tertiary);
  font-size: 12px;
}

.suggestion-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface-glass);
  color: var(--c-text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition:
    color 180ms ease,
    background-color 180ms ease,
    border-color 180ms ease;
}

.suggestion-tag:hover {
  border-color: var(--c-border-focus);
  background: var(--c-surface);
  color: var(--c-primary);
}

.suggestion-arrow {
  margin-left: 2px;
  color: var(--c-text-quaternary);
}

.creation-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 22px;
  color: var(--c-text-tertiary);
  font-size: 11px;
}

.creation-steps > span:not(.step-line) {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.step-number {
  color: var(--c-primary-soft);
  font-size: 10px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.step-line {
  width: 32px;
  height: 1px;
  background: var(--c-border-strong);
}

.apps-container {
  display: flex;
  flex-direction: column;
  gap: 34px;
  padding-top: 8px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.section-eyebrow {
  display: block;
  margin-bottom: 10px;
  color: var(--c-text-tertiary);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1.8px;
}

.section-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.section-title {
  margin: 0;
  color: var(--c-text);
  font-size: 24px;
  font-weight: 650;
  line-height: 1.4;
  letter-spacing: -0.5px;
}

.section-description {
  margin: 8px 0 0;
  color: var(--c-text-tertiary);
  font-size: 13px;
  line-height: 1.7;
}

.section-count,
.featured-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: var(--r-xs);
  color: var(--c-primary);
  background: var(--c-info-bg);
  font-size: 11px;
}

.featured-label {
  color: var(--c-warning);
  background: var(--c-warning-bg);
}

.section-meta {
  color: var(--c-text-tertiary);
  font-size: 12px;
  white-space: nowrap;
}

.section-action,
.empty-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  color: var(--c-text-secondary);
  background: var(--c-surface);
  font: inherit;
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition:
    border-color 180ms ease,
    color 180ms ease;
}

.section-action:hover,
.empty-action:hover {
  border-color: var(--c-border-focus);
  color: var(--c-primary);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36px 20px;
  border: 1px dashed var(--c-border-strong);
  border-radius: var(--r-lg);
  background: var(--c-surface-glass);
  text-align: center;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
  border: 1px solid var(--c-border);
  border-radius: 15px;
  background: var(--c-surface);
  box-shadow: var(--sh-sm);
  color: var(--c-primary-soft);
  font-size: 23px;
  transform: rotate(-6deg);
}

.empty-state h3 {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 15px;
  font-weight: 600;
}

.empty-state p {
  margin: 9px 0 18px;
  color: var(--c-text-tertiary);
  font-size: 12px;
  line-height: 1.8;
}

.empty-action {
  color: var(--c-primary);
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.app-card {
  display: block;
  min-width: 0;
  overflow: hidden;
  padding: 7px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-lg);
  background: var(--c-surface);
  box-shadow: var(--sh-xs);
  text-decoration: none;
  transition:
    transform 200ms ease,
    box-shadow 200ms ease,
    border-color 200ms ease;
}

.app-card:hover {
  transform: translateY(-4px);
  border-color: var(--c-border-focus);
  box-shadow: var(--sh-lg);
}

.app-card:focus-visible {
  outline: 2px solid var(--c-primary);
  outline-offset: 3px;
}

.app-cover {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  border-radius: 10px;
  background: var(--c-surface-alt);
}

.app-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 300ms ease;
}

.app-card:hover .app-cover img {
  transform: scale(1.035);
}

.app-cover-thumb {
  position: absolute;
  top: 0;
  left: 50%;
  width: 1200px;
  height: 900px;
  border: 0;
  pointer-events: none;
  transform: translateX(-50%) scale(0.35);
  transform-origin: top center;
}

.app-cover-letter {
  color: var(--c-surface);
  font-size: 60px;
  font-weight: 700;
  text-transform: uppercase;
}

.type-tag,
.featured-badge {
  position: absolute;
  top: 10px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-xs);
  background: var(--c-surface-glass-strong);
  backdrop-filter: blur(8px);
  font-size: 10px;
  font-weight: 500;
}

.type-tag {
  right: 10px;
  color: var(--c-text-secondary);
}

.featured-badge {
  left: 10px;
  color: var(--c-warning);
}

.cover-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(17, 24, 39, 0.2);
  opacity: 0;
  transition: opacity 200ms ease;
}

.app-card:hover .cover-overlay,
.app-card:focus-visible .cover-overlay {
  opacity: 1;
}

.open-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: var(--r-pill);
  background: var(--c-surface);
  color: var(--c-primary);
  box-shadow: var(--sh-md);
  font-size: 12px;
}

.app-info {
  padding: 16px 10px 8px;
}

.app-name {
  position: relative;
  overflow: hidden;
  padding-right: 24px;
  color: var(--c-text);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-name :deep(.anticon) {
  position: absolute;
  top: 4px;
  right: 0;
  color: var(--c-text-quaternary);
  font-size: 12px;
}

.app-desc {
  display: -webkit-box;
  min-height: 40px;
  overflow: hidden;
  margin-top: 7px;
  color: var(--c-text-tertiary);
  font-size: 12px;
  line-height: 20px;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.app-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--c-border);
}

.app-footer.is-solo {
  justify-content: flex-end;
}

.app-author {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.author-avatar {
  flex-shrink: 0;
  background: var(--c-info-bg);
  color: var(--c-primary-soft);
  font-size: 11px;
}

.author-name {
  overflow: hidden;
  color: var(--c-text-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-stat {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 5px;
  color: var(--c-text-tertiary);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

/* 生成模式切换条 */
.prompt-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 14px;
  padding: 0 0 18px;
}

.toolbar-label {
  color: var(--c-text-tertiary);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.mode-switch {
  padding: 4px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  background: var(--c-surface-alt);
}

.mode-switch:hover,
.mode-switch:focus {
  background: var(--c-surface-alt);
}

.mode-switch :deep(.ant-segmented-item) {
  min-width: 110px;
  border-radius: var(--r-sm);
  color: var(--c-text-tertiary);
  transition: color 180ms ease;
}

.mode-switch :deep(.ant-segmented-item-label) {
  min-height: 34px;
  padding: 0 14px;
  line-height: 34px;
}

.mode-option {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 500;
}

.mode-option :deep(.anticon) {
  font-size: 15px;
}

.mode-switch :deep(.ant-segmented-item::after) {
  border-radius: var(--r-sm);
}

.mode-switch :deep(.ant-segmented-item:hover),
.mode-switch :deep(.ant-segmented-item-selected) {
  color: var(--c-primary);
}

.mode-switch :deep(.ant-segmented-item-selected),
.mode-switch :deep(.ant-segmented-thumb) {
  border-radius: var(--r-sm);
  background: var(--c-surface);
  box-shadow:
    var(--sh-sm),
    inset 0 0 0 1px var(--c-border);
}

.mode-switch :deep(.ant-segmented-item:has(input:focus-visible)) {
  outline: 2px solid var(--c-primary);
  outline-offset: 1px;
}

@media (max-width: 480px) {
  .prompt-toolbar {
    padding-inline: 0;
    gap: 8px;
  }

  .mode-switch :deep(.ant-segmented-item) {
    min-width: 94px;
  }

  .mode-switch :deep(.ant-segmented-item-label) {
    padding-inline: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mode-switch :deep(.ant-segmented-item),
  .mode-switch :deep(.ant-segmented-thumb) {
    transition: none !important;
    animation: none !important;
  }
}

@media (max-width: 900px) {
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px;
  }
}

@media (max-width: 600px) {
  .home-page {
    padding-bottom: 40px;
  }

  .hero {
    padding: 28px 0 28px;
  }

  .hero-title {
    margin: 22px 0 16px;
    font-size: 40px;
    line-height: 1.35;
    letter-spacing: -1.5px;
  }

  .mobile-break {
    display: block;
  }

  .hero-subtitle {
    font-size: 13px;
  }

  .creation-workspace {
    margin-top: 26px;
  }

  .prompt-box {
    padding: 14px;
    border-radius: var(--r-lg);
  }

  .workspace-caption {
    display: none;
  }

  .prompt-input {
    font-size: 15px;
  }

  .suggestions {
    gap: 8px;
    max-width: 350px;
    margin-inline: auto;
  }

  .suggestions-label {
    flex-basis: 100%;
    margin: 0 0 2px;
  }

  .suggestion-tag {
    justify-content: center;
    width: calc(50% - 4px);
    min-height: 38px;
  }

  .creation-steps {
    gap: 10px;
    font-size: 10px;
  }

  .step-line {
    width: 16px;
  }

  .section-header {
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 20px;
  }

  .section-title {
    font-size: 21px;
  }

  .section-description {
    font-size: 12px;
  }

  .app-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .apps-container {
    gap: 32px;
    padding-top: 4px;
  }

  .empty-state {
    padding: 28px 16px;
  }
}
</style>
