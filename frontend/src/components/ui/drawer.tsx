import * as React from 'react';
import { Drawer as DrawerPrimitive } from '@base-ui/react/drawer';

import { cn } from '@lib/utils';
import { XIcon } from 'lucide-react';

function Drawer({ ...props }: DrawerPrimitive.Root.Props) {
  return <DrawerPrimitive.Root data-slot="drawer" {...props} />;
}

function DrawerTrigger({ ...props }: DrawerPrimitive.Trigger.Props) {
  return <DrawerPrimitive.Trigger data-slot="drawer-trigger" {...props} />;
}

function DrawerPortal({ ...props }: DrawerPrimitive.Portal.Props) {
  return <DrawerPrimitive.Portal data-slot="drawer-portal" {...props} />;
}

function DrawerClose({ ...props }: DrawerPrimitive.Close.Props) {
  return <DrawerPrimitive.Close data-slot="drawer-close" {...props} />;
}

function DrawerPopup({
  className,
  children,
  showCloseButton = true,
  ...props
}: DrawerPrimitive.Popup.Props & { showCloseButton?: boolean }) {
  return (
    <DrawerPortal>
      <DrawerPrimitive.Popup
        data-slot="drawer-popup"
        className={cn(
          'bg-surface-elevated fixed top-0 right-0 z-50 flex h-full w-full flex-col border-l shadow-lg outline-none',
          'data-open:animate-in data-open:slide-in-from-right data-closed:animate-out data-closed:slide-out-to-right duration-200',
          'sm:w-96 md:w-[28rem] lg:w-[32rem] xl:w-[36rem]',
          className,
        )}
        {...props}
      >
        {children}
        {showCloseButton && (
          <DrawerPrimitive.Close
            data-slot="drawer-close"
            className={cn(
              'text-fg-muted absolute top-4 right-4 rounded-sm opacity-70 transition-opacity',
              'focus-visible:outline-accent hover:opacity-100 focus-visible:outline-1 focus-visible:outline-offset-0',
            )}
            aria-label="Close"
          >
            <XIcon className="h-4 w-4" />
          </DrawerPrimitive.Close>
        )}
      </DrawerPrimitive.Popup>
    </DrawerPortal>
  );
}

function DrawerHeader({ className, ...props }: React.ComponentProps<'div'>) {
  return (
    <div
      data-slot="drawer-header"
      className={cn('flex items-center justify-between border-b p-4', className)}
      {...props}
    />
  );
}

function DrawerTitle({ className, ...props }: DrawerPrimitive.Title.Props) {
  return (
    <DrawerPrimitive.Title
      data-slot="drawer-title"
      className={cn('text-base leading-none font-semibold', className)}
      {...props}
    />
  );
}

function DrawerContent({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="drawer-content" className={cn('flex-1 overflow-y-auto p-4', className)} {...props} />;
}

export { Drawer, DrawerClose, DrawerContent, DrawerHeader, DrawerPopup, DrawerPortal, DrawerTitle, DrawerTrigger };
