import { cn } from '@/lib/utils';
import { type InputHTMLAttributes, forwardRef } from 'react';

export type InputProps = InputHTMLAttributes<HTMLInputElement>;

export const Input = forwardRef<HTMLInputElement, InputProps>(({ className, type, ...props }, ref) => (
  <input
    ref={ref}
    type={type}
    className={cn(
      'border-border bg-surface flex h-9 w-full rounded-md border px-3 py-1 text-sm shadow-sm',
      'placeholder:text-fg-subtle',
      'focus-visible:outline-accent focus-visible:outline-1 focus-visible:outline-offset-0',
      'disabled:cursor-not-allowed disabled:opacity-50',
      'file:border-0 file:bg-transparent file:text-sm file:font-medium',
      className,
    )}
    {...props}
  />
));
Input.displayName = 'Input';
