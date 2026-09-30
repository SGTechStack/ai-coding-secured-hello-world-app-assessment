import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MfaDialog } from './MfaDialog'

function renderDialog(props: Partial<Parameters<typeof MfaDialog>[0]> = {}) {
  const callback = vi.fn(async (_code: string): Promise<string | undefined> => undefined)
  const onCancel = vi.fn()
  render(<MfaDialog open callback={callback} onCancel={onCancel} {...props} />)
  return { callback, onCancel }
}

describe('MfaDialog', () => {
  it('T-FE-020: titles itself "<mfaType> Verification"', async () => {
    renderDialog()

    expect(await screen.findByRole('dialog', { name: 'TOTP Verification' })).toBeInTheDocument()
  })

  it('T-FE-021: six digits and Verify call the callback with the code', async () => {
    const { callback } = renderDialog()
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Verify' }))

    await waitFor(() => expect(callback).toHaveBeenCalledWith('123456'))
  })

  it('T-FE-022: Cancel calls onCancel', async () => {
    const { onCancel, callback } = renderDialog()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Cancel' }))

    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(callback).not.toHaveBeenCalled()
  })

  it('T-FE-023: with showSpinner the Verify button shows the spinner and is not actionable', async () => {
    const { callback } = renderDialog({ showSpinner: true })
    const user = userEvent.setup()

    const verify = await screen.findByRole('button', { name: 'Verify' })
    expect(verify).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByTestId('spinner')).toBeInTheDocument()
    expect(verify).toBeDisabled()

    await user.type(screen.getByLabelText('Code from the app'), '123456')
    await user.click(verify)
    expect(callback).not.toHaveBeenCalled()
  })

  it('shows no spinner while idle', async () => {
    renderDialog()

    expect(await screen.findByRole('button', { name: 'Verify' })).toHaveAttribute('aria-busy', 'false')
    expect(screen.queryByTestId('spinner')).not.toBeInTheDocument()
  })

  it('T-FE-024: renders the description it is given', async () => {
    renderDialog({ description: 'Changes need a recent code.' })

    expect(await screen.findByRole('dialog', { name: 'TOTP Verification' })).toHaveAccessibleDescription(
      'Changes need a recent code.',
    )
  })

  it('renders nothing while closed', () => {
    renderDialog({ open: false })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
