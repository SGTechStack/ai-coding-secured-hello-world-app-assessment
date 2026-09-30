/**
 * Reads a text field out of a `FormData`.
 *
 * `FormData.get` is typed `string | File | null`, so `String(...)` on it can produce the literal text
 * `[object Object]` when the field happens to be a file input. None of this application's forms have a
 * file input, but the narrowing is real rather than a cast: it makes the "not a string" case explicit
 * instead of stringifying whatever turned up.
 */
export function textField(form: FormData, name: string): string {
  const value = form.get(name)
  return typeof value === 'string' ? value : ''
}
