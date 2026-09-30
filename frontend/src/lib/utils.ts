import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Resolves after `ms` milliseconds. */
export const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** Round to 1 significant figure (e.g. 0.08123 -> 0.08, 0.9734 -> 1). 0 stays 0. */
export function to1SigFig(value: number): number {
  if (value === 0) return 0;
  const factor = 10 ** Math.floor(Math.log10(Math.abs(value)));
  return Math.round(value / factor) * factor;
}
