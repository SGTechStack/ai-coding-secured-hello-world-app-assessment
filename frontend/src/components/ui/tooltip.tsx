'use client';

import { cn } from '@/lib/utils';
import { Tooltip as BaseTooltip } from '@base-ui/react/tooltip';
import type { ComponentPropsWithoutRef, ReactNode } from 'react';

export const TooltipProvider = BaseTooltip.Provider;
export const Tooltip = BaseTooltip.Root;
export const TooltipTrigger = BaseTooltip.Trigger;

export function TooltipContent({
  className,
  children,
  side,
  sideOffset = 6,
  align,
  variant = 'solid',
  ...props
}: ComponentPropsWithoutRef<typeof BaseTooltip.Popup> & {
  side?: 'top' | 'right' | 'bottom' | 'left';
  sideOffset?: number;
  align?: 'start' | 'center' | 'end';
  variant?: 'solid' | 'glass';
  children?: ReactNode;
}) {
  return (
    <BaseTooltip.Portal>
      <BaseTooltip.Positioner side={side} sideOffset={sideOffset} align={align} className="z-[100]">
        <BaseTooltip.Popup
          className={cn(
            'border-border text-fg rounded-md border px-2.5 py-1.5 text-xs shadow-md',
            variant === 'glass' ? 'surface-glass-panel' : 'bg-surface-elevated',
            'data-[ending-style]:opacity-0 data-[starting-style]:opacity-0',
            'transition-opacity duration-150',
            className,
          )}
          {...props}
        >
          {children}
        </BaseTooltip.Popup>
      </BaseTooltip.Positioner>
    </BaseTooltip.Portal>
  );
}
