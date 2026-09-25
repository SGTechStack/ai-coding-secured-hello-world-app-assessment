import { useMutation } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { LoaderCircle } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
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
import { register, type Registration } from "../api/auth";
import { ApiError } from "../api/client";
import { bannerMessage } from "./bannerMessage";
import { PASSWORD_HINT, passwordProblem } from "./passwordRule";

/** The loading state is shown for at least this long so it never flickers. */
const MIN_SUBMITTING_MS = 400;

type Form = Registration & { confirmPassword: string };
type FieldName = keyof Form;
type FieldErrors = Partial<Record<FieldName, string>>;

/** Form order: the first invalid one of these gets focus. */
const FIELDS: readonly FieldName[] = [
  "username",
  "email",
  "firstName",
  "password",
  "confirmPassword",
];

const EMPTY: Form = {
  username: "",
  email: "",
  firstName: "",
  password: "",
  confirmPassword: "",
};

// The same rules the API enforces, so most mistakes are caught before a round trip. The API
// remains the authority, e.g. for the common-password list and taken usernames.
const USERNAME = /^[A-Za-z0-9._-]{3,50}$/;
const EMAIL = /^[^\s@]+@[^\s@]+$/;

/** Submit-time validation, one message per field. */
function validate(form: Form): FieldErrors {
  const errors: FieldErrors = {};
  if (!form.username) errors.username = "Username is required";
  else if (!USERNAME.test(form.username))
    errors.username =
      "Username must be 3 to 50 letters, digits, dots, hyphens or underscores.";

  const email = form.email.trim();
  if (!email) errors.email = "Email is required";
  else if (!EMAIL.test(email) || email.length > 254)
    errors.email = "Enter a valid email address.";

  const firstName = form.firstName.trim();
  if (!firstName) errors.firstName = "First name is required";
  else if (firstName.length > 100)
    errors.firstName = "First name must be 1 to 100 characters.";

  const password = passwordProblem(form.password);
  if (password) errors.password = password;

  if (!form.confirmPassword)
    errors.confirmPassword = "Please confirm your password";
  else if (form.confirmPassword !== form.password)
    errors.confirmPassword = "Passwords do not match";
  return errors;
}

/**
 * Self-registration. Errors are validated on submit (never while typing), shown under their
 * field, and announced by moving focus to the first invalid field, which reads out its message
 * (linked through aria-describedby). Server `fieldErrors` (a taken username or email, a common
 * password) land on the same fields. Success goes to the login page with a fixed notice; the API
 * creates no session.
 */
export function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const registration = useMutation({
    mutationFn: ({ confirmPassword: _, ...submitted }: Form) =>
      settleNoSoonerThan(MIN_SUBMITTING_MS, register(submitted)),
    onSuccess: () =>
      navigate({ to: "/login", search: { notice: "registered" } }),
    onError: (error) => {
      const serverErrors = fieldErrorsFrom(error);
      setFieldErrors(serverErrors);
      focusFirstError();
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      focusFirstError();
      return;
    }
    registration.mutate({
      ...form,
      email: form.email.trim(),
      firstName: form.firstName.trim(),
    });
  }

  /** Typing in a field clears that field's inline error and any banner. */
  function handleChange(field: FieldName, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setFieldErrors(({ [field]: _, ...rest }) => rest);
    registration.reset();
  }

  const submitting = registration.isPending;
  const focusFirstError = useFocusFirstError(fieldErrors, submitting);
  // A failure that named no field of this form still needs saying.
  const banner =
    registration.isError && Object.keys(fieldErrors).length === 0
      ? bannerMessage(registration.error, { showRejections: true })
      : undefined;

  function field(
    name: FieldName,
    label: string,
    props: { type?: string; autoComplete: string },
  ) {
    return (
      <FormField
        name={name}
        label={label}
        {...props}
        value={form[name]}
        error={fieldErrors[name]}
        disabled={submitting}
        onChange={(value) => handleChange(name, value)}
      />
    );
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <h1 className="text-2xl font-semibold tracking-tight">
          Create an account
        </h1>
        <CardDescription>
          Choose a username and {PASSWORD_HINT}.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {banner && <ErrorAlert>{banner}</ErrorAlert>}
        <form
          className="flex flex-col gap-5"
          onSubmit={handleSubmit}
          noValidate
        >
          {field("username", "Username", { autoComplete: "username" })}
          {field("email", "Email", { type: "email", autoComplete: "email" })}
          {field("firstName", "First name", { autoComplete: "given-name" })}
          {field("password", "Password", {
            type: "password",
            autoComplete: "new-password",
          })}
          {field("confirmPassword", "Confirm password", {
            type: "password",
            autoComplete: "new-password",
          })}
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? (
              <>
                <LoaderCircle
                  className="animate-spin motion-reduce:animate-none"
                  aria-hidden
                />
                <span>Creating account...</span>
              </>
            ) : (
              "Create account"
            )}
          </Button>
        </form>
        <p className="text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link
            to="/login"
            className="font-medium text-foreground underline underline-offset-4"
          >
            Log in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}

/** The API's field errors for fields on this form; the first message per field wins. */
function fieldErrorsFrom(error: Error): FieldErrors {
  const errors: FieldErrors = {};
  if (!(error instanceof ApiError)) return errors;
  for (const { field, message } of error.fieldErrors) {
    const known = FIELDS.find((name) => name === field);
    if (known && !errors[known]) errors[known] = message;
  }
  return errors;
}

/**
 * Returns a function that, once `errors` are on screen and the form is enabled again (`busy` is
 * false), focuses the first invalid field in form order, so a screen reader announces its label
 * and error message.
 */
function useFocusFirstError(errors: FieldErrors, busy: boolean) {
  const pending = useRef(false);
  useEffect(() => {
    if (!pending.current || busy) return;
    pending.current = false;
    const first = FIELDS.find((name) => errors[name]);
    if (first) document.getElementById(first)?.focus();
  }, [errors, busy]);
  return () => {
    pending.current = true;
  };
}
