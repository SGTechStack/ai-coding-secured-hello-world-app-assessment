import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { Button } from '@components/ui/button';
import { ConfirmDialog } from './confirm-dialog';

// ConfirmDialog is controlled (open / onOpenChange), so each story wraps it in a small
// stateful host with a trigger button to open it.
function ConfirmDialogDemo(props: Partial<React.ComponentProps<typeof ConfirmDialog>>) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        Open dialog
      </Button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title="Confirm action"
        description="Are you sure you want to continue?"
        confirmLabel="Confirm"
        onConfirm={() => setOpen(false)}
        {...props}
      />
    </>
  );
}

const meta: Meta<typeof ConfirmDialogDemo> = {
  title: 'Layout/ConfirmDialog',
  component: ConfirmDialogDemo,
};

export default meta;
type Story = StoryObj<typeof ConfirmDialogDemo>;

// Default confirm — neutral (solid) confirm button.
export const Default: Story = {};

// Destructive intent — confirm button uses the danger variant.
export const Destructive: Story = {
  args: {
    title: 'Delete item',
    description: 'This action cannot be undone.',
    confirmLabel: 'Delete',
    variant: 'destructive',
  },
};

// In-flight — confirm button disabled and showing the pending label.
export const Pending: Story = {
  args: {
    isPending: true,
    pendingLabel: 'Saving…',
  },
};

// Error surfaced from a failed action (Error instance → message shown).
export const WithError: Story = {
  args: {
    variant: 'destructive',
    confirmLabel: 'Delete',
    error: new Error('Could not delete item. Please try again.'),
  },
};

// Non-Error value falls back to the generic (or custom) fallback message.
export const WithErrorFallback: Story = {
  args: {
    error: 'oops',
    errorFallback: 'Something went wrong while saving your changes.',
  },
};
