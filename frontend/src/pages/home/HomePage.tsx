import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { isAuthenticationRequired } from '../../common/http/api-client';
import { Button } from '../../common/ui/button';
import { Dialog } from '../../common/ui/dialog';
import { readSession } from '../../features/auth/session';
import { useGreeting } from '../../features/account/greeting';

const HEADING = 'text-2xl font-semibold tracking-tight text-ink';

/**
 * The protected landing page: the server's Greeting as the heading, proving the Session is live. Any other failure
 * opens an error dialog; once dismissed, a "Welcome" heading keeps an inline "Try again". A 401 is handled by the
 * global Session-expired reaction, never here: the placeholder stays up while it navigates to /login.
 */
export function HomePage() {
  const greeting = useGreeting(readSession(useQueryClient())?.id);
  // Failures (counted by TanStack) the User has already answered; a newer failure reopens the dialog.
  const [answeredFailures, setAnsweredFailures] = useState(0);
  const inlineRetry = useRef<HTMLButtonElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  // Set by "Try again": the button that had focus disappears while the retry loads, so focus would fall to the body.
  const [retried, setRetried] = useState(false);

  const failed = greeting.isError && !isAuthenticationRequired(greeting.error);
  const dialogOpen = failed && greeting.errorUpdateCount > answeredFailures;
  const retry = () => {
    setAnsweredFailures(greeting.errorUpdateCount);
    setRetried(true);
    void greeting.refetch();
  };
  // A failed retry reopens the dialog, which takes focus itself; a successful one hands focus to the Greeting.
  useEffect(() => {
    if (retried && greeting.isSuccess) heading.current?.focus();
  }, [retried, greeting.isSuccess]);
  // Native return-focus would land on the body, so move focus to the inline retry once the dialog has closed.
  const close = () => {
    flushSync(() => {
      setAnsweredFailures(greeting.errorUpdateCount);
    });
    inlineRetry.current?.focus();
  };

  let content: ReactNode;
  if (greeting.isSuccess) {
    content = (
      // tabIndex -1: focusable from script only, so a successful retry can move focus here; never a Tab stop.
      <h1 ref={heading} tabIndex={-1} className={`${HEADING} outline-none`}>
        {greeting.data.message}
      </h1>
    );
  } else if (failed) {
    content = (
      <div className="flex flex-col items-center gap-3">
        <h1 className={HEADING}>Welcome</h1>
        {!dialogOpen && (
          <Button ref={inlineRetry} variant="secondary" onClick={retry}>
            Try again
          </Button>
        )}
      </div>
    );
  } else {
    content = (
      // Loading, and every retry (TanStack puts a query with no data back to pending). h-8 is text-2xl's line
      // height, so the heading replaces the placeholder without a layout shift.
      <div
        role="status"
        aria-busy="true"
        aria-label="Loading your greeting"
        className="h-8 w-48 rounded-control bg-line"
      />
    );
  }

  return (
    <main className="grid flex-1 place-items-center p-4">
      {content}
      <Dialog
        open={dialogOpen}
        title="Unable to load your greeting"
        description="Unable to load your greeting. Please try again."
        onClose={close}
      >
        <Button onClick={retry}>Try again</Button>
        <Button variant="secondary" onClick={close}>
          Close
        </Button>
      </Dialog>
    </main>
  );
}
