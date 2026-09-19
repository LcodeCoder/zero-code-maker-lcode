/**
 * useTypewriter —— 打字机占位符
 * --------------------------------------------------------------------------
 * 让输入框 placeholder 像有人在逐字输入一样循环展示多句话，
 * 带闪烁光标。尊重 prefers-reduced-motion：开启减弱动效时静态显示首句。
 * --------------------------------------------------------------------------
 */
import { onBeforeUnmount, ref } from 'vue'

interface TypewriterOptions {
  typeSpeed?: number // 打字每字间隔 ms
  deleteSpeed?: number // 删除每字间隔 ms
  pause?: number // 打完一句后的停留 ms
  holdStart?: number // 删空到下一句的间隔 ms
  cursor?: boolean // 是否带闪烁光标
  blinkSpeed?: number // 光标闪烁间隔 ms
}

export function useTypewriter(phrases: string[], options: TypewriterOptions = {}) {
  const {
    typeSpeed = 75,
    deleteSpeed = 32,
    pause = 1600,
    holdStart = 360,
    cursor = true,
    blinkSpeed = 500,
  } = options

  const text = ref('')
  let phraseIndex = 0
  let charIndex = 0
  let phase: 'type' | 'hold' | 'delete' | 'wait' = 'type'
  let cursorVisible = true
  let timer: ReturnType<typeof setTimeout> | null = null
  let blinkTimer: ReturnType<typeof setInterval> | null = null

  const reduced =
    typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

  const render = () => {
    const phrase = phrases[phraseIndex] ?? ''
    const body = phrase.slice(0, charIndex)
    return cursor ? body + (cursorVisible ? '▋' : ' ') : body
  }

  const tick = () => {
    const phrase = phrases[phraseIndex] ?? ''
    if (phase === 'type') {
      charIndex += 1
      if (charIndex >= phrase.length) {
        charIndex = phrase.length
        text.value = render()
        phase = 'hold'
        timer = setTimeout(tick, pause)
        return
      }
      text.value = render()
      timer = setTimeout(tick, typeSpeed)
    } else if (phase === 'hold') {
      phase = 'delete'
      timer = setTimeout(tick, deleteSpeed)
    } else if (phase === 'delete') {
      charIndex -= 1
      if (charIndex <= 0) {
        charIndex = 0
        text.value = render()
        phase = 'wait'
        phraseIndex = (phraseIndex + 1) % Math.max(phrases.length, 1)
        timer = setTimeout(tick, holdStart)
        return
      }
      text.value = render()
      timer = setTimeout(tick, deleteSpeed)
    } else {
      phase = 'type'
      timer = setTimeout(tick, typeSpeed)
    }
  }

  const start = () => {
    if (!phrases.length) return
    if (reduced) {
      text.value = phrases[0] ?? ''
      return
    }
    if (cursor) {
      blinkTimer = setInterval(() => {
        cursorVisible = !cursorVisible
        text.value = render()
      }, blinkSpeed)
    }
    text.value = render()
    tick()
  }

  const stop = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    if (blinkTimer) {
      clearInterval(blinkTimer)
      blinkTimer = null
    }
  }

  onBeforeUnmount(stop)

  return { text, start, stop }
}
