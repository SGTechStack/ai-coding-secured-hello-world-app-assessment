/**
 * Blocks shaped like the protected frame (header bar, page heading), so the page replaces them without a jump. Busy
 * while a reloaded protected page asks the server whether its Session is still live; otherwise silent for assistive
 * technology, behind a dialog that owns the announcement.
 */
export function SessionPlaceholder({ busy }: Readonly<{ busy: boolean }>) {
  return (
    <div
      className="flex min-h-screen flex-col bg-canvas"
      {...(busy
        ? { role: 'status', 'aria-busy': true, 'aria-label': 'Restoring your session' }
        : { 'aria-hidden': true })}
    >
      <div className="flex items-center justify-between gap-3 border-b border-line bg-surface px-4 py-2 sm:px-6">
        <div className="h-11 w-32 rounded-control bg-line motion-safe:animate-pulse" />
        <div className="h-11 w-24 rounded-control bg-line motion-safe:animate-pulse" />
      </div>
      <div className="grid flex-1 place-items-center p-4">
        <div className="h-8 w-48 rounded-control bg-line motion-safe:animate-pulse" />
      </div>
    </div>
  );
}
