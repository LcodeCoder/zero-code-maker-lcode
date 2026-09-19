<template>
  <div id="chatHistoryManagePage">
    <h2>对话管理</h2>
    <p>查询和查看所有应用的用户与 AI 对话记录。</p>

    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="对话 id">
        <a-input v-model:value="searchParams.id" placeholder="输入对话 id" allow-clear />
      </a-form-item>
      <a-form-item label="应用 id">
        <a-input v-model:value="searchParams.appId" placeholder="输入应用 id" allow-clear />
      </a-form-item>
      <a-form-item label="用户 id">
        <a-input v-model:value="searchParams.userId" placeholder="输入用户 id" allow-clear />
      </a-form-item>
      <a-form-item label="消息类型">
        <a-select
          v-model:value="searchParams.messageType"
          placeholder="选择消息类型"
          allow-clear
          style="width: 140px"
          :options="messageTypeOptions"
        />
      </a-form-item>
      <a-form-item label="消息内容">
        <a-input v-model:value="searchParams.message" placeholder="输入消息内容" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="loading">搜索</a-button>
          <a-button @click="resetSearch">重置</a-button>
          <a-button :disabled="loading" @click="getData">刷新</a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <a-divider />

    <a-table
      row-key="id"
      :loading="loading"
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :scroll="{ x: 1100 }"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'messageType'">
          <a-tag :color="isAiMessage(record.messageType) ? 'cyan' : 'blue'">
            {{ messageTypeLabel(record.messageType) }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'message'">
          <a-typography-paragraph
            class="message-cell"
            :content="record.message || '-'"
            :ellipsis="{ rows: 2, expandable: true, symbol: '展开' }"
          />
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="router.push(`/app/chat/${record.appId}`)">
            查看应用对话
          </a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController.ts'

const router = useRouter()

const columns = [
  { title: 'id', dataIndex: 'id', width: 170 },
  { title: '应用 id', dataIndex: 'appId', width: 170 },
  { title: '用户 id', dataIndex: 'userId', width: 170 },
  { title: '消息类型', dataIndex: 'messageType', width: 110 },
  { title: '消息内容', dataIndex: 'message', minWidth: 320 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 130, fixed: 'right' },
]

const messageTypeOptions = [
  { label: '用户消息', value: 'user' },
  { label: 'AI 消息', value: 'ai' },
]

const data = ref<API.ChatHistory[]>([])
const total = ref(0)
const loading = ref(false)
let requestId = 0

const searchParams = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const isAiMessage = (messageType?: string) => {
  const normalizedType = messageType?.toLowerCase()
  return normalizedType === 'ai' || normalizedType === 'assistant'
}

const messageTypeLabel = (messageType?: string) => {
  if (!messageType) return '-'
  return isAiMessage(messageType) ? 'AI' : '用户'
}

const formatTime = (time?: string) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

const getData = async () => {
  const currentRequest = ++requestId
  loading.value = true
  try {
    const res = await listAllChatHistoryByPageForAdmin({
      ...searchParams,
      id: searchParams.id || undefined,
      appId: searchParams.appId || undefined,
      userId: searchParams.userId || undefined,
      message: searchParams.message?.trim() || undefined,
    })
    if (currentRequest !== requestId) return

    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = Number(res.data.data.totalRow ?? 0)
      const lastPage = Math.max(1, Math.ceil(total.value / (searchParams.pageSize ?? 10)))
      if ((searchParams.pageNum ?? 1) > lastPage) {
        searchParams.pageNum = lastPage
        await getData()
      }
    } else {
      message.error(res.data.message || '数据获取失败')
    }
  } catch {
    if (currentRequest === requestId) message.error('获取对话列表失败，请稍后重试')
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

const resetSearch = () => {
  Object.assign(searchParams, {
    pageNum: 1,
    id: undefined,
    appId: undefined,
    userId: undefined,
    messageType: undefined,
    message: undefined,
  })
  getData()
}

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (count: number) => `共 ${count} 条`,
}))

const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.pageSize !== searchParams.pageSize ? 1 : page.current
  searchParams.pageSize = page.pageSize
  getData()
}

const doSearch = () => {
  searchParams.pageNum = 1
  getData()
}

onMounted(() => {
  getData()
})
</script>

<style scoped>
#chatHistoryManagePage {
  padding: 24px;
  background: var(--c-surface);
  border-radius: 12px;
}

#chatHistoryManagePage > p {
  color: var(--c-text-secondary);
  margin-bottom: 24px;
}

#chatHistoryManagePage :deep(.ant-form-item) {
  margin-bottom: 12px;
}

.message-cell {
  margin-bottom: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
