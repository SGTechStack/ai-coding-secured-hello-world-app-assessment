'use client';

import { cn } from '@/lib/utils';
import { Tabs as BaseTabs } from '@base-ui/react/tabs';
import type { ComponentPropsWithoutRef } from 'react';

export const Tabs = BaseTabs.Root;

export function TabsList({ className, ...props }: ComponentPropsWithoutRef<typeof BaseTabs.List>) {
  return (
    <BaseTabs.List
      className={cn(
        'bg-bg-muted text-fg-muted relative inline-flex h-9 items-center justify-center rounded-md p-1',
        className,
      )}
      {...props}
    />
  );
}

export function TabsTrigger({ className, ...props }: ComponentPropsWithoutRef<typeof BaseTabs.Tab>) {
  return (
    <BaseTabs.Tab
      className={cn(
        'inline-flex items-center justify-center rounded-sm px-3 py-1 text-sm font-medium whitespace-nowrap transition-all',
        'text-fg-muted hover:text-fg',
        'focus-visible:outline-accent focus-visible:outline-1 focus-visible:outline-offset-0',
        'data-[active]:bg-surface data-[active]:text-accent data-[active]:shadow-sm',
        'data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
        className,
      )}
      {...props}
    />
  );
}

export function TabsContent({ className, ...props }: ComponentPropsWithoutRef<typeof BaseTabs.Panel>) {
  return (
    <BaseTabs.Panel
      className={cn(
        'mt-2',
        'focus-visible:outline-accent focus-visible:outline-1 focus-visible:outline-offset-0',
        className,
      )}
      {...props}
    />
  );
}
