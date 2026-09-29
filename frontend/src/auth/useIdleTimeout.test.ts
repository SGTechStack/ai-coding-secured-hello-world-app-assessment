import { fireEvent, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useIdleTimeout } from './useIdleTimeout'

const TIMEOUT = 60_000

describe('useIdleTimeout', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    window.localStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('fires once the timeout passes without input', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(true, TIMEOUT, onIdle))

    vi.advanceTimersByTime(TIMEOUT - 1)
    expect(onIdle).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(onIdle).toHaveBeenCalledOnce()
  })

  it('restarts the countdown on user input', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(true, TIMEOUT, onIdle))

    vi.advanceTimersByTime(40_000)
    fireEvent.keyDown(window)
    vi.advanceTimersByTime(40_000)
    expect(onIdle).not.toHaveBeenCalled()
    vi.advanceTimersByTime(20_000)
    expect(onIdle).toHaveBeenCalledOnce()
  })

  it('counts input in other tabs', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(true, TIMEOUT, onIdle))

    vi.advanceTimersByTime(50_000)
    // Another tab records activity through shared storage.
    window.localStorage.setItem('hello-auth:last-activity', String(Date.now()))
    vi.advanceTimersByTime(TIMEOUT - 1)
    expect(onIdle).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(onIdle).toHaveBeenCalledOnce()
  })

  it('does nothing while disabled', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(false, TIMEOUT, onIdle))

    vi.advanceTimersByTime(TIMEOUT * 5)
    expect(onIdle).not.toHaveBeenCalled()
  })
})
