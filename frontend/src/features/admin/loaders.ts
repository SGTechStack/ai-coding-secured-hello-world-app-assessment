import { redirect } from 'react-router'
import { getCurrentUser } from '../auth/api.ts'
import { listUsers } from './api.ts'

/** Anonymous visitors go to /login and USERs to /; the server enforces the same rule on the API. */
export async function adminUsersLoader() {
  const result = await getCurrentUser()
  if (result.status === 'anonymous') throw redirect('/login')
  if (result.user.role !== 'ADMIN') throw redirect('/')
  return { currentUser: result.user, users: await listUsers() }
}
