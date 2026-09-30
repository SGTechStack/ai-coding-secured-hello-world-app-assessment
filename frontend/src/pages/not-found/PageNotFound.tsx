import { Link } from '@tanstack/react-router';
import { BrandMark } from '../../common/ui/brand-mark';
import { cn } from '../../common/lib/cn';
import { buttonClasses } from '../../common/ui/styles';

/**
 * Any address that is not a page of the application (ADR 0007). The same for Visitors and Users: outside the
 * authenticated layout, with no API call. The link's guard sends a Visitor on to the login screen.
 */
export function PageNotFound() {
  return (
    <main className="flex min-h-screen justify-center bg-canvas px-4 py-10 text-ink sm:items-center sm:px-6">
      <section aria-labelledby="not-found-heading" className="w-full max-w-md text-center">
        <div className="mb-6 flex justify-center">
          <BrandMark />
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink" id="not-found-heading">
          Page not found
        </h1>
        <p className="mt-1.5 text-sm text-ink-muted">The address you opened is not a page of this application.</p>
        <Link className={cn(buttonClasses(), 'mt-6')} to="/home">
          Go to the home page
        </Link>
      </section>
    </main>
  );
}
