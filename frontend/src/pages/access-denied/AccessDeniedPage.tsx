import { Link } from '@tanstack/react-router';
import { ShieldAlert } from 'lucide-react';
import { cn } from '../../common/lib/cn';
import { buttonClasses } from '../../common/ui/styles';

/**
 * A page that exists but that the caller's role may not open (ADR 0007's role guard). Inside the authenticated
 * layout, mirroring Page not found. It never ends the Session.
 */
export function AccessDeniedPage() {
  return (
    <main className="flex flex-1 justify-center px-4 py-10 sm:items-center sm:px-6">
      <section aria-labelledby="access-denied-heading" className="w-full max-w-md text-center">
        <div className="mx-auto mb-6 grid size-12 place-items-center rounded-full bg-warning-soft text-warning">
          <ShieldAlert aria-hidden="true" className="size-6" />
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink" id="access-denied-heading">
          Access denied
        </h1>
        <p className="mt-1.5 text-sm text-ink-muted">Your account can't open this page. You're still signed in.</p>
        <Link className={cn(buttonClasses(), 'mt-6')} to="/home">
          Go to the home page
        </Link>
      </section>
    </main>
  );
}
