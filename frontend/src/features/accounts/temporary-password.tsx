import { Copy } from 'lucide-react';
import { Button } from '@components/ui/button';
import { toast } from '@components/ui/toast';
import { ActionTooltip } from './action-tooltip';

/** A Temporary Password kept blurred until hovered or focused, with a button to copy it. */
export function TemporaryPassword({ password }: { password: string }) {
  async function copy() {
    try {
      await navigator.clipboard.writeText(password);
      toast.add({ type: 'success', title: 'Temporary password copied' });
    } catch {
      toast.add({ type: 'error', title: 'Could not copy', description: 'Select the password and copy it by hand.' });
    }
  }

  return (
    <span className="inline-flex items-center gap-1 align-middle">
      <code
        tabIndex={0}
        className="rounded px-1 font-mono font-semibold blur-sm transition-[filter] select-none hover:blur-none hover:select-text focus-visible:blur-none focus-visible:select-text"
      >
        {password}
      </code>
      <ActionTooltip label="Copy password">
        <Button type="button" variant="ghost" size="icon" className="size-6" aria-label="Copy password" onClick={copy}>
          <Copy className="size-3.5" />
        </Button>
      </ActionTooltip>
    </span>
  );
}
