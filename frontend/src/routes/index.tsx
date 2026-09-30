import { createFileRoute, redirect } from '@tanstack/react-router';

// The site root only points at the Home page. Not under _authenticated, so its guard runs once, on /home.
export const Route = createFileRoute('/')({
  beforeLoad: () => {
    throw redirect({ to: '/home', replace: true });
  },
});
