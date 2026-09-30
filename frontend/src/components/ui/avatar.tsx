'use client';

import { cn } from '@/lib/utils';
import { Avatar as BaseAvatar } from '@base-ui/react/avatar';
import { type VariantProps, cva } from 'class-variance-authority';
import type { ComponentPropsWithoutRef } from 'react';

const avatarVariants = cva(
  'relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-bg-muted text-fg-muted',
  {
    variants: {
      size: {
        sm: 'h-6 w-6 text-[10px]',
        md: 'h-8 w-8 text-xs',
        lg: 'h-10 w-10 text-sm',
        xl: 'h-14 w-14 text-base',
      },
    },
    defaultVariants: { size: 'md' },
  },
);

export interface AvatarProps
  extends ComponentPropsWithoutRef<typeof BaseAvatar.Root>, VariantProps<typeof avatarVariants> {}

export function Avatar({ className, size, ...props }: AvatarProps) {
  return <BaseAvatar.Root className={cn(avatarVariants({ size }), className)} {...props} />;
}

export function AvatarImage({ className, ...props }: ComponentPropsWithoutRef<typeof BaseAvatar.Image>) {
  return <BaseAvatar.Image className={cn('h-full w-full object-cover', className)} {...props} />;
}

export function AvatarFallback({ className, ...props }: ComponentPropsWithoutRef<typeof BaseAvatar.Fallback>) {
  return (
    <BaseAvatar.Fallback
      className={cn('inline-flex h-full w-full items-center justify-center font-medium', className)}
      {...props}
    />
  );
}
