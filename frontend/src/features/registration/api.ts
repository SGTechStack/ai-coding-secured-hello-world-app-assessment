import { apiFetch, errorMessage } from '../../shared/api/http.ts'
import type { SubmissionResult } from '../../shared/navigation/useSubmitThenLogIn.ts'

export async function register(
  username: string,
  email: string,
  password: string,
): Promise<SubmissionResult> {
  try {
    const response = await apiFetch('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email, password }),
    })
    if (response.status === 201) return { status: 'done' }
    if (response.status === 400 || response.status === 409) {
      const message = await errorMessage(response)
      if (message) return { status: 'rejected', message }
    }
    return { status: 'unavailable' }
  } catch {
    return { status: 'unavailable' }
  }
}
