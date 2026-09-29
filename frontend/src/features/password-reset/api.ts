import { apiFetch, errorMessage } from '../../shared/api/http.ts'
import type { SubmissionResult } from '../../shared/navigation/useSubmitThenLogIn.ts'

export type ResetRequestResult = { status: 'accepted'; message: string } | { status: 'unavailable' }

function postJson(path: string, body: unknown): Promise<Response> {
  return apiFetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function requestPasswordReset(email: string): Promise<ResetRequestResult> {
  try {
    const response = await postJson('/auth/password-reset/request', { email })
    const message = response.status === 202 ? await errorMessage(response) : undefined
    return message ? { status: 'accepted', message } : { status: 'unavailable' }
  } catch {
    return { status: 'unavailable' }
  }
}

export async function confirmPasswordReset(
  token: string,
  newPassword: string,
): Promise<SubmissionResult> {
  try {
    const response = await postJson('/auth/password-reset/confirm', { token, newPassword })
    if (response.status === 204) return { status: 'done' }
    const message = response.status === 400 ? await errorMessage(response) : undefined
    return message ? { status: 'rejected', message } : { status: 'unavailable' }
  } catch {
    return { status: 'unavailable' }
  }
}
