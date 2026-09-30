import type { ReactNode } from 'react'

/** The two banners. Error text comes from `errorText.ts`, which is not a component and so lives apart. */

export function ErrorBanner({ message }: { message: string | null }): ReactNode {
  if (!message) {
    return null
  }
  return (
    <p className="banner banner-error" role="alert">
      {message}
    </p>
  )
}

export function NoticeBanner({ message }: { message: string | null }): ReactNode {
  if (!message) {
    return null
  }
  return (
    <p className="banner banner-notice" role="status">
      {message}
    </p>
  )
}
