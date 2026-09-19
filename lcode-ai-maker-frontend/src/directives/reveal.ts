/**
 * v-reveal —— 统一渐进入场指令
 * --------------------------------------------------------------------------
 * 元素挂载时保持「隐藏 + 下沉」，进入视口后翻牌入场。
 * 用法：
 *   v-reveal                 默认上浮渐入
 *   v-reveal="0.1"           延迟 0.1s 入场（用于列表 stagger）
 *   v-reveal="{ delay: 0.2, y: 24, once: true }"
 * 内部尊重 prefers-reduced-motion：开启减弱动效时直接显示，不动画。
 * --------------------------------------------------------------------------
 */
import type { Directive, DirectiveBinding } from 'vue'

type RevealOptions = {
  delay?: number // 秒
  y?: number // 垂直位移 px
  once?: boolean // 仅触发一次（默认 true）
}

const prefersReducedMotion = () =>
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

const normalize = (binding: DirectiveBinding): Required<RevealOptions> => {
  const v = binding.value
  if (typeof v === 'number') return { delay: v, y: 24, once: true }
  const o = (v || {}) as RevealOptions
  return {
    delay: o.delay ?? 0,
    y: o.y ?? 24,
    once: o.once ?? true,
  }
}

const applyHidden = (el: HTMLElement, y: number) => {
  el.style.willChange = 'opacity, transform'
  el.style.opacity = '0'
  el.style.transform = `translateY(${y}px)`
  el.style.transition = `opacity 0.55s cubic-bezier(0.16,1,0.3,1), transform 0.55s cubic-bezier(0.16,1,0.3,1)`
}

const show = (el: HTMLElement, delay: number) => {
  el.style.transitionDelay = `${delay}s`
  // 强制重排，确保初始态先渲染，再过渡
  void (el as HTMLElement & { _r?: number }).offsetWidth
  el.style.opacity = '1'
  el.style.transform = 'translateY(0)'
}

export const vReveal: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    // 减弱动效偏好：直接显示，不做动画
    if (prefersReducedMotion()) {
      return
    }

    const { delay, y, once } = normalize(binding)
    applyHidden(el, y)

    // 不支持 IntersectionObserver 的兜底：下个帧直接显示
    if (typeof IntersectionObserver === 'undefined') {
      requestAnimationFrame(() => show(el, delay))
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            show(el, delay)
            if (once) observer.unobserve(el)
          } else if (!once) {
            // once=false 时离开视口重新隐藏，便于反复触发
            applyHidden(el, y)
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -8% 0px' },
    )
    observer.observe(el)
    ;(el as HTMLElement & { _revealObserver?: IntersectionObserver })._revealObserver = observer
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    // binding 变化时刷新延迟（列表异步加载后 stagger 用得到）
    if (prefersReducedMotion()) return
    const { delay } = normalize(binding)
    el.style.transitionDelay = `${delay}s`
  },
  unmounted(el: HTMLElement) {
    const obs = (el as HTMLElement & { _revealObserver?: IntersectionObserver })._revealObserver
    obs?.disconnect()
  },
}
