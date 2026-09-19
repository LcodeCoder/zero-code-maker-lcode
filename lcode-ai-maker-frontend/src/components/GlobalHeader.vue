<template>
  <a-layout-header class="header">
    <div class="header-content">
      <RouterLink class="logo-section" to="/" aria-label="Lcode Maker 首页">
        <img src="@/assets/code maker.png" alt="logo" class="logo-img" />
        <span class="site-title">Lcode <span class="brand-accent">Maker</span></span>
      </RouterLink>
      <a-menu
        :selected-keys="selectedKeys"
        mode="horizontal"
        :items="menuItems"
        class="menu"
        aria-label="主导航"
        @click="handleMenuClick"
      />
      <div class="user-section">
        <a-dropdown v-if="loginUserStore.loginUser.id" :trigger="['click']">
          <button type="button" class="user-trigger" aria-label="打开用户菜单" @click.prevent>
            <a-avatar :size="32" :src="loginUserStore.loginUser.userAvatar" />
            <span class="user-name">{{ loginUserStore.loginUser.userName }}</span>
            <DownOutlined class="user-chevron" />
          </button>
          <template #overlay>
            <a-menu @click="handleUserMenuClick">
              <a-menu-item key="profile">
                <EditOutlined /> 修改个人信息
              </a-menu-item>
              <a-menu-item key="logout">
                <span class="logout-item"><LogoutOutlined /> 退出登录</span>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <div v-else>
          <a-button type="primary" @click="router.push('/user/login')">登录</a-button>
        </div>
      </div>
    </div>
    <EditProfileModal
      v-model:visible="editVisible"
      :user="loginUserStore.loginUser"
      @success="handleEditSuccess"
    />
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { AppstoreOutlined, DownOutlined, EditOutlined, HomeOutlined, LogoutOutlined, TeamOutlined } from '@ant-design/icons-vue'
import type { MenuProps } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import EditProfileModal from '@/components/EditProfileModal.vue'

// 获取登陆用户信息
const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()
const selectedKeys = computed(() => [route.path])
const editVisible = ref(false)
// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    icon: () => h(TeamOutlined),
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    icon: () => h(AppstoreOutlined),
    label: '应用管理',
    title: '应用管理',
  },
]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

const handleMenuClick: MenuProps['onClick'] = (e) => {
  router.push(e.key as string)
}

const handleLogout = async () => {
  try {
    await userLogout()
  } catch (e) {
    // 即使接口失败也继续清理本地状态
  }
  loginUserStore.setLoginUser({ userName: '未登录' })
  message.success('已退出登录')
  router.replace('/user/login')
}

const handleUserMenuClick: MenuProps['onClick'] = (e) => {
  if (e.key === 'logout') {
    Modal.confirm({
      title: '确认退出登录?',
      okText: '退出',
      cancelText: '取消',
      onOk: handleLogout,
    })
  } else if (e.key === 'profile') {
    editVisible.value = true
  }
}

const handleEditSuccess = (updated: API.LoginUserVO) => {
  loginUserStore.setLoginUser(updated)
}
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: auto;
  padding: 0 32px;
  line-height: normal;
  background: var(--c-surface-glass-strong);
  border-bottom: 1px solid var(--c-border);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.header-content {
  display: flex;
  align-items: center;
  gap: 40px;
  max-width: 1400px;
  min-height: 76px;
  margin: 0 auto;
}

.logo-section {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 10px;
  padding: 4px 0;
  border-radius: var(--r-sm);
  text-decoration: none;
}

.logo-img {
  width: 38px;
  height: 38px;
  object-fit: contain;
}

.site-title {
  color: var(--c-text);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.6px;
  white-space: nowrap;
}

.brand-accent {
  color: var(--c-primary);
}

.menu {
  flex: 1;
  min-width: 0;
  border: 0;
  line-height: 40px;
  background: transparent;
}

.menu :deep(.ant-menu-item),
.menu :deep(.ant-menu-submenu) {
  height: 40px;
  margin-inline: 3px;
  padding-inline: 16px;
  border-radius: var(--r-md);
  color: var(--c-text-secondary);
  font-size: 14px;
  font-weight: 500;
  line-height: 40px;
  transition: color 180ms ease, background-color 180ms ease;
}

.menu :deep(.ant-menu-item::after),
.menu :deep(.ant-menu-submenu::after) {
  display: none;
}

.menu :deep(.ant-menu-item:hover),
.menu :deep(.ant-menu-submenu:hover) {
  color: var(--c-primary);
  background: var(--c-surface-alt);
}

.menu :deep(.ant-menu-item-selected),
.menu :deep(.ant-menu-item-selected:hover) {
  color: var(--c-primary);
  background: var(--c-info-bg);
}

.menu :deep(.ant-menu-item .anticon) {
  font-size: 16px;
}

.user-section {
  flex-shrink: 0;
  margin-left: auto;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 5px 11px 5px 5px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-pill);
  background: var(--c-surface);
  cursor: pointer;
  font: inherit;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.user-trigger:hover {
  border-color: var(--c-border-focus);
  box-shadow: var(--sh-sm);
}

.user-name {
  max-width: 128px;
  overflow: hidden;
  color: var(--c-text-secondary);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-chevron {
  color: var(--c-text-tertiary);
  font-size: 10px;
}

.logo-section:focus-visible,
.user-trigger:focus-visible {
  outline: 2px solid var(--c-primary);
  outline-offset: 4px;
}

.logout-item {
  color: var(--c-danger);
}

.user-section :deep(.ant-btn-primary) {
  height: 38px;
  padding: 0 22px;
  border-radius: var(--r-md);
  font-size: 13px;
  font-weight: 600;
  box-shadow: var(--sh-xs);
}

@media (max-width: 768px) {
  .header {
    padding: 0 20px;
  }

  .header-content {
    gap: 16px;
    min-height: 68px;
  }

  .site-title {
    font-size: 18px;
  }

  .menu :deep(.ant-menu-item),
  .menu :deep(.ant-menu-submenu) {
    padding-inline: 12px;
  }

  .user-name {
    max-width: 80px;
  }
}

@media (max-width: 560px) {
  .header {
    padding: 0 16px;
  }

  .header-content {
    flex-wrap: wrap;
    gap: 0 12px;
    padding-top: 12px;
  }

  .logo-img {
    width: 32px;
    height: 32px;
  }

  .menu {
    order: 3;
    flex-basis: 100%;
    margin-top: 10px;
    padding: 8px 0;
    border-top: 1px solid var(--c-border);
  }

  .menu :deep(.ant-menu-item),
  .menu :deep(.ant-menu-submenu) {
    margin-inline: 0 4px;
  }

  .user-name {
    max-width: 64px;
  }

  .user-section :deep(.ant-btn-primary) {
    height: 34px;
    padding-inline: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .menu :deep(.ant-menu-item),
  .menu :deep(.ant-menu-submenu),
  .user-trigger {
    transition: none;
  }
}
</style>
