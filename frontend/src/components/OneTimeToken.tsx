import { useId, useState } from 'react'
import { Button } from '@/components/ui/button'

/**
 * Shows a single-use token once (R-FE-007): the token and the link that carries it, with a copy affordance. The token
 * lives only in the caller's component state; nothing here logs, stores or reports it, and the clipboard is the one
 * place it goes. Leaving the page drops it, and the server never returns it again.
 */
export function OneTimeToken({ heading, token, link }: { heading: string; token: string; link: string }) {
  const headingId = useId()
  const [copied, setCopied] = useState('')

  const onCopy = async () => {
    try {
      await navigator.clipboard.writeText(link)
      setCopied('Link copied.')
    } catch {
      setCopied('The link could not be copied. Select it and copy it by hand.')
    }
  }

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2 rounded-md border p-4">
      <h3 id={headingId} className="font-semibold">
        {heading}
      </h3>
      <p className="text-sm">
        This is shown once and will not be shown again. Copy it now and pass it to the user yourself.
      </p>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
        <dt>Link</dt>
        <dd>
          <code className="break-all">{link}</code>
        </dd>
        <dt>Token</dt>
        <dd>
          <code className="break-all">{token}</code>
        </dd>
      </dl>
      <div>
        <Button type="button" variant="outline" onClick={onCopy}>
          Copy link
        </Button>
      </div>
      <p role="status" className="text-sm">
        {copied}
      </p>
    </section>
  )
}
