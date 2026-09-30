/** Distinct validation messages for one form field. */
export function FieldErrors({ errors }: { errors: ReadonlyArray<{ message: string } | undefined> }) {
  const messages = [...new Set(errors.map((error) => error?.message).filter(Boolean))];
  return messages.map((message) => (
    <p key={message} className="text-danger text-sm">
      {message}
    </p>
  ));
}
