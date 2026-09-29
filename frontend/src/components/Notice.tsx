export function Notice({ message, error = false }: { message: string; error?: boolean }) {
  if (!message) return null;
  return (
    <div className={`notice ${error ? 'notice-error' : ''}`} role={error ? 'alert' : 'status'}>
      {message}
    </div>
  );
}
