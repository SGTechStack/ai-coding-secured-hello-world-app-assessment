import { Outlet, useLocation } from 'react-router'
import { ForgeBackdrop } from '../shared/scene/ForgeBackdrop.tsx'

const AUTH_PATHS = new Set(['/login', '/register', '/forgot-password', '/reset-password'])

/**
 * Root layout. The backdrop stays mounted across navigation, so moving between the sign-in
 * pages and the signed-in pages glides the scene to a new layout instead of reloading it.
 */
export function AppShell() {
  const { pathname } = useLocation()
  return (
    <>
      <ForgeBackdrop mode={AUTH_PATHS.has(pathname) ? 'auth' : 'app'} />
      <Outlet />
    </>
  )
}
