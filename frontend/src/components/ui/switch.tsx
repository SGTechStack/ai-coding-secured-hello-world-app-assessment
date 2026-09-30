'use client';

import { cn } from '@/lib/utils';
import { Switch as BaseSwitch } from '@base-ui/react/switch';
import type { ComponentPropsWithoutRef } from 'react';

export function Switch({ className, ...props }: ComponentPropsWithoutRef<typeof BaseSwitch.Root>) {
  return (
    <BaseSwitch.Root
      className={cn(
        'peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors',
        'bg-bg-muted data-[checked]:bg-accent',
        'focus-visible:outline-accent focus-visible:outline-1 focus-visible:outline-offset-0',
        'data-[disabled]:cursor-not-allowed data-[disabled]:opacity-50',
        className,
      )}
      {...props}
    >
      <BaseSwitch.Thumb
        className={cn(
          // Hairline border defines the thumb edge in both modes — the surface
          // fill flips dark in dark mode and would otherwise blend into the track.
          'border-border-strong bg-surface pointer-events-none block h-4 w-4 rounded-full border shadow-sm transition-transform',
          'translate-x-0 data-[checked]:translate-x-4',
        )}
      />
    </BaseSwitch.Root>
  );
}
