import { redirect } from 'react-router'
import { getCurrentUser, getGreeting } from './api.ts'

export async function protectedLoader() {
  const result = await getCurrentUser()
  if (result.status === 'anonymous') throw redirect('/login')
  return result.user
}

/** Pages for signed-out visitors (login, registration, password reset) send signed-in users home. */
export async function loginLoader() {
  const result = await getCurrentUser()
  if (result.status === 'authenticated') throw redirect('/')
  return null
}

export async function landingLoader() {
  const greeting = await getGreeting()
  if (greeting === null) throw redirect('/login')
  return { greeting }
}
