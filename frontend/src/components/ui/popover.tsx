'use client';

import { cn } from '@/lib/utils';
import { Popover as BasePopover } from '@base-ui/react/popover';
import { X } from 'lucide-react';
import type { ComponentPropsWithoutRef, ReactNode } from 'react';

export const Popover = BasePopover.Root;
export const PopoverTrigger = BasePopover.Trigger;
export const PopoverClose = BasePopover.Close;

export function PopoverContent({
  className,
  children,
  sideOffset = 8,
  variant = 'solid',
  showCloseButton = false,
  ...props
}: ComponentPropsWithoutRef<typeof BasePopover.Popup> & {
  sideOffset?: number;
  variant?: 'solid' | 'glass';
  showCloseButton?: boolean;
  children?: ReactNode;
}) {
  return (
    <BasePopover.Portal>
      <BasePopover.Positioner sideOffset={sideOffset}>
        <BasePopover.Popup
          className={cn(
            'border-border z-50 w-72 rounded-lg border p-4 shadow-md',
            variant === 'glass' ? 'surface-glass-panel' : 'bg-surface-elevated',
            'data-[starting-style]:scale-95 data-[starting-style]:opacity-0',
            'data-[ending-style]:scale-95 data-[ending-style]:opacity-0',
            'transition-all duration-150',
            className,
          )}
          {...props}
        >
          {children}
          {showCloseButton && (
            <BasePopover.Close
              className={cn(
                'text-fg-muted absolute top-3 right-3 rounded-sm opacity-70 transition-opacity',
                'focus-visible:outline-accent hover:opacity-100 focus-visible:outline-1 focus-visible:outline-offset-0',
              )}
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </BasePopover.Close>
          )}
        </BasePopover.Popup>
      </BasePopover.Positioner>
    </BasePopover.Portal>
  );
}

export function PopoverHeader({ className, ...props }: ComponentPropsWithoutRef<'div'>) {
  return <div className={cn('mb-2 flex flex-col gap-1', className)} {...props} />;
}

export function PopoverTitle({ className, ...props }: ComponentPropsWithoutRef<typeof BasePopover.Title>) {
  return <BasePopover.Title className={cn('text-fg text-sm font-semibold', className)} {...props} />;
}

export function PopoverDescription({ className, ...props }: ComponentPropsWithoutRef<typeof BasePopover.Description>) {
  return <BasePopover.Description className={cn('text-fg-muted text-sm', className)} {...props} />;
}
