import type { ReactNode } from 'react'
import './AuthCard.css'

interface AuthCardProps {
  title: string
  description: string
  /** Links under the form, e.g. back to log in. */
  footer: ReactNode
  /** Notices and the form, in order: a notice directly precedes the form. */
  children: ReactNode
}

/** The glass card a signed-out page renders as its <main>, with the page's single h1. */
export function AuthCard({ title, description, footer, children }: AuthCardProps) {
  return (
    <main className="auth-card panel">
      <div className="auth-card__header">
        <h1>{title}</h1>
        <p className="muted">{description}</p>
      </div>
      {children}
      <div className="auth-card__links">{footer}</div>
    </main>
  )
}
