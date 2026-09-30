import { cn } from '@/lib/utils';
import { Input as BaseInput } from '@base-ui/react/input';
import { type TextareaHTMLAttributes, forwardRef } from 'react';

export type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement>;

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(({ className, ...props }, ref) => (
  <BaseInput
    render={<textarea ref={ref} {...props} />}
    className={cn(
      'border-border bg-surface flex min-h-[80px] w-full rounded-md border px-3 py-2 text-sm shadow-sm',
      'placeholder:text-fg-subtle',
      'focus-visible:outline-accent focus-visible:outline-1 focus-visible:outline-offset-0',
      'disabled:cursor-not-allowed disabled:opacity-50',
      className,
    )}
  />
));
Textarea.displayName = 'Textarea';
