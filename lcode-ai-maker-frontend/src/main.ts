import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';
import '@/styles/theme.css'
import App from './App.vue'
import router from './router'
import '@/access.ts'
import { vReveal } from '@/directives/reveal'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)
// 全局渐进入场指令：任意元素 v-reveal 即可滚动/挂载时翻牌
app.directive('reveal', vReveal)
app.mount('#app')

