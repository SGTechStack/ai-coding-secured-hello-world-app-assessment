import type { ReactNode } from "react";
import { Logo } from "./Logo";

export function Card({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="w-full rounded-lg border border-edge bg-panel p-6">
      {title ? (
        <h1 className="mb-4 text-lg font-semibold text-ink">
          {title}
          {/* A short accent rule under the heading: the one piece of pure decoration here, and it is
              decoration rather than information, so it is hidden from assistive technology. */}
          <span aria-hidden="true" className="mt-2 block h-0.5 w-10 rounded-full bg-accent" />
        </h1>
      ) : null}
      {children}
    </section>
  );
}

/**
 * The narrow single-column frame the auth pages share.
 *
 * The logo lives here rather than in each page, so login, registration and both password-reset screens
 * are branded identically by construction. Someone arriving from an emailed reset link should see the
 * same thing as someone who came to log in — a page that looks different is a page people hesitate to
 * type a password into.
 */
export function CenteredPage({ children }: { children: ReactNode }) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 bg-canvas p-4">
      <Logo variant="hero" />
      <div className="w-full max-w-md">{children}</div>
    </main>
  );
}
