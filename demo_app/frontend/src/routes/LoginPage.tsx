import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { CircleCheck, LoaderCircle } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
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
import { login, meQueryOptions, type Credentials } from "../api/auth";
import { bannerMessage } from "./bannerMessage";
import { loginNoticeText } from "./loginNotice";

/** The loading state is shown for at least this long so it never flickers. */
const MIN_SUBMITTING_MS = 400;

/**
 * Never says which credential was wrong: every 401 gets this one message. Any other failure that
 * survives the CSRF retry is "can't connect" (see `bannerMessage`).
 */
const INVALID_CREDENTIALS = "Invalid username or password";

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
  const notice = useOneShotNotice();
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
    notice.dismiss();
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
        {notice.text && (
          <Alert role="status">
            <CircleCheck />
            <AlertDescription>{notice.text}</AlertDescription>
          </Alert>
        )}
        {/* The banner sits above the form, outside the <form> element. */}
        {loginMutation.isError && (
          <ErrorAlert>
            {bannerMessage(loginMutation.error, {
              unauthorized: INVALID_CREDENTIALS,
            })}
          </ErrorAlert>
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
        <p className="text-center text-sm">
          <Link
            to="/forgot-password"
            className="font-medium text-foreground underline underline-offset-4"
          >
            Forgot password?
          </Link>
        </p>
        <p className="text-center text-sm text-muted-foreground">
          New here?{" "}
          <Link
            to="/register"
            className="font-medium text-foreground underline underline-offset-4"
          >
            Create an account
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}

/**
 * The notice the page was opened with (e.g. "Account created. Please log in."), shown until the
 * user submits. The search param is removed from the URL straight away, so a reload or a shared
 * link doesn't show it again.
 */
function useOneShotNotice() {
  const navigate = useNavigate();
  const { notice } = useSearch({ from: "/login" });
  const [text, setText] = useState(notice && loginNoticeText(notice));

  useEffect(() => {
    if (notice) void navigate({ to: "/login", search: {}, replace: true });
  }, [notice, navigate]);

  return { text, dismiss: () => setText(undefined) };
}
