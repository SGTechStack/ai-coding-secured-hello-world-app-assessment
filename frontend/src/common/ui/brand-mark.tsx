import { ShieldCheck } from 'lucide-react';

/** Product logo + name. Decorative text only, never a heading. */
export function BrandMark() {
  return (
    <div className="flex items-center gap-2.5">
      <span className="grid size-9 place-items-center rounded-control bg-primary text-ink-inverse">
        <ShieldCheck aria-hidden="true" className="size-5" />
      </span>
      <span className="text-lg font-semibold tracking-tight text-ink">Builder Day</span>
    </div>
  );
}
