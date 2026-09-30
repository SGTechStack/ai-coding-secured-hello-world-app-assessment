import type { ReactNode } from 'react';
import { BrandMark } from './brand-mark';

type CenteredCardLayoutProps = {
  headingId: string;
  title: string;
  description: string;
  children: ReactNode;
  footer: ReactNode;
};

/** A full-screen page with the logo above one centred card: title, description, content and a footer line. */
export function CenteredCardLayout({
  headingId,
  title,
  description,
  children,
  footer,
}: Readonly<CenteredCardLayoutProps>) {
  return (
    <main className="flex min-h-screen justify-center bg-canvas px-4 py-10 text-ink sm:items-center sm:px-6">
      <section aria-labelledby={headingId} className="w-full max-w-md">
        <div className="mb-6 flex justify-center">
          <BrandMark />
        </div>
        <div className="rounded-card border border-line bg-surface p-6 shadow-sm sm:p-8">
          <h1 className="text-2xl font-semibold tracking-tight text-ink" id={headingId}>
            {title}
          </h1>
          <p className="mt-1.5 text-sm text-ink-muted">{description}</p>
          <div className="mt-6">{children}</div>
        </div>
        <p className="mt-4 text-center text-sm text-ink-muted">{footer}</p>
      </section>
    </main>
  );
}
