'use client';

import { cn } from '@/lib/utils';
import { Toast as BaseToast } from '@base-ui/react/toast';
import { AlertCircle, CheckCircle, Info, X } from 'lucide-react';
import type { ReactNode } from 'react';

// Singleton manager — import `toast` and call toast.add(), toast.promise(), etc.
export const toast = BaseToast.createToastManager();

const typeIcons: Record<string, ReactNode> = {
  success: <CheckCircle className="text-success h-4 w-4" />,
  error: <AlertCircle className="text-danger h-4 w-4" />,
  info: <Info className="text-info h-4 w-4" />,
};

function ToastViewportInner() {
  const { toasts } = BaseToast.useToastManager();
  return (
    <BaseToast.Viewport
      className={cn(
        'fixed right-4 bottom-4 z-50 flex max-h-screen w-full max-w-sm flex-col-reverse gap-2',
        'sm:right-4 sm:bottom-4',
      )}
    >
      {toasts.map((t) => (
        <BaseToast.Root
          key={t.id}
          toast={t}
          className={cn(
            'border-border relative flex w-full items-start gap-3 overflow-hidden rounded-lg border',
            'bg-surface-elevated p-4 shadow-lg',
            'data-[starting-style]:translate-y-2 data-[starting-style]:opacity-0',
            'data-[ending-style]:translate-y-2 data-[ending-style]:opacity-0',
            'transition-all duration-200',
          )}
        >
          {t.type && typeIcons[t.type] && <div className="mt-0.5 shrink-0">{typeIcons[t.type]}</div>}
          <div className="flex flex-1 flex-col gap-0.5">
            {t.title && <BaseToast.Title className="text-fg text-sm font-semibold" />}
            {t.description && <BaseToast.Description className="text-fg-muted text-sm" />}
          </div>
          <BaseToast.Close
            className={cn(
              'text-fg-muted mt-0.5 shrink-0 rounded-sm opacity-70 transition-opacity',
              'focus-visible:outline-accent hover:opacity-100 focus-visible:outline-1 focus-visible:outline-offset-0',
            )}
            aria-label="Dismiss"
          >
            <X className="h-4 w-4" />
          </BaseToast.Close>
        </BaseToast.Root>
      ))}
    </BaseToast.Viewport>
  );
}

export function ToastProvider({ children, timeout = 5000 }: { children: ReactNode; timeout?: number }) {
  return (
    <BaseToast.Provider toastManager={toast} timeout={timeout}>
      {children}
      <ToastViewportInner />
    </BaseToast.Provider>
  );
}
