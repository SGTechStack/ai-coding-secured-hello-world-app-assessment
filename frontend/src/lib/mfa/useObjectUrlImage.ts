import { type RefObject, useEffect, useRef } from 'react'

/**
 * Shows `blob` in the returned image ref through a `blob:` object URL (ADR-025). One effect owns both halves: it
 * creates the URL in its body and revokes it in its cleanup, keyed on the `Blob`, so every create has exactly one
 * revoke, across a StrictMode remount, a replaced blob, unmount and a cleared blob alike. The cleanup takes the URL
 * off the image before revoking it, so the image never points at a revoked URL.
 */
export function useObjectUrlImage(blob: Blob | undefined): RefObject<HTMLImageElement | null> {
  const image = useRef<HTMLImageElement>(null)

  useEffect(() => {
    const element = image.current
    if (!blob || !element) {
      return undefined
    }
    const url = URL.createObjectURL(blob)
    element.src = url
    return () => {
      element.removeAttribute('src')
      URL.revokeObjectURL(url)
    }
  }, [blob])

  return image
}
