import type { ComponentProps } from 'react';
import { cn } from '../lib/cn';
import { buttonClasses, type ButtonVariant } from './styles';

type ButtonProps = ComponentProps<'button'> & { variant?: ButtonVariant; block?: boolean };

/** The one button primitive. Always 44x44px or larger. Defaults to `type="button"` so it never submits by accident. */
export function Button({ variant = 'primary', block = false, className, type = 'button', ...props }: ButtonProps) {
  return <button className={cn(buttonClasses(variant, block), className)} type={type} {...props} />;
}
