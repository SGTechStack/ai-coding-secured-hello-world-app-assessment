import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TotpCodeForm } from '@/components/TotpCodeForm'

describe('TotpCodeForm', () => {
  it('T-FE-011: pasting a six-digit code fills the single code field, which then submits it whole', async () => {
    const onCode = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<TotpCodeForm submitLabel="Verify" onCode={onCode} />)

    const field = screen.getByLabelText('Code from the app')
    await user.click(field)
    await user.paste('123456')

    expect(field).toHaveValue('123456')
    await user.click(screen.getByRole('button', { name: 'Verify' }))
    expect(onCode).toHaveBeenCalledWith('123456')
  })
})
