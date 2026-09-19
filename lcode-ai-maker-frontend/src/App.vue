<script setup lang="ts">
import { useRoute } from 'vue-router'
import { theme } from 'ant-design-vue'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'

const route = useRoute()
const loginUserStore = useLoginUserStore()
loginUserStore.fetchLoginUser()

// 全局 antd 主题：与 theme.css 设计 Token 对齐（靛紫主色轴）
const themeConfig = {
  token: {
    colorPrimary: '#4f46e5',
    colorInfo: '#4f46e5',
    colorLink: '#4f46e5',
    colorSuccess: '#16a34a',
    colorWarning: '#d97706',
    colorError: '#ef4444',
    borderRadius: 10,
    borderRadiusLG: 12,
    borderRadiusSM: 8,
    fontFamily:
      "'Poppins', 'HarmonyOS Sans SC', 'PingFang SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    controlHeight: 38,
    controlHeightLG: 44,
  },
  algorithm: theme.defaultAlgorithm,
}
</script>

<template>
  <a-config-provider :theme="themeConfig">
    <!-- 登录/注册等 hideLayout 页面：独立全屏，不套通用布局 -->
    <router-view v-if="route.meta.hideLayout" v-slot="{ Component }">
      <transition name="route-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
    <!-- 其余页面：套通用布局（含 header/footer 与全局背景） -->
    <BasicLayout v-else />
  </a-config-provider>
</template>

<style>
#app {
  min-height: 100vh;
}
</style>
