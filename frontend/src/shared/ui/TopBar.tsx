import type { ReactNode } from 'react'
import { Brand } from './Brand.tsx'
import './TopBar.css'

/** Sticky glass header for signed-in pages; each page supplies its own actions. */
export function TopBar({ children }: { children: ReactNode }) {
  return (
    <header className="top-bar">
      <div className="top-bar__inner">
        <Brand />
        <div className="top-bar__actions">{children}</div>
      </div>
    </header>
  )
}
