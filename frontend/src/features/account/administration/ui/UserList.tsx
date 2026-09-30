import { ChevronDown, ChevronLeft, ChevronRight, ChevronUp } from 'lucide-react';
import { Fragment, useRef, useState, type Ref } from 'react';
import { cn } from '../../../../common/lib/cn';
import { Button } from '../../../../common/ui/button';
import { roleLabel, type ListedAccount, type UserListPage } from '../model/user-list';
import { AccountDetails, Email, Field, SgtTime, StatusPill } from './cells';
import { DeleteAccount } from './DeleteAccount';
import { RoleChange } from './RoleChange';
import { StatusToggle } from './StatusToggle';

type UserListProps = {
  /** The page to show (the last one loaded). */
  shown: UserListPage;
  /** The caller's own Account is marked "(you)". */
  callerId: string;
  /** The requested page is loading: the shown rows are dimmed and the list is marked busy. */
  busy: boolean;
  /** @param page 1-based */
  onPageChange: (page: number) => void;
  /** Applies an Account whose status or role just changed to the shown page, updating its row in place. */
  onToggled: (account: ListedAccount) => void;
  /** Receives the list's region, so the page can move focus to it after a successful "Try again". */
  ref?: Ref<HTMLElement>;
};

const CARD = 'rounded-card border border-line bg-surface shadow-sm';
const TH = 'px-4 py-3 text-left text-xs font-medium text-ink-muted';
const TD = 'px-4 py-3 align-middle text-sm text-ink';

/**
 * The User list card: a table from `md` up, one card per Account below it, and the pagination footer. Row details
 * may be open on several rows at once and close when the page changes.
 */
export function UserList({ shown, callerId, busy, onPageChange, onToggled, ref }: Readonly<UserListProps>) {
  const { content, page } = shown;
  const [open, setOpen] = useState<{ page: number; ids: ReadonlySet<string> }>({ page: page.number, ids: new Set() });
  const openIds = open.page === page.number ? open.ids : new Set<string>();
  const toggle = (id: string) => {
    const ids = new Set(openIds);
    if (!ids.delete(id)) ids.add(id);
    setOpen({ page: page.number, ids });
  };
  const top = useRef<HTMLDivElement>(null);
  // A Role change updates its row silently, so it is announced here, once for the table and the cards together.
  const [announcement, setAnnouncement] = useState('');
  const onRoleChanged = (account: ListedAccount) => {
    onToggled(account);
    setAnnouncement(`${account.username} is now ${roleLabel(account.role)}. They have been signed out.`);
  };
  const go = (next: number) => {
    onPageChange(next);
    // Keep focus on the pressed button; only bring the list's top back into view when it has scrolled away.
    if ((top.current?.getBoundingClientRect().top ?? 0) < 0) top.current?.scrollIntoView?.({ block: 'start' });
  };

  // Paging steps from the page on screen, so the label and the buttons never disagree, even after a failed change.
  const current = page.number + 1;

  return (
    <section ref={ref} tabIndex={-1} aria-label="Users" className={cn(CARD, 'outline-none')}>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <div ref={top} aria-busy={busy} className={cn('transition-opacity', busy && 'opacity-60')}>
        <div className="hidden md:block">
          <table className="w-full table-fixed border-collapse">
            <caption className="sr-only">
              Users, page {current} of {page.totalPages}
            </caption>
            <thead className="bg-surface-muted">
              <tr className="border-b border-line">
                <th scope="col" className={cn(TH, 'w-[16%] rounded-tl-card')}>
                  Username
                </th>
                <th scope="col" className={cn(TH, 'w-[22%]')}>
                  Email
                </th>
                <th scope="col" className={TH}>
                  Role
                </th>
                <th scope="col" className={TH}>
                  Status
                </th>
                <th scope="col" className={TH}>
                  Created (SGT)
                </th>
                <th scope="col" className={TH}>
                  Last login (SGT)
                </th>
                <th scope="col" className={cn(TH, 'w-52')}>
                  Actions
                </th>
                <th scope="col" className={cn(TH, 'w-24 rounded-tr-card')}>
                  Details
                </th>
              </tr>
            </thead>
            <tbody>
              {content.map((account) => {
                const expanded = openIds.has(account.id);
                const detailsId = `user-${account.id}-row-details`;
                return (
                  <Fragment key={account.id}>
                    <tr className={cn('border-b border-line last:border-b-0', expanded && 'bg-primary-soft')}>
                      <td className={cn(TD, 'break-words font-medium')}>
                        <Username account={account} callerId={callerId} />
                      </td>
                      <td className={TD}>
                        <Email value={account.email} />
                      </td>
                      <td className={cn(TD, 'py-1.5')}>
                        <RoleChange account={account} isSelf={account.id === callerId} onChanged={onRoleChanged} />
                      </td>
                      <td className={TD}>
                        <StatusPill account={account} />
                      </td>
                      <td className={TD}>
                        <SgtTime value={account.createdAt} />
                      </td>
                      <td className={TD}>
                        <SgtTime value={account.lastLoginAt} missing="Never" />
                      </td>
                      <td className={cn(TD, 'py-1.5')}>
                        <div className="flex flex-wrap items-start justify-end gap-2">
                          <StatusToggle account={account} isSelf={account.id === callerId} onToggled={onToggled} />
                          <DeleteAccount account={account} isSelf={account.id === callerId} />
                        </div>
                      </td>
                      <td className={cn(TD, 'py-1.5')}>
                        <Button
                          variant="ghost"
                          className="size-11 px-0"
                          aria-expanded={expanded}
                          aria-controls={detailsId}
                          aria-label={`${expanded ? 'Hide' : 'Show'} details for ${account.username}`}
                          onClick={() => toggle(account.id)}
                        >
                          {expanded ? (
                            <ChevronUp aria-hidden="true" className="size-5" />
                          ) : (
                            <ChevronDown aria-hidden="true" className="size-5" />
                          )}
                        </Button>
                      </td>
                    </tr>
                    <tr id={detailsId} hidden={!expanded} className="border-b border-line bg-surface-muted">
                      <td colSpan={8} className="px-4 py-4">
                        <AccountDetails account={account} className="grid-cols-3" />
                      </td>
                    </tr>
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
        <ul className="flex flex-col gap-3 p-3 md:hidden">
          {content.map((account) => {
            const expanded = openIds.has(account.id);
            const detailsId = `user-${account.id}-card-details`;
            return (
              <li key={account.id} className={cn(CARD, 'p-4')}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h2 className="break-all text-base font-semibold text-ink">
                    <Username account={account} callerId={callerId} />
                  </h2>
                  <StatusPill account={account} />
                </div>
                <dl className="mt-3 flex flex-col gap-3 text-sm">
                  <Field label="Email">
                    <Email value={account.email} />
                  </Field>
                  <Field label="Role">
                    <RoleChange account={account} isSelf={account.id === callerId} onChanged={onRoleChanged} />
                  </Field>
                  <Field label="Created (SGT)">
                    <SgtTime value={account.createdAt} />
                  </Field>
                  <Field label="Last login (SGT)">
                    <SgtTime value={account.lastLoginAt} missing="Never" />
                  </Field>
                </dl>
                <div id={detailsId} hidden={!expanded} className="mt-3 border-t border-line pt-3">
                  <AccountDetails account={account} className="grid-cols-1" />
                </div>
                <div className="mt-3 flex flex-col gap-3">
                  <StatusToggle account={account} isSelf={account.id === callerId} onToggled={onToggled} block />
                  <DeleteAccount account={account} isSelf={account.id === callerId} block />
                </div>
                <Button
                  variant="secondary"
                  block
                  className="mt-3"
                  aria-expanded={expanded}
                  aria-controls={detailsId}
                  aria-label={`${expanded ? 'Hide' : 'Show'} details for ${account.username}`}
                  onClick={() => toggle(account.id)}
                >
                  {expanded ? 'Hide details' : 'Show details'}
                </Button>
              </li>
            );
          })}
        </ul>
      </div>
      <nav
        aria-label="Pagination"
        className="flex flex-col gap-3 border-t border-line px-4 py-3 md:flex-row md:items-center md:justify-between"
      >
        <p aria-live="polite" className="order-first text-center text-sm text-ink-muted md:order-none">
          Page <strong className="font-semibold text-ink">{current}</strong> of {page.totalPages}
          {' · '}
          {page.totalElements} {page.totalElements === 1 ? 'user' : 'users'}
        </p>
        <div className="grid grid-cols-2 gap-3 md:contents">
          <Button
            variant="secondary"
            className="md:order-first"
            disabled={current <= 1}
            onClick={() => go(current - 1)}
          >
            <ChevronLeft aria-hidden="true" className="size-4" />
            Previous
          </Button>
          <Button variant="secondary" disabled={current >= page.totalPages} onClick={() => go(current + 1)}>
            Next
            <ChevronRight aria-hidden="true" className="size-4" />
          </Button>
        </div>
      </nav>
    </section>
  );
}

function Username({ account, callerId }: Readonly<{ account: ListedAccount; callerId: string }>) {
  return (
    <>
      {account.username}
      {account.id === callerId && (
        <span className="ml-1.5 whitespace-nowrap text-xs font-normal text-ink-muted">(you)</span>
      )}
    </>
  );
}

/** Holds the list's place while the first page loads: a header strip and five rows, so nothing shifts. */
export function UserListSkeleton() {
  return (
    <div role="status" aria-busy="true" aria-label="Loading users" className={cn(CARD, 'overflow-hidden')}>
      <div className="h-11 border-b border-line bg-surface-muted" />
      {Array.from({ length: 5 }, (_, row) => (
        <div key={row} className="flex h-14 items-center gap-4 border-b border-line px-4 last:border-b-0">
          <div className="h-4 w-1/5 rounded-control bg-line" />
          <div className="hidden h-4 w-1/4 rounded-control bg-line md:block" />
          <div className="h-5 w-16 rounded-full bg-line" />
          <div className="hidden h-4 w-1/6 rounded-control bg-line md:block" />
        </div>
      ))}
    </div>
  );
}
