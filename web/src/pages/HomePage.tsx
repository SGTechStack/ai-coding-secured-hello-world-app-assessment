import { useQuery } from "@tanstack/react-query";
import * as authApi from "../api/auth.api";
import { Alert } from "../components/ui/Alert";
import { Card } from "../components/ui/Card";
import { Spinner } from "../components/ui/Spinner";
import { toMessage } from "../lib/errors";

/**
 * Story 5 — the personalized greeting.
 *
 * The greeting is fetched from the protected endpoint rather than assembled from the session the
 * frontend already holds. That is the whole point of the story: a string this page composed itself
 * would prove nothing, whereas one that came back from `/api/hello` proves the session actually
 * authenticates a request.
 */
export function HomePage() {
  const {
    data: greeting,
    isPending,
    error,
  } = useQuery({
    queryKey: ["greeting"],
    queryFn: authApi.getGreeting,
    retry: false,
  });

  return (
    <Card title="You are logged in">
      {isPending ? (
        <p className="flex items-center gap-2 text-sm text-ink-faint">
          <Spinner label="Loading your greeting" />
          Loading your greeting
        </p>
      ) : null}
      {error ? <Alert tone="error">{toMessage(error, "Could not load your greeting.")}</Alert> : null}
      {/* The greeting is the point of the page, so it gets the accent. At 7.5:1 on this background it
          is emphasis without becoming hard to read. */}
      {greeting ? <p className="text-2xl font-semibold text-accent">{greeting}</p> : null}
      <p className="mt-4 text-sm text-ink-muted">
        This greeting came from the API's protected endpoint, so the session cookie made it across
        origins and the server accepted it.
      </p>
    </Card>
  );
}
