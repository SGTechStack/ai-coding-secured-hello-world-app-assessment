import type { ReactNode } from 'react';
import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-react';
import { cn } from '../lib/cn';

type AlertTone = 'danger' | 'success' | 'warning' | 'info';

const TONES: Record<AlertTone, { classes: string; Icon: typeof Info }> = {
  danger: { classes: 'border-danger-line bg-danger-soft text-danger', Icon: CircleAlert },
  success: { classes: 'border-success-line bg-success-soft text-success', Icon: CircleCheck },
  warning: { classes: 'border-warning-line bg-warning-soft text-warning', Icon: TriangleAlert },
  info: { classes: 'border-info-line bg-info-soft text-info', Icon: Info },
};

type AlertProps = {
  tone: AlertTone;
  children: ReactNode;
  /** `alert` interrupts (errors); `status` is polite (confirmations). Defaults by tone. */
  role?: 'alert' | 'status';
  className?: string;
};

/** Page- or form-level message banner. Colour is never the only signal: every tone has its own icon. */
export function Alert({
  tone,
  children,
  role = tone === 'danger' ? 'alert' : 'status',
  className,
}: Readonly<AlertProps>) {
  const { classes, Icon } = TONES[tone];
  return (
    <div
      className={cn('flex items-start gap-3 rounded-control border px-4 py-3 text-sm font-medium', classes, className)}
      role={role}
    >
      <Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
      <p>{children}</p>
    </div>
  );
}
