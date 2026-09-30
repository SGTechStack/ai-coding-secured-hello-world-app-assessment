'use client';

import { cn } from '@/lib/utils';
import { Radio as BaseRadio } from '@base-ui/react/radio';
import { RadioGroup as BaseRadioGroup } from '@base-ui/react/radio-group';
import type { ComponentPropsWithoutRef } from 'react';

export function RadioGroup({ className, ...props }: ComponentPropsWithoutRef<typeof BaseRadioGroup>) {
  return <BaseRadioGroup className={cn('grid gap-2', className)} {...props} />;
}

export function RadioGroupItem({ className, ...props }: ComponentPropsWithoutRef<typeof BaseRadio.Root>) {
  return (
    <BaseRadio.Root
      className={cn(
        'peer border-border-strong bg-surface inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full border shadow-sm transition-colors',
        'data-[checked]:border-accent',
        'focus-visible:outline-accent focus-visible:outline-1 focus-visible:outline-offset-0',
        'data-[disabled]:cursor-not-allowed data-[disabled]:opacity-50',
        className,
      )}
      {...props}
    >
      <BaseRadio.Indicator className="bg-accent h-2 w-2 rounded-full data-[unchecked]:hidden" />
    </BaseRadio.Root>
  );
}
