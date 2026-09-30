'use client';

import { cn } from '@/lib/utils';
import { Progress as BaseProgress } from '@base-ui/react/progress';
import type { ComponentPropsWithoutRef } from 'react';

/**
 * Prizm Design Progress Bar Component
 *
 * Determinate and indeterminate progress indicator built on Base UI Progress.
 * Supports native aria progress attributes (`role="progressbar"`, `aria-valuenow`).
 *
 * @see {@link https://prizm-design.github.io/prizm/components/progress/}
 */
export function Progress({ className, ...props }: ComponentPropsWithoutRef<typeof BaseProgress.Root>) {
  return (
    <BaseProgress.Root className={cn('relative w-full', className)} {...props}>
      <BaseProgress.Track className="bg-bg-muted h-2 w-full overflow-hidden rounded-full">
        <BaseProgress.Indicator className="bg-accent data-[indeterminate]:animate-progress-indeterminate h-full transition-[width] duration-300 ease-out" />
      </BaseProgress.Track>
    </BaseProgress.Root>
  );
}
