import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { DemoCodeHint } from '@/components/DemoCodeHint'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { noteSentCode } from '@/lib/auth/demoAccounts'

const schema = z.object({
  code: z.string().regex(/^\d{6}$/, 'Enter the 6-digit code from your authenticator app.'),
})

type CodeForm = z.infer<typeof schema>

/** The copy for a code the server refused (`INVALID_FACTOR`). */
export const INVALID_CODE = 'That code was not accepted. Check the code in your authenticator app and try again.'

/** The copy for a throttled code route (`TOO_MANY_REQUESTS`). */
export const TOO_MANY_ATTEMPTS = 'Too many attempts. Wait a moment, then try again.'

interface TotpCodeFormProps {
  /** The submit button's label. */
  submitLabel: string
  /**
   * Sends a well-formed code. Resolves with a message to show on the field (a refused code), or `undefined` when the
   * caller handled the outcome itself. Nothing is sent until the button is pressed: no auto-submit (ADR-027).
   */
  onCode: (code: string) => Promise<string | undefined>
  /** Focus the field when the form appears, as on the challenge page. */
  autoFocus?: boolean
  /** Hold the submit button disabled, as while a factor lock lasts. */
  disabled?: boolean
  /** Show the in-flight spinner on the submit button, which is then not actionable; it also shows while submitting. */
  showSpinner?: boolean
  /**
   * Offer the dev-only demo code hint ({@link DemoCodeHint}), for a prompt that checks the enrolled factor. Never set
   * where a new secret is confirmed: the hint's code is the enrolled one's.
   */
  demoCodeHint?: boolean
}

/**
 * A six-digit TOTP code field and its submit button, labelled, with its error linked by `aria-describedby` and
 * announced as an alert. The field is cleared on every submit, so a used code is never left to resend.
 */
export function TotpCodeForm({
  submitLabel,
  onCode,
  autoFocus = false,
  disabled = false,
  showSpinner = false,
  demoCodeHint = false,
}: TotpCodeFormProps) {
  const {
    register,
    handleSubmit,
    setError,
    resetField,
    setValue,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<CodeForm>({ resolver: zodResolver(schema), defaultValues: { code: '' } })

  const busy = showSpinner || isSubmitting

  const onSubmit = async ({ code }: CodeForm) => {
    resetField('code')
    noteSentCode(code)
    const refusal = await onCode(code)
    if (refusal) {
      setError('code', { message: refusal }, { shouldFocus: true })
    }
  }

  return (
    <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="totp-code">Code from the app</Label>
        <Input
          id="totp-code"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          autoFocus={autoFocus}
          aria-invalid={errors.code ? true : undefined}
          aria-describedby={errors.code ? 'totp-code-error' : undefined}
          {...register('code')}
        />
        {errors.code && (
          <p id="totp-code-error" role="alert" className="text-sm text-destructive">
            {errors.code.message}
          </p>
        )}
      </div>
      {demoCodeHint && (
        <DemoCodeHint
          onFill={(code) => {
            setValue('code', code, { shouldValidate: true })
            setFocus('code')
          }}
        />
      )}
      <div>
        <Button type="submit" disabled={disabled || busy} aria-busy={busy}>
          {busy && <Spinner />}
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}

/** The in-flight indicator: decorative, since the button's `aria-busy` carries the state. */
function Spinner() {
  return (
    <svg data-testid="spinner" aria-hidden="true" viewBox="0 0 24 24" className="animate-spin" fill="none">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="3" strokeOpacity="0.25" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}
