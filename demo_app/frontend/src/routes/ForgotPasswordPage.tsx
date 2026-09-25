import { useMutation } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { CircleCheck, LoaderCircle } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ErrorAlert } from "@/components/ErrorAlert";
import { FormField } from "@/components/FormField";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from "@/components/ui/card";
import { settleNoSoonerThan } from "@/lib/timing";
import { requestPasswordReset } from "../api/auth";
import { ApiError } from "../api/client";
import { UNAVAILABLE } from "./bannerMessage";

/** The loading state is shown for at least this long so it never flickers. */
const MIN_SUBMITTING_MS = 400;

export const RESET_REQUESTED =
  "If an account exists for that email, we've sent a reset link.";

/**
 * Asks for a reset link. Whatever the API answers (the empty `202`, or any error status, a `429`
 * included) the page shows the same generic confirmation, so it never reveals whether the email
 * has an account. Only a request that got no answer at all says it couldn't connect.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string>();
  const reset = useMutation({
    mutationFn: (submitted: string) =>
      settleNoSoonerThan(MIN_SUBMITTING_MS, requestPasswordReset(submitted)),
  });
  const answered =
    reset.isSuccess ||
    (reset.error instanceof ApiError && reset.error.status !== undefined);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = email.trim();
    if (!trimmed) {
      setError("Email is required");
      document.getElementById("email")?.focus();
      return;
    }
    reset.mutate(trimmed);
  }

  const submitting = reset.isPending;

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <h1 className="text-2xl font-semibold tracking-tight">
          Forgot password
        </h1>
        <CardDescription>
          Enter your account's email and we'll send you a reset link.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {answered ? (
          <Alert role="status">
            <CircleCheck />
            <AlertDescription>{RESET_REQUESTED}</AlertDescription>
          </Alert>
        ) : (
          <>
            {reset.isError && <ErrorAlert>{UNAVAILABLE}</ErrorAlert>}
            <form
              className="flex flex-col gap-5"
              onSubmit={handleSubmit}
              noValidate
            >
              <FormField
                name="email"
                label="Email"
                type="email"
                autoComplete="email"
                value={email}
                error={error}
                disabled={submitting}
                onChange={(value) => {
                  setEmail(value);
                  setError(undefined);
                  reset.reset();
                }}
              />
              <Button type="submit" className="w-full" disabled={submitting}>
                {submitting ? (
                  <>
                    <LoaderCircle
                      className="animate-spin motion-reduce:animate-none"
                      aria-hidden
                    />
                    <span>Sending link...</span>
                  </>
                ) : (
                  "Send reset link"
                )}
              </Button>
            </form>
          </>
        )}
        <p className="text-center text-sm text-muted-foreground">
          <Link
            to="/login"
            className="font-medium text-foreground underline underline-offset-4"
          >
            Back to log in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
