import { Outlet } from 'react-router'
import { Brand } from '../shared/ui/Brand.tsx'
import './AuthLayout.css'

/**
 * Split layout for the signed-out pages: brand story on the left over the 3D gem, the page's
 * card on the right. The tagline is a paragraph so each page keeps a single h1.
 */
export function AuthLayout() {
  return (
    <div className="auth-layout">
      <header className="auth-hero">
        <Brand />
        <div className="auth-hero__copy rise-in">
          <p className="eyebrow">Accounts, forged properly</p>
          <p className="auth-hero__title">
            Secure access,
            <br />
            <span className="text-molten">hammered into shape.</span>
          </p>
          <p className="auth-hero__lede">
            Sign in, recover your password and manage your team from one place.
          </p>
        </div>
      </header>
      <div className="auth-panel">
        <Outlet />
      </div>
    </div>
  )
}
