import { type ChangeEvent, useState } from 'react';

export interface FieldState {
  value: string;
  error: string | null;
  touched: boolean;
  onChange: (e: ChangeEvent<HTMLInputElement>) => void;
  onBlur: () => void;
  /** Marks field touched, runs validation, returns the error (or null). Used on submit. */
  touch: () => string | null;
}

/**
 * Field-level validation state.
 *
 * Behaviour:
 *  - Error only shown after the field is touched (first blur or form submit).
 *  - Once touched, error updates live as the user types.
 *  - `touch()` is called imperatively on form submit to mark all fields at once.
 */
export function useField(validate?: (value: string) => string | null): FieldState {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);

  const runValidate = (v: string): string | null => validate?.(v) ?? null;

  const onChange = (e: ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value;
    setValue(v);
    // Only show errors live if the field has already been blurred.
    if (touched) setError(runValidate(v));
  };

  const onBlur = () => {
    setTouched(true);
    setError(runValidate(value));
  };

  const touch = (): string | null => {
    setTouched(true);
    const err = runValidate(value);
    setError(err);
    return err;
  };

  return { value, error, touched, onChange, onBlur, touch };
}
