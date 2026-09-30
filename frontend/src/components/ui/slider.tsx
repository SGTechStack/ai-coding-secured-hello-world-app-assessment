'use client';

import { cn } from '@/lib/utils';
import { Slider as BaseSlider } from '@base-ui/react/slider';
import type { ComponentPropsWithoutRef } from 'react';

export function Slider({ className, ...props }: ComponentPropsWithoutRef<typeof BaseSlider.Root>) {
  return (
    <BaseSlider.Root className={cn('relative flex w-full touch-none items-center select-none', className)} {...props}>
      <BaseSlider.Control className="relative flex h-5 w-full items-center">
        <BaseSlider.Track className="bg-bg-muted relative h-1.5 w-full grow overflow-hidden rounded-full">
          <BaseSlider.Indicator className="bg-accent absolute h-full" />
        </BaseSlider.Track>
        <BaseSlider.Thumb
          className={cn(
            'border-accent bg-surface block h-4 w-4 rounded-full border-2 shadow-sm transition-colors',
            'focus-visible:outline-accent focus-visible:outline-1 focus-visible:outline-offset-0',
            'data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
          )}
        />
      </BaseSlider.Control>
    </BaseSlider.Root>
  );
}
