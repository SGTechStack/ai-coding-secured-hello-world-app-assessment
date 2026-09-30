import { AlertCircle } from 'lucide-react';

import { Button } from '@components/ui/button';

interface ErrorPageProps {
  reset: () => void;
}

export function ErrorPage({ reset }: ErrorPageProps) {
  const handleGoHome = () => {
    window.location.href = '/';
  };

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 p-8 text-center">
      <AlertCircle className="text-danger size-12" />
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Something went wrong</h1>
        <p className="text-fg-muted max-w-md text-sm">
          An unexpected error occurred. Please try again or return to the home page.
        </p>
      </div>
      <div className="flex gap-3">
        <Button variant="outline" onClick={handleGoHome}>
          Go home
        </Button>
        <Button onClick={reset}>Try again</Button>
      </div>
    </div>
  );
}
