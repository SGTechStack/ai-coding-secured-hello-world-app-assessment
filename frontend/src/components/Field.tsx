/** One message shown under a field, keyed by the rule or check that produced it. */
export type FieldError = { key: string; message: string }

/**
 * IM8 dp-8 sensitivity of the data a field takes, shown after its "Confidential" classification.
 * Passwords are Sensitive High; usernames and emails, which identify a person, are Sensitive Normal.
 */
export type Sensitivity = 'Non Sensitive' | 'Sensitive Normal' | 'Sensitive High'

type FieldProps = {
  name: string
  label: string
  sensitivity: Sensitivity
  type?: string
  autoComplete: string
  value: string
  errors?: FieldError[]
  onChange: (value: string) => void
}

/** One input with its visible data-classification and sensitivity label (IM8 dp-8) and any errors. */
export function Field({
  name,
  label,
  sensitivity,
  type = 'text',
  autoComplete,
  value,
  errors = [],
  onChange,
}: FieldProps) {
  const classificationId = `${name}-classification`
  const errorId = `${name}-errors`
  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      <input
        id={name}
        name={name}
        type={type}
        autoComplete={autoComplete}
        value={value}
        aria-invalid={errors.length > 0}
        aria-describedby={errors.length > 0 ? `${classificationId} ${errorId}` : classificationId}
        onChange={(event) => onChange(event.target.value)}
      />
      <span id={classificationId} className="classification">
        Confidential / {sensitivity}
      </span>
      {errors.length > 0 && (
        <ul id={errorId} className="field-errors">
          {errors.map(({ key, message }) => (
            <li key={key}>{message}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
