import { useRouter } from '@tanstack/react-router';
import { SearchX } from 'lucide-react';

import { Button } from '@components/ui/button';

export function NotFoundPage() {
  const router = useRouter();

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 p-8 text-center">
      <SearchX className="text-fg-muted size-12" />
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Page not found</h1>
        <p className="text-fg-muted max-w-md text-sm">
          The page you are looking for does not exist or may have been moved.
        </p>
      </div>
      <Button onClick={() => router.navigate({ to: '/' })}>Go home</Button>
    </div>
  );
}
