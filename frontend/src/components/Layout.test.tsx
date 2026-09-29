import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { mockApi, problem } from '../test/mockApi'
import { renderApp } from '../test/renderApp'

describe('layout', () => {
  it('links to the vulnerability disclosure channel on every page', async () => {
    mockApi({ 'GET /api/me': () => problem(401, 'UNAUTHENTICATED', 'Authentication is required.') })

    renderApp('/login')

    const link = await screen.findByRole('link', { name: 'Report Vulnerability' })
    expect(link).toHaveAttribute('href', 'https://tech.gov.sg/report_vulnerability')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })
})
