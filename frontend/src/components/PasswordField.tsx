import { useState } from 'react';

interface PasswordFieldProps {
  id: string;
  label: string;
  value: string;
  error?: string | null;
  autoComplete?: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onBlur?: () => void;
  /** When provided, renders a requirements checklist. */
  requirements?: Array<{ label: string; met: boolean }>;
}

/** Eye icon (password visible). */
function EyeIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

/** Eye-off icon (password hidden). */
function EyeOffIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  );
}

/**
 * Password input with visibility toggle and optional requirements checklist.
 *
 * The toggle button uses a visually-hidden span for its accessible name so that
 * `getByLabelText(/password/i)` in tests only matches the <input>, not the button.
 */
export function PasswordField({
  id, label, value, error, autoComplete, onChange, onBlur, requirements,
}: PasswordFieldProps) {
  const [show, setShow] = useState(false);
  const errorId = `${id}-error`;

  return (
    <div className="form-group">
      <label htmlFor={id} className="form-label">{label}</label>
      <div className="password-input-wrapper">
        <input
          id={id}
          type={show ? 'text' : 'password'}
          value={value}
          autoComplete={autoComplete}
          className="form-input"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          onChange={onChange}
          onBlur={onBlur}
        />
        <button
          type="button"
          className="password-toggle-btn"
          onClick={() => setShow((s) => !s)}
          tabIndex={0}
        >
          {/* Visible icon; accessible name from sr-only span only — no aria-label
              that contains "password", which would interfere with getByLabelText */}
          <span className="sr-only">{show ? 'Hide' : 'Show'}</span>
          {show ? <EyeOffIcon /> : <EyeIcon />}
        </button>
      </div>
      {error && (
        <span id={errorId} className="form-field-error" aria-live="polite">
          {error}
        </span>
      )}
      {requirements && requirements.length > 0 && (
        <ul className="password-requirements" aria-label="Password requirements">
          {requirements.map((req) => (
            <li
              key={req.label}
              className={`password-requirement ${req.met ? 'password-requirement--met' : 'password-requirement--unmet'}`}
            >
              <span className="password-requirement-icon" aria-hidden="true">
                {req.met ? '✓' : '○'}
              </span>
              {req.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
