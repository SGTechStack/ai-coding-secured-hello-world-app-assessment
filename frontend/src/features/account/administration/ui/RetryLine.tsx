/** A generic (non-409) failure of a row action: the row is unchanged and the action can be retried. */
export function RetryLine({ block, onRetry }: Readonly<{ block: boolean; onRetry: () => void }>) {
  return (
    <span className={`flex items-center gap-2 text-xs text-danger ${block ? '' : 'justify-end'}`}>
      <span role="alert">Something went wrong.</span>
      <button type="button" className="font-semibold underline underline-offset-2" onClick={onRetry}>
        Try again
      </button>
    </span>
  );
}
