import { cn } from '@/lib/utils';
import { Separator as BaseSeparator } from '@base-ui/react/separator';
import type { ComponentPropsWithoutRef } from 'react';

export function Separator({
  className,
  orientation = 'horizontal',
  ...props
}: ComponentPropsWithoutRef<typeof BaseSeparator>) {
  return (
    <BaseSeparator
      orientation={orientation}
      className={cn('bg-border shrink-0', orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px', className)}
      {...props}
    />
  );
}
