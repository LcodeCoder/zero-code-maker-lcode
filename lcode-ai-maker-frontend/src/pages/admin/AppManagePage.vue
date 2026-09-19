<template>
  <div id="appManagePage">
    <h2>应用管理</h2>
    <p>查询所有应用，修改应用信息，设置精选或删除应用。</p>
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="应用 id">
        <a-input v-model:value="searchParams.id" placeholder="输入应用 id" allow-clear />
      </a-form-item>
      <a-form-item label="应用名称">
        <a-input v-model:value="searchParams.appName" placeholder="输入应用名称" allow-clear />
      </a-form-item>
      <a-form-item label="创建者 id">
        <a-input v-model:value="searchParams.userId" placeholder="输入创建者 id" allow-clear />
      </a-form-item>
      <a-form-item label="生成类型">
        <a-select
          v-model:value="searchParams.codeGenType"
          placeholder="选择生成类型"
          allow-clear
          style="width: 160px"
          :options="CODE_GEN_TYPE_OPTIONS"
        />
      </a-form-item>
      <a-form-item label="优先级">
        <a-input-number v-model:value="searchParams.priority" :min="0" placeholder="如 99 为精选" />
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
    <!-- 表格 -->
    <a-table
      row-key="id"
      :loading="loading"
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :scroll="{ x: 1200 }"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'cover'">
          <a-image v-if="record.cover" :src="record.cover" :width="80" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'codeGenType'">
          <a-tag color="blue">{{ codeGenTypeLabel(record.codeGenType) }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'user'">
          <div v-if="record.user" class="creator-cell">
            <a-avatar :size="22" :src="record.user.userAvatar" class="creator-avatar">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <span class="creator-name">{{ record.user.userName || '-' }}</span>
          </div>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <a-tag v-if="record.priority === FEATURED_PRIORITY" color="gold">精选</a-tag>
          <span v-else>{{ record.priority ?? 0 }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="router.push(`/app/chat/${record.id}`)"
              >查看</a-button
            >
            <AppManageActions
              :app="record"
              admin-mode
              :disabled="!!featuring[String(record.id)]"
              @deleted="getData"
            />
            <a-button
              type="link"
              size="small"
              :danger="record.priority === FEATURED_PRIORITY"
              :loading="!!featuring[String(record.id)]"
              @click="doFeatured(record)"
            >
              {{ record.priority === FEATURED_PRIORITY ? '取消精选' : '精选' }}
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { listAppByPage, updateApp } from '@/api/appController.ts'
import AppManageActions from '@/components/AppManageActions.vue'
import { CODE_GEN_TYPE_OPTIONS, FEATURED_PRIORITY } from '@/constants/app.ts'

const router = useRouter()

const columns = [
  {
    title: 'id',
    dataIndex: 'id',
    width: 90,
  },
  {
    title: '应用名称',
    dataIndex: 'appName',
  },
  {
    title: '封面',
    dataIndex: 'cover',
    width: 110,
  },
  {
    title: '初始提示词',
    dataIndex: 'initPrompt',
    ellipsis: true,
  },
  {
    title: '生成类型',
    dataIndex: 'codeGenType',
    width: 130,
  },
  {
    title: '优先级',
    dataIndex: 'priority',
    width: 90,
  },
  {
    title: '创建者 id',
    dataIndex: 'userId',
    width: 110,
  },
  {
    title: '创建者',
    dataIndex: 'user',
    width: 130,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 180,
  },
  {
    title: '操作',
    key: 'action',
    width: 290,
    fixed: 'right',
  },
]

// 展示数据（后端返回 AppVO，含创建者信息）
const data = ref<API.AppVO[]>([])
const total = ref(0)
const loading = ref(false)
const featuring = reactive<Record<string, boolean>>({})
let requestId = 0

// 搜索条件(管理员不限制每页数量)
const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const codeGenTypeLabel = (value?: string) => {
  return CODE_GEN_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || '-'
}

// 获取数据
const getData = async () => {
  const currentRequest = ++requestId
  loading.value = true
  try {
    const res = await listAppByPage({
      ...searchParams,
      id: searchParams.id || undefined,
      userId: searchParams.userId || undefined,
    })
    if (currentRequest !== requestId) return
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      const lastPage = Math.max(1, Math.ceil(total.value / (searchParams.pageSize ?? 10)))
      if ((searchParams.pageNum ?? 1) > lastPage) {
        searchParams.pageNum = lastPage
        await getData()
      }
    } else {
      message.error(res.data.message || '数据获取失败')
    }
  } catch {
    if (currentRequest === requestId) message.error('获取应用列表失败，请稍后重试')
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

const resetSearch = () => {
  Object.assign(searchParams, {
    pageNum: 1,
    id: undefined,
    appName: undefined,
    userId: undefined,
    codeGenType: undefined,
    priority: undefined,
  })
  getData()
}

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格分页变化
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.pageSize !== searchParams.pageSize ? 1 : page.current
  searchParams.pageSize = page.pageSize
  getData()
}

// 条件查询
const doSearch = () => {
  searchParams.pageNum = 1
  getData()
}

// 精选 / 取消精选：已精选的再点即取消（优先级清零）
const doFeatured = async (record: API.AppVO) => {
  if (!record.id || featuring[String(record.id)]) return
  const key = String(record.id)
  featuring[key] = true
  const isFeatured = record.priority === FEATURED_PRIORITY
  try {
    const res = await updateApp({
      id: record.id,
      priority: isFeatured ? 0 : FEATURED_PRIORITY,
    })
    if (res.data.code === 0 && res.data.data === true) {
      message.success(isFeatured ? '已取消精选' : '已设为精选')
      await getData()
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch {
    message.error('操作失败，请稍后重试')
  } finally {
    delete featuring[key]
  }
}

onMounted(() => {
  getData()
})
</script>

<style scoped>
#appManagePage {
  padding: 24px;
  background: var(--c-surface);
  border-radius: 12px;
}
#appManagePage > p {
  color: var(--c-text-secondary);
  margin-bottom: 24px;
}
#appManagePage :deep(.ant-form-item) {
  margin-bottom: 12px;
}

.creator-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.creator-avatar {
  flex-shrink: 0;
  background: var(--grad-primary);
}

.creator-name {
  font-size: 13px;
  color: var(--c-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
