<template>
  <div class="user-manage-page">
    <!-- 搜索表单 -->
    <div class="filter-card">
      <a-form layout="inline" :model="searchParams" @finish="doSearch">
        <a-form-item label="账号">
          <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" allow-clear />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="searchParams.userName" placeholder="输入用户名" allow-clear />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="searchParams.userEmail" placeholder="输入用户邮箱" allow-clear />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit">搜索</a-button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="data"
        :pagination="pagination"
        :scroll="{ x: 1100 }"
        row-key="id"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userAvatar'">
            <a-avatar v-if="record.userAvatar" :src="record.userAvatar" :size="44" />
            <a-avatar v-else :size="44">{{ record.userName?.[0] || 'U' }}</a-avatar>
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <a-tag v-if="record.userRole === 'admin'" color="green">管理员</a-tag>
            <a-tag v-else color="blue">普通用户</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
          </template>
          <template v-else-if="column.dataIndex === 'updateTime'">
            {{ dayjs(record.updateTime).format('YYYY-MM-DD HH:mm:ss') }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-popconfirm
              title="确认删除该用户?"
              ok-text="删除"
              cancel-text="取消"
              @confirm="doDelete(record)"
            >
              <a-button type="link" danger size="small">删除</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { listUserVoByPage, deleteUser } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'

const columns = [
  { title: 'id', dataIndex: 'id', width: 90 },
  { title: '账号', dataIndex: 'userAccount', width: 120 },
  { title: '用户名', dataIndex: 'userName', width: 120 },
  { title: '头像', dataIndex: 'userAvatar', width: 80 },
  { title: '邮箱', dataIndex: 'userEmail', ellipsis: true },
  { title: '简介', dataIndex: 'userProfile', ellipsis: true },
  { title: '用户角色', dataIndex: 'userRole', width: 110 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' },
]

// 展示数据
const data = ref<API.UserVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageSize: 10,
  pageNum: 1,
})

// 获取数据
const getData = async () => {
  const response = await listUserVoByPage({ ...searchParams })
  if (response.data.data) {
    data.value = response.data.data.records ?? []
    total.value = response.data.data.totalRow ?? 0
  } else {
    message.error('数据获取失败,' + response.data.message)
  }
}

// 分页参数
const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
}))

// 表格分页变化
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  getData()
}

// 条件查询
const doSearch = () => {
  searchParams.pageNum = 1
  getData()
}

// 删除用户
const doDelete = async (record: API.UserVO) => {
  if (!record.id) {
    return
  }
  const res = await deleteUser({ id: record.id })
  if (res.data.code === 0) {
    message.success('删除成功')
    getData()
  } else {
    message.error(res.data.message || '删除失败')
  }
}

onMounted(() => {
  getData()
})
</script>

<style scoped>
.user-manage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card,
.table-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px 24px;
  box-shadow: 0 2px 12px rgba(17, 24, 39, 0.05);
}

.filter-card :deep(.ant-form-item) {
  margin-bottom: 8px;
}
</style>
