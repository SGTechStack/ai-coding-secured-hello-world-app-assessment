import { cleanup, createEvent, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { axeViolations } from '../../test-support';
import { Button } from './button';
import { Dialog } from './dialog';

afterEach(cleanup);

function renderDialog(open: boolean, onClose = vi.fn()) {
  const view = render(
    <Dialog open={open} title="Something failed" description="Please try again." onClose={onClose}>
      <Button>Try again</Button>
      <Button variant="secondary" onClick={onClose}>
        Close
      </Button>
    </Dialog>,
  );
  return { ...view, onClose };
}

describe('Dialog', () => {
  it('opens as a modal alert dialog, labelled by its title and described by its message', () => {
    const showModal = vi.spyOn(HTMLDialogElement.prototype, 'showModal');
    renderDialog(true);

    const dialog = screen.getByRole('alertdialog', { name: 'Something failed' });
    expect(showModal).toHaveBeenCalledOnce();
    expect(dialog).toHaveAttribute('open');
    expect(dialog).toHaveAccessibleDescription('Please try again.');
    expect(dialog).toHaveClass('rounded-card', 'max-w-md');
    showModal.mockRestore();
  });

  it('puts initial focus on its first action', () => {
    renderDialog(true);

    expect(screen.getByRole('button', { name: 'Try again' })).toHaveFocus();
  });

  it('stays closed until opened, and closes when open turns false', () => {
    const { rerender, onClose } = renderDialog(false);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();

    rerender(
      <Dialog open title="Something failed" description="Please try again." onClose={onClose}>
        x
      </Dialog>,
    );
    expect(screen.getByRole('alertdialog')).toHaveAttribute('open');

    rerender(
      <Dialog open={false} title="Something failed" description="Please try again." onClose={onClose}>
        x
      </Dialog>,
    );
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('asks the owner to close on Esc instead of closing itself', () => {
    const { onClose } = renderDialog(true);
    const dialog = screen.getByRole('alertdialog');

    // Esc makes the browser fire a cancelable `cancel` event on the modal dialog.
    const cancel = createEvent('cancel', dialog, { cancelable: true });
    fireEvent(dialog, cancel);

    expect(onClose).toHaveBeenCalledOnce();
    expect(cancel.defaultPrevented).toBe(true);
    expect(dialog).toHaveAttribute('open');
  });

  it('shows its body between the description and the actions', () => {
    render(
      <Dialog open title="Change role" description="Pick one." body={<p>Body content</p>} onClose={vi.fn()}>
        <Button>Save</Button>
      </Dialog>,
    );

    const body = screen.getByText('Body content');
    const description = screen.getByText('Pick one.');
    const save = screen.getByRole('button', { name: 'Save' });
    expect(description.compareDocumentPosition(body) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(body.compareDocumentPosition(save) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('focuses the checked radio in its body on open', () => {
    render(
      <Dialog
        open
        title="Change role"
        body={
          <fieldset>
            <legend>Role</legend>
            <label>
              <input type="radio" name="r" value="USER" defaultChecked /> User
            </label>
            <label>
              <input type="radio" name="r" value="ADMIN" /> Admin
            </label>
          </fieldset>
        }
        onClose={vi.fn()}
      >
        <Button disabled>Save</Button>
      </Dialog>,
    );

    expect(screen.getByRole('radio', { name: 'User' })).toHaveFocus();
    expect(screen.getByRole('alertdialog', { name: 'Change role' })).not.toHaveAttribute('aria-describedby');
  });

  it('skips a disabled first action when choosing initial focus', () => {
    render(
      <Dialog open title="Change role" description="Pick one." onClose={vi.fn()}>
        <Button disabled>Save</Button>
        <Button variant="secondary">Cancel</Button>
      </Dialog>,
    );

    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
  });

  it('has no axe violations when open with a body', async () => {
    const { container } = render(
      <Dialog
        open
        title="Change role"
        description="Pick one."
        body={
          <fieldset>
            <legend>Role</legend>
            <label>
              <input type="radio" name="r" value="USER" defaultChecked /> User
            </label>
          </fieldset>
        }
        onClose={vi.fn()}
      >
        <Button>Save</Button>
      </Dialog>,
    );

    expect(await axeViolations(container)).toEqual([]);
  });
});
