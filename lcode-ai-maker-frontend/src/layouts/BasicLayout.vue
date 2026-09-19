<template>
  <a-layout class="basic-layout">
    <!-- 全局背景：低饱和青蓝斜向色带 + 磨砂颗粒纹理 + 散落粉红米粒 -->
    <div class="app-bg" aria-hidden="true">
      <div class="bg-band"></div>
      <div class="bg-noise"></div>
      <div class="bg-specks"></div>
    </div>

    <GlobalHeader />
    <a-layout-content class="content">
      <div class="content-wrapper">
        <router-view v-slot="{ Component }">
          <transition name="route-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </a-layout-content>
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<style scoped>
.basic-layout {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* 背景固定覆盖视口，尺寸和位置不随页面内容或滚动变化。 */
.app-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  background: transparent;
}

/* 一条很宽的青→蓝斜向色带：从左下到右上横在中间，
   低饱和 + 大模糊 = 边缘不规则、柔和不刺眼 */
.bg-band {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 220vw;
  height: 62vh;
  transform: translate(-50%, -50%) rotate(-16deg);
  background: linear-gradient(
    90deg,
    rgba(34, 211, 238, 0) 0%,
    rgba(34, 211, 238, 0.62) 16%,
    rgba(56, 189, 248, 0.62) 34%,
    rgba(37, 99, 235, 0.72) 56%,
    rgba(30, 64, 175, 0.78) 76%,
    rgba(30, 58, 138, 0) 100%
  );
  filter: blur(72px);
  opacity: 0.9;
}

/* 强颗粒磨砂纹理：叠加在色带之上，磨砂质感拉满 */
.bg-noise {
  position: absolute;
  inset: 0;
  opacity: 0.55;
  mix-blend-mode: overlay;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.68' numOctaves='5' stitchTiles='stitch'/%3E%3CfeColorMatrix type='saturate' values='0'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  background-size: 200px 200px;
}

/* 散落的粉红「米粒」颗粒：细长椭圆，零星点缀在色带之上 */
.bg-specks {
  position: absolute;
  inset: 0;
  opacity: 0.6;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='400'%3E%3Cg fill='%23f9a8d4'%3E%3Cellipse cx='24' cy='56' rx='1.4' ry='2.8' transform='rotate(30 24 56)'/%3E%3Cellipse cx='132' cy='28' rx='1.3' ry='2.6' transform='rotate(-20 132 28)'/%3E%3Cellipse cx='268' cy='88' rx='1.5' ry='2.9' transform='rotate(55 268 88)'/%3E%3Cellipse cx='360' cy='40' rx='1.2' ry='2.4' transform='rotate(-40 360 40)'/%3E%3Cellipse cx='88' cy='168' rx='1.4' ry='2.7' transform='rotate(45 88 168)'/%3E%3Cellipse cx='212' cy='132' rx='1.3' ry='2.6' transform='rotate(-15 212 132)'/%3E%3Cellipse cx='324' cy='208' rx='1.5' ry='2.9' transform='rotate(60 324 208)'/%3E%3Cellipse cx='48' cy='252' rx='1.2' ry='2.5' transform='rotate(-35 48 252)'/%3E%3Cellipse cx='172' cy='284' rx='1.4' ry='2.8' transform='rotate(50 172 284)'/%3E%3Cellipse cx='296' cy='312' rx='1.3' ry='2.6' transform='rotate(-25 296 312)'/%3E%3Cellipse cx='372' cy='300' rx='1.5' ry='2.9' transform='rotate(40 372 300)'/%3E%3Cellipse cx='116' cy='372' rx='1.2' ry='2.4' transform='rotate(65 116 372)'/%3E%3Cellipse cx='244' cy='360' rx='1.4' ry='2.7' transform='rotate(-45 244 360)'/%3E%3C/g%3E%3C/svg%3E");
  background-size: 440px 440px;
  background-repeat: repeat;
}

/* ===== 内容区 ===== */
.content {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: transparent;
  padding: 24px;
}

.content-wrapper {
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  flex: 1;
}

@media (max-width: 768px) {
  .content {
    padding: 16px;
  }
}
</style>
