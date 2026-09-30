import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/forbidden')({
  component: ForbiddenPage,
});

function ForbiddenPage() {
  return (
    <div className="flex flex-1 items-center justify-center py-12">
      <p className="text-fg-muted">You don't have access to this page.</p>
    </div>
  );
}
