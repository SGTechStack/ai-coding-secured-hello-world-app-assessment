import { ScrollArea } from '@base-ui/react/scroll-area'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { App } from '@/App'

// The app's pages use no Base UI component that injects a <style> element, so this file swaps the routes for one
// that mounts a real injector (ScrollArea hides its native scrollbar with an inline <style> unless told not to).
vi.mock('@/routes', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/routes')>()
  return { ...actual, routes: [{ path: '*', Component: ScrollAreaProbe }] }
})

function ScrollAreaProbe() {
  return (
    <ScrollArea.Root>
      <ScrollArea.Viewport>
        <p>scroll-area-probe</p>
      </ScrollArea.Viewport>
    </ScrollArea.Root>
  )
}

function styleElements() {
  return document.querySelectorAll('style')
}

describe('App CSP provider', () => {
  // Order matters: React hoists the <style> into <head> and keeps it there, so the control runs last.
  it('T-HDR-006: a Base UI component mounted inside the app inserts no inline <style> or <script>', async () => {
    // A <style> already here would come from a test that ran first, not from this render.
    expect(styleElements()).toHaveLength(0)
    render(<App />)

    expect(await screen.findByText('scroll-area-probe')).toBeInTheDocument()
    expect(styleElements()).toHaveLength(0)
    expect([...document.querySelectorAll('script')].filter((s) => !s.src)).toHaveLength(0)
  })

  it('the probe is a real injector: mounted without the provider it inserts a <style> element', async () => {
    render(<ScrollAreaProbe />)

    expect(await screen.findByText('scroll-area-probe')).toBeInTheDocument()
    expect(styleElements().length).toBeGreaterThan(0)
  })
})
