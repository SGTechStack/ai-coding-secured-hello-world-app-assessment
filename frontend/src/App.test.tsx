import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from '@/App'

describe('App', () => {
  it('T-HDR-006: mounting the app inserts no inline script', async () => {
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Secured Hello World' })).toBeInTheDocument()
    const inlineScripts = [...document.querySelectorAll('script')].filter((s) => !s.src)
    expect(inlineScripts).toHaveLength(0)
    expect(document.querySelectorAll('style')).toHaveLength(0)
  })
})
