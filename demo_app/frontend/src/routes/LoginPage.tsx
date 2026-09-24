import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { LoaderCircle } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ErrorAlert } from "@/components/ErrorAlert";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from "@/components/ui/card";
import { login, meQueryOptions, type Credentials } from "../api/auth";
import { ApiError } from "../api/client";

/** The loading state is shown for at least this long so it never flickers. */
const MIN_SUBMITTING_MS = 400;

const UNAVAILABLE = "Unable to connect to the server. Please try again later.";

type FieldName = keyof Credentials;
type FieldErrors = Partial<Record<FieldName, string>>;

const NO_CREDENTIALS: Credentials = { username: "", password: "" };

/** Submit-time validation: a blank (or whitespace-only) field is an error. */
function validate({ username, password }: Credentials): FieldErrors {
  return {
    ...(username.trim() ? {} : { username: "Username is required" }),
    ...(password.trim() ? {} : { password: "Password is required" }),
  };
}

export function LoginPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [credentials, setCredentials] = useState(NO_CREDENTIALS);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const loginMutation = useMutation({
    mutationFn: (submitted: Credentials) =>
      settleNoSoonerThan(MIN_SUBMITTING_MS, login(submitted)),
    onSuccess: (profile) => {
      queryClient.setQueryData(meQueryOptions.queryKey, profile);
      return navigate({ to: "/" });
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validate(credentials);
    setFieldErrors(errors);
    if (errors.username || errors.password) return;
    loginMutation.mutate(credentials);
  }

  /** Typing in a field clears that field's inline error and any banner. */
  function handleChange(field: FieldName, value: string) {
    setCredentials((current) => ({ ...current, [field]: value }));
    setFieldErrors(({ [field]: _, ...rest }) => rest);
    loginMutation.reset();
  }

  const submitting = loginMutation.isPending;

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <h1 className="text-2xl font-semibold tracking-tight">Log in</h1>
        <CardDescription>
          Enter your username and password to continue.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {/* The banner sits above the form, outside the <form> element. */}
        {loginMutation.isError && (
          <ErrorAlert>{bannerMessage(loginMutation.error)}</ErrorAlert>
        )}
        <form
          className="flex flex-col gap-5"
          onSubmit={handleSubmit}
          noValidate
        >
          <FormField
            name="username"
            label="Username"
            autoComplete="username"
            value={credentials.username}
            error={fieldErrors.username}
            disabled={submitting}
            onChange={(value) => handleChange("username", value)}
          />
          <FormField
            name="password"
            label="Password"
            type="password"
            autoComplete="current-password"
            value={credentials.password}
            error={fieldErrors.password}
            disabled={submitting}
            onChange={(value) => handleChange("password", value)}
          />
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? (
              <>
                <LoaderCircle
                  className="animate-spin motion-reduce:animate-none"
                  aria-hidden
                />
                <span>
                  Logging in
                  <span className="ellipsis">
                    <span>.</span>
                    <span>.</span>
                    <span>.</span>
                  </span>
                </span>
              </>
            ) : (
              "Log in"
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

/**
 * Never says which credential was wrong: a 401 gets one generic message. A 429 means this network
 * is throttled, not that the password is wrong. Anything else that still fails (after the CSRF
 * retry on a 403), a 5xx or a network error, is "can't connect".
 */
function bannerMessage(error: Error): string {
  if (!(error instanceof ApiError)) return UNAVAILABLE;
  switch (error.kind) {
    case "unauthorized":
      return "Invalid username or password";
    case "throttled":
      return "Too many attempts. Please try again later.";
    default:
      return UNAVAILABLE;
  }
}

/** Settles like `promise`, but no sooner than `ms` after this call. */
async function settleNoSoonerThan<T>(ms: number, promise: Promise<T>) {
  const minimum = new Promise((resolve) => setTimeout(resolve, ms));
  try {
    return await promise;
  } finally {
    await minimum;
  }
}
