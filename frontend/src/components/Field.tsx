/** One message shown under a field, keyed by the rule or check that produced it. */
export type FieldError = { key: string; message: string }

type FieldProps = {
  name: string
  label: string
  type?: string
  autoComplete: string
  value: string
  errors?: FieldError[]
  onChange: (value: string) => void
}

/** One input with its visible "Confidential" data-classification label and any errors. */
export function Field({ name, label, type = 'text', autoComplete, value, errors = [], onChange }: FieldProps) {
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
        Confidential
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
