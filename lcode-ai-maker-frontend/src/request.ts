import axios from 'axios'
import {message} from 'ant-design-vue'
import JSONbig from 'json-bigint'
import { API_BASE_URL } from '@/config/env.ts'

// 雪花算法生成的 Long id 超过了 JS 安全整数范围,直接用 JSON.parse 会丢精度。
// 这里用 json-bigint 解析,并将大整数转为字符串,保证 id 不被破坏。
const jsonBig = JSONbig({ storeAsString: true })

// 创建 Axios 实例
const myAxios = axios.create({
  // 统一走相对路径（开发环境由 Vite proxy 转发到后端，规避 CORS）
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true,
  // 覆盖默认的 JSON.parse,避免大整数精度丢失
  transformResponse: [
    (data) => {
      if (typeof data !== 'string') {
        return data
      }
      try {
        return jsonBig.parse(data)
      } catch {
        return data
      }
    },
  ],
})

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // Do something before request is sent
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const {data} = response
    // 未登录
    if (data.code === 40100) {
      // 不是获取用户信息的请求，并且用户目前不是已经在用户登录页面，则跳转到登录页面
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return response
  },
  function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios
