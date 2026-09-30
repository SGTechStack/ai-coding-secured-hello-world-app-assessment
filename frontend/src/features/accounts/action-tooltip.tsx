import type { ReactElement } from 'react';
import { Tooltip, TooltipContent, TooltipTrigger } from '@components/ui/tooltip';

/** Shows `label` in a tooltip on hover/focus of the icon-only `children` trigger. */
export function ActionTooltip({ label, children }: { label: string; children: ReactElement }) {
  return (
    <Tooltip>
      <TooltipTrigger render={children} />
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}
