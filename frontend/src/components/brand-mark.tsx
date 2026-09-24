import { Fingerprint } from 'lucide-react'

/**
 * The app's brand mark — a tinted fingerprint glyph plus wordmark, used at
 * the top of every card so the product reads as one designed thing rather
 * than six pages with a text label each.
 */
export function BrandMark() {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary ring-1 ring-primary/20">
        <Fingerprint className="size-5" strokeWidth={1.75} />
      </div>
      <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
        Hello World Auth
      </p>
    </div>
  )
}
