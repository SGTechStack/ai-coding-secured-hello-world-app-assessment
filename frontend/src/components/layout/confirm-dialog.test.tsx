import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmDialog } from './confirm-dialog';

function renderDialog(overrides: Partial<React.ComponentProps<typeof ConfirmDialog>> = {}) {
  const defaults = {
    open: true,
    onOpenChange: vi.fn(),
    title: 'Confirm action',
    description: 'Are you sure?',
    confirmLabel: 'Confirm',
    onConfirm: vi.fn(),
  };
  render(<ConfirmDialog {...defaults} {...overrides} />);
  return defaults;
}

describe('ConfirmDialog', () => {
  it('renders title and description when open', () => {
    renderDialog();
    expect(screen.getByText('Confirm action')).toBeInTheDocument();
    expect(screen.getByText('Are you sure?')).toBeInTheDocument();
  });

  it('does not render content when closed', () => {
    renderDialog({ open: false });
    expect(screen.queryByText('Confirm action')).not.toBeInTheDocument();
  });

  it('calls onConfirm when confirm button is clicked', async () => {
    const user = userEvent.setup();
    const { onConfirm } = renderDialog();
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it('calls onOpenChange(false) when cancel button is clicked', async () => {
    const user = userEvent.setup();
    const { onOpenChange } = renderDialog();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('disables the confirm button while isPending', () => {
    renderDialog({ isPending: true });
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled();
  });

  it('shows pendingLabel while isPending when provided', () => {
    renderDialog({ isPending: true, pendingLabel: 'Saving…' });
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeInTheDocument();
  });

  it('falls back to confirmLabel while isPending when pendingLabel is omitted', () => {
    renderDialog({ isPending: true });
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeInTheDocument();
  });

  it('shows Error message when error is an Error instance', () => {
    renderDialog({ error: new Error('Delete failed') });
    expect(screen.getByText('Delete failed')).toBeInTheDocument();
  });

  it('shows errorFallback when error is a non-Error value', () => {
    renderDialog({ error: 'oops' });
    expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument();
  });

  it('shows custom errorFallback when provided', () => {
    renderDialog({ error: 42, errorFallback: 'Custom error message' });
    expect(screen.getByText('Custom error message')).toBeInTheDocument();
  });

  it('does not show error section when error is null', () => {
    renderDialog({ error: null });
    expect(screen.queryByText('Something went wrong. Please try again.')).not.toBeInTheDocument();
  });
});
