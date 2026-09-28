import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Spinner } from "./Spinner";

type Variant = "primary" | "secondary" | "danger";

/**
 * Note the primary variant's text colour: near-black on orange, not white.
 *
 * White on this orange is roughly 2.8:1, which is unreadable for anyone with reduced contrast
 * sensitivity and fails WCAG outright. Flipping it to dark ink gets 7.5:1 from the same two colours.
 */
const variantStyles: Record<Variant, string> = {
  primary: "bg-accent text-accent-ink hover:bg-accent-hover",
  secondary: "border border-edge-strong bg-panel text-ink hover:bg-panel-hover hover:border-accent",
  danger: "border border-danger/50 bg-transparent text-danger hover:bg-danger-soft",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  busy?: boolean;
  busyLabel?: string;
  children: ReactNode;
}

export function Button({
  variant = "primary",
  busy = false,
  busyLabel = "Working",
  children,
  className = "",
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      // A busy button stays disabled so a slow network cannot turn one click into three requests.
      disabled={disabled || busy}
      aria-busy={busy}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-50 ${variantStyles[variant]} ${className}`}
      {...rest}
    >
      {busy ? <Spinner label={busyLabel} /> : null}
      {children}
    </button>
  );
}
