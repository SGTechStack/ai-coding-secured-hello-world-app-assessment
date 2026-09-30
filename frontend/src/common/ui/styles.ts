import { cn } from '../lib/cn';

/** Class recipes shared by the common/ui primitives and by elements that must look like them (e.g. a router Link). */

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

export const FOCUS_RING = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus';

const BUTTON_BASE = `inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center gap-2 rounded-control px-4 py-2
  text-sm font-semibold transition-colors duration-150 ${FOCUS_RING}
  disabled:cursor-not-allowed disabled:opacity-60`;

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-ink-inverse shadow-sm hover:bg-primary-hover disabled:hover:bg-primary',
  secondary: 'border border-line-strong bg-surface text-ink hover:bg-surface-muted disabled:hover:bg-surface',
  ghost: 'text-ink-muted hover:bg-surface-muted hover:text-ink disabled:hover:bg-transparent',
  danger: 'bg-danger text-ink-inverse shadow-sm hover:bg-danger-hover disabled:hover:bg-danger',
};

export const buttonClasses = (variant: ButtonVariant = 'primary', block = false) =>
  cn(BUTTON_BASE, BUTTON_VARIANTS[variant], block && 'w-full');

/** Inline text link; 44px tall so it is a valid touch target. */
export const LINK_CLASSES = `inline-flex min-h-11 items-center rounded-sm font-semibold text-primary underline
  decoration-primary/30 underline-offset-4 transition-colors hover:decoration-primary ${FOCUS_RING}`;
