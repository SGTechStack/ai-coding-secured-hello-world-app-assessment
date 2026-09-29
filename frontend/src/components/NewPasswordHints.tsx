import { useEffect, useState } from 'react'
import { MAX_BYTES, utf8Bytes } from '@/lib/password/policy'
import { estimateStrength, type StrengthScore } from '@/lib/password/strength'

const STRENGTH_LABELS: Readonly<Record<StrengthScore, string>> = {
  0: 'Very weak',
  1: 'Weak',
  2: 'Fair',
  3: 'Strong',
  4: 'Very strong',
}

interface NewPasswordHintsProps {
  /** The element id prefix; the byte count is `${id}-bytes`, for the field's `aria-describedby`. */
  id: string
  password: string
  /** Words the estimate should penalise, such as the username (the server's context terms are authoritative). */
  userInputs: readonly string[]
}

/**
 * The byte count and the indicative strength meter under a new-password field. The server runs the whole policy and
 * decides (ADR-005); the byte count is what the server counts, after NFC (ADR-003).
 */
export function NewPasswordHints({ id, password, userInputs }: NewPasswordHintsProps) {
  const [estimate, setEstimate] = useState<{ password: string; score: StrengthScore }>()
  const inputs = userInputs.join('\n')

  useEffect(() => {
    if (!password) {
      return
    }
    let current = true
    void estimateStrength(password, inputs ? inputs.split('\n') : []).then((score) => {
      if (current) {
        setEstimate({ password, score })
      }
    })
    return () => {
      current = false
    }
  }, [password, inputs])

  const score = password && estimate?.password === password ? estimate.score : undefined

  return (
    <>
      <p id={`${id}-bytes`} className="text-sm text-muted-foreground">
        {utf8Bytes(password)} of {MAX_BYTES} bytes
      </p>
      <div className="flex items-center gap-2 text-sm">
        <label htmlFor={`${id}-strength`}>Strength (indicative)</label>
        <meter id={`${id}-strength`} min={0} max={4} low={2} high={3} optimum={4} value={score ?? 0} />
        <span aria-live="polite">{score === undefined ? '' : STRENGTH_LABELS[score]}</span>
      </div>
    </>
  )
}
