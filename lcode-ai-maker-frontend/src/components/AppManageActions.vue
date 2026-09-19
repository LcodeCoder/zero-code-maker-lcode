<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { deleteApp, deleteMyApp } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

const props = defineProps<{ app: API.AppVO; adminMode?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ deleted: [] }>()
const route = useRoute()
const router = useRouter()
const store = useLoginUserStore()
const deleting = ref(false)
const isAdmin = computed(() => props.adminMode && store.loginUser.userRole === 'admin')
const canManage = computed(
  () =>
    !!props.app.id &&
    !!store.loginUser.id &&
    (isAdmin.value || String(props.app.userId) === String(store.loginUser.id)),
)

const edit = () => {
  if (!canManage.value || deleting.value || props.disabled) return
  router.push({
    path: `/app/edit/${props.app.id}`,
    query: { returnTo: route.path, mode: isAdmin.value ? 'admin' : 'owner' },
  })
}

const remove = async () => {
  if (!canManage.value || deleting.value || props.disabled) return
  deleting.value = true
  try {
    const res = await (isAdmin.value ? deleteApp : deleteMyApp)({ id: props.app.id })
    if (res.data.code === 0 && res.data.data === true) {
      message.success('应用已删除')
      emit('deleted')
    } else {
      message.error(res.data.message || '删除失败，请重试')
    }
  } catch {
    message.error('删除失败，请稍后重试')
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <a-space v-if="canManage" size="small">
    <a-button size="small" :disabled="disabled || deleting" @click="edit">编辑</a-button>
    <a-popconfirm
      :title="`确认删除「${app.appName || '未命名应用'}」？`"
      ok-text="删除"
      cancel-text="取消"
      :disabled="disabled || deleting"
      :ok-button-props="{ danger: true, loading: deleting }"
      @confirm="remove"
    >
      <a-button size="small" danger :loading="deleting" :disabled="disabled">删除</a-button>
    </a-popconfirm>
  </a-space>
</template>
