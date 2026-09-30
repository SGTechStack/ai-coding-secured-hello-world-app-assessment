import { cn } from '@/lib/utils';
import { type HTMLAttributes, forwardRef } from 'react';

export const Skeleton = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) => (
  <div ref={ref} aria-hidden="true" className={cn('bg-bg-muted animate-pulse rounded-md', className)} {...props} />
));
Skeleton.displayName = 'Skeleton';
