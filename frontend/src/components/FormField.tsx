import { forwardRef, type InputHTMLAttributes } from 'react';

interface FormFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'onChange' | 'onBlur'> {
  id: string;
  label: string;
  error?: string | null;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onBlur?: () => void;
}

/**
 * Accessible form field: label + input + inline error.
 * Inline errors use aria-live="polite" (NOT role="alert") so they do not
 * conflict with the single form-level role="alert" banner.
 * ref is forwarded for programmatic focus (e.g. on submit error).
 */
export const FormField = forwardRef<HTMLInputElement, FormFieldProps>(
  function FormField({ id, label, error, onChange, onBlur, ...inputProps }, ref) {
    const errorId = `${id}-error`;
    return (
      <div className="form-group">
        <label htmlFor={id} className="form-label">
          {label}
        </label>
        <input
          id={id}
          ref={ref}
          className="form-input"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          onChange={onChange}
          onBlur={onBlur}
          {...inputProps}
        />
        {error && (
          <span id={errorId} className="form-field-error" aria-live="polite">
            {error}
          </span>
        )}
      </div>
    );
  },
);
