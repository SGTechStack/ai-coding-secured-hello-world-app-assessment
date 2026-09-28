export function Spinner({ label }: { label: string }) {
  return (
    <span
      className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
      role="status"
      aria-label={label}
    />
  );
}

export function FullPageSpinner({ label }: { label: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center gap-3 bg-canvas text-accent">
      <Spinner label={label} />
      {/* Announced to screen readers, which get no benefit from a spinning border. */}
      <span className="sr-only">{label}</span>
    </div>
  );
}
