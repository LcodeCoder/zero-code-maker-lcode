<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { getAppById, updateApp, updateMyApp } from '@/api/appController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { FEATURED_PRIORITY } from '@/constants/app.ts'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = computed(() => route.params.id as string)

// 是否为管理员(管理员可编辑封面、优先级)
const isAdmin = computed(
  () => loginUserStore.loginUser.userRole === 'admin' && route.query.mode !== 'owner',
)
const returnPath = computed(() => {
  const target = route.query.returnTo
  if (
    target === '/' ||
    (target === '/admin/appManage' && isAdmin.value) ||
    target === `/app/chat/${appId.value}`
  )
    return target
  return isAdmin.value ? '/admin/appManage' : '/'
})
const goBack = () => router.push(returnPath.value)

const formRef = ref()
const loading = ref(false)
const submitting = ref(false)

const form = reactive<API.AppEditRequest>({
  id: undefined,
  appName: '',
  cover: '',
  priority: 0,
})

const rules: Record<string, Rule[]> = {
  appName: [{ required: true, whitespace: true, message: '请输入应用名称', trigger: 'blur' }],
}

// 加载应用信息
const loadApp = async () => {
  loading.value = true
  try {
    const res = await getAppById({ id: appId.value as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      // 普通用户只能编辑自己的应用
      if (
        !isAdmin.value &&
        (!loginUserStore.loginUser.id ||
          String(data.userId) !== String(loginUserStore.loginUser.id))
      ) {
        message.error('无权编辑该应用')
        router.push('/')
        return
      }
      form.id = data.id
      form.appName = data.appName
      form.cover = data.cover
      form.priority = data.priority ?? 0
    } else {
      message.error(res.data.message || '应用不存在')
      router.push('/')
    }
  } catch {
    message.error('加载应用信息失败')
    router.push('/')
  } finally {
    loading.value = false
  }
}

// 封面上传:转 base64
const handleBeforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (!file.type.startsWith('image/')) {
    message.error('请选择图片文件')
    return false
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB')
    return false
  }
  const reader = new FileReader()
  reader.onload = (e) => {
    form.cover = e.target?.result as string
  }
  reader.readAsDataURL(file)
  return false
}

const handleSubmit = async () => {
  if (submitting.value || loading.value || !form.id) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    let res
    if (isAdmin.value) {
      // 管理员:支持修改名称、封面、优先级
      res = await updateApp({
        id: form.id,
        appName: form.appName?.trim(),
        cover: form.cover,
        priority: form.priority,
      })
    } else {
      // 普通用户:仅支持修改名称
      res = await updateMyApp({ id: form.id, appName: form.appName?.trim() })
    }
    if (res.data.code === 0 && res.data.data === true) {
      message.success('保存成功')
      goBack()
    } else {
      message.error(res.data.message || '保存失败')
    }
  } catch {
    message.error('保存失败,请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadApp()
})
</script>

<template>
  <div class="app-edit-page">
    <a-card class="edit-card" :loading="loading" title="编辑应用信息">
      <a-form ref="formRef" layout="vertical" :model="form" :rules="rules">
        <a-form-item label="应用名称" name="appName">
          <a-input v-model:value="form.appName" placeholder="请输入应用名称" allow-clear />
        </a-form-item>

        <!-- 以下仅管理员可编辑 -->
        <template v-if="isAdmin">
          <a-form-item label="应用封面" name="cover">
            <div class="cover-upload">
              <a-upload
                list-type="picture-card"
                class="cover-uploader"
                :show-upload-list="false"
                :before-upload="handleBeforeUpload"
                accept="image/*"
              >
                <img v-if="form.cover" :src="form.cover" alt="cover" class="cover-preview" />
                <div v-else class="cover-placeholder">
                  <PlusOutlined />
                  <div class="upload-text">上传封面</div>
                </div>
              </a-upload>
              <a-button v-if="form.cover" type="link" danger size="small" @click="form.cover = ''">
                移除
              </a-button>
            </div>
            <div class="cover-tip">支持图片文件，大小不超过 2MB</div>
          </a-form-item>

          <a-form-item label="优先级" name="priority">
            <div class="priority-row">
              <a-input-number
                v-model:value="form.priority"
                :min="0"
                :max="99"
                placeholder="数值越大越靠前"
                style="width: 200px"
              />
              <a-button @click="form.priority = FEATURED_PRIORITY"
                >设为精选({{ FEATURED_PRIORITY }})</a-button
              >
            </div>
            <div class="cover-tip">优先级为 {{ FEATURED_PRIORITY }} 时即为精选应用</div>
          </a-form-item>
        </template>

        <a-form-item>
          <a-space>
            <a-button
              type="primary"
              :loading="submitting"
              :disabled="loading || !form.id"
              @click="handleSubmit"
              >保存</a-button
            >
            <a-button @click="goBack()">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.app-edit-page {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}

.edit-card {
  width: 100%;
  max-width: 640px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.cover-upload {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-uploader :deep(.ant-upload) {
  width: 160px;
  height: 100px;
  overflow: hidden;
  padding: 0;
}

.cover-preview {
  width: 160px;
  height: 100px;
  object-fit: cover;
}

.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 12px;
}

.cover-placeholder :deep(.anticon) {
  font-size: 22px;
}

.upload-text {
  margin-top: 4px;
}

.cover-tip {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
}

.priority-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
