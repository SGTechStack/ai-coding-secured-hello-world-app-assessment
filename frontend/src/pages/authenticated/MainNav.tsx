import { Link } from '@tanstack/react-router';
import { House, Users } from 'lucide-react';
import { cn } from '../../common/lib/cn';
import { FOCUS_RING } from '../../common/ui/styles';

/**
 * Every destination of the protected application and the roles that see it. The server still enforces every rule.
 * ponytail: one row at every width; revisit as a sidebar (steering's ResponsiveNav) past about five items.
 */
const ITEMS = [
  { label: 'Home', to: '/home', Icon: House, roles: ['USER', 'ADMIN'] },
  { label: 'Users', to: '/admin/users', Icon: Users, roles: ['ADMIN'] },
] as const;

const ITEM = `relative inline-flex min-h-11 items-center gap-2 rounded-sm text-sm transition-colors duration-150
  ${FOCUS_RING}`;

/** The row under the logo bar. Hidden when the role leaves a single destination, so a User's header is unchanged. */
export function MainNav({ role }: Readonly<{ role: string | undefined }>) {
  const items = ITEMS.filter((item) => (item.roles as readonly string[]).includes(role ?? ''));
  if (items.length < 2) return null;
  return (
    <nav aria-label="Main" className="border-b border-line bg-surface px-4 sm:px-6">
      <ul className="flex gap-6">
        {items.map(({ label, to, Icon }) => (
          <li key={to}>
            <Link
              to={to}
              className={cn(ITEM, 'font-medium text-ink-muted hover:text-ink')}
              // The underline and weight mark the current item too, so colour is never the only signal.
              activeProps={{
                'aria-current': 'page',
                className: cn(
                  ITEM,
                  `font-semibold text-primary
            after:absolute after:inset-x-0 after:bottom-0 after:h-0.75 after:bg-primary`,
                ),
              }}
            >
              <Icon aria-hidden="true" className="size-4" />
              {label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
