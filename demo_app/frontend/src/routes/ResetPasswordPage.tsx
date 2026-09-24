import { useMutation } from "@tanstack/react-query";
import {
  Link,
  useLocation,
  useNavigate,
  useRouter,
} from "@tanstack/react-router";
import { LoaderCircle } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { ErrorAlert } from "@/components/ErrorAlert";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from "@/components/ui/card";
import { settleNoSoonerThan } from "@/lib/timing";
import { confirmPasswordReset } from "../api/auth";
import { ApiError } from "../api/client";

/** The loading state is shown for at least this long so it never flickers. */
const MIN_SUBMITTING_MS = 400;

export const INVALID_LINK = "This reset link is invalid or has expired.";
const UNAVAILABLE = "Unable to connect to the server. Please try again later.";

type Form = { newPassword: string; confirmPassword: string };
type FieldName = keyof Form;
type FieldErrors = Partial<Record<FieldName, string>>;

/** Submit-time validation; the API remains the authority (e.g. for the common-password list). */
function validate({ newPassword, confirmPassword }: Form): FieldErrors {
  const errors: FieldErrors = {};
  // Characters, not UTF-16 units, as the API counts them.
  const length = [...newPassword].length;
  if (!newPassword) errors.newPassword = "Password is required";
  else if (length < 12 || length > 64)
    errors.newPassword = "Password must be 12 to 64 characters.";
  if (!confirmPassword) errors.confirmPassword = "Please confirm your password";
  else if (confirmPassword !== newPassword)
    errors.confirmPassword = "Passwords do not match";
  return errors;
}

/** The `token` in a `#token=...` fragment (with or without the `#`), if any. */
function tokenFrom(hash: string): string | undefined {
  return new URLSearchParams(hash.replace(/^#/, "")).get("token") || undefined;
}

/**
 * Sets a new password from an emailed link (`/reset-password#token=...`). The token is read from
 * the URL fragment once, on mount, and kept in component state only: the fragment is removed from
 * the address bar straight away (a `replaceState` through the router's history), so it doesn't
 * linger in history, and it was never sent to a server or in a `Referer`. A used, expired or
 * unknown link shows the API's message with a way to request a new one. Success lands on the login
 * page with a fixed notice.
 */
export function ResetPasswordPage() {
  const navigate = useNavigate();
  const router = useRouter();
  const { hash, pathname } = useLocation();
  // Read once: the fragment is gone after the effect below.
  const [token] = useState(() => tokenFrom(hash));
  const [form, setForm] = useState<Form>({
    newPassword: "",
    confirmPassword: "",
  });
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [invalidLink, setInvalidLink] = useState(token === undefined);

  useEffect(() => {
    if (hash) router.history.replace(pathname);
  }, [hash, pathname, router]);

  const confirmation = useMutation({
    mutationFn: (newPassword: string) =>
      settleNoSoonerThan(
        MIN_SUBMITTING_MS,
        confirmPasswordReset(token ?? "", newPassword),
      ),
    onSuccess: () =>
      navigate({ to: "/login", search: { notice: "password-updated" } }),
    onError: (error) => {
      if (!(error instanceof ApiError)) return;
      if (error.code === "INVALID_RESET_TOKEN") {
        setInvalidLink(true);
        return;
      }
      const problem = error.fieldErrors.find(
        ({ field }) => field === "newPassword",
      );
      if (problem) {
        setFieldErrors({ newPassword: problem.message });
        focus("newPassword");
      }
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validate(form);
    setFieldErrors(errors);
    const first = (["newPassword", "confirmPassword"] as const).find(
      (name) => errors[name],
    );
    if (first) {
      focus(first);
      return;
    }
    confirmation.mutate(form.newPassword);
  }

  function handleChange(field: FieldName, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setFieldErrors(({ [field]: _, ...rest }) => rest);
    confirmation.reset();
  }

  const submitting = confirmation.isPending;
  const banner =
    confirmation.isError && Object.keys(fieldErrors).length === 0
      ? bannerMessage(confirmation.error)
      : undefined;

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <h1 className="text-2xl font-semibold tracking-tight">
          Set a new password
        </h1>
        <CardDescription>
          Choose a password of at least 12 characters.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {invalidLink ? (
          <>
            <ErrorAlert>{INVALID_LINK}</ErrorAlert>
            <p className="text-center text-sm">
              <Link
                to="/forgot-password"
                className="font-medium text-foreground underline underline-offset-4"
              >
                Request a new reset link
              </Link>
            </p>
          </>
        ) : (
          <>
            {banner && <ErrorAlert>{banner}</ErrorAlert>}
            <form
              className="flex flex-col gap-5"
              onSubmit={handleSubmit}
              noValidate
            >
              <FormField
                name="newPassword"
                label="New password"
                type="password"
                autoComplete="new-password"
                value={form.newPassword}
                error={fieldErrors.newPassword}
                disabled={submitting}
                onChange={(value) => handleChange("newPassword", value)}
              />
              <FormField
                name="confirmPassword"
                label="Confirm new password"
                type="password"
                autoComplete="new-password"
                value={form.confirmPassword}
                error={fieldErrors.confirmPassword}
                disabled={submitting}
                onChange={(value) => handleChange("confirmPassword", value)}
              />
              <Button type="submit" className="w-full" disabled={submitting}>
                {submitting ? (
                  <>
                    <LoaderCircle
                      className="animate-spin motion-reduce:animate-none"
                      aria-hidden
                    />
                    <span>Updating password...</span>
                  </>
                ) : (
                  "Update password"
                )}
              </Button>
            </form>
          </>
        )}
      </CardContent>
    </Card>
  );
}

/** Moves focus to a field once it is enabled again, so its error is announced. */
function focus(field: FieldName) {
  setTimeout(() => document.getElementById(field)?.focus());
}

/** For failures without a field to point at. */
function bannerMessage(error: Error): string {
  if (error instanceof ApiError && error.kind === "rejected")
    return error.message;
  if (error instanceof ApiError && error.kind === "throttled")
    return "Too many attempts. Please try again later.";
  return UNAVAILABLE;
}
