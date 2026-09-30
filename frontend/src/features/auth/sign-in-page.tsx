import { useNavigate } from '@tanstack/react-router';
import { useForm } from '@tanstack/react-form';
import { Alert, AlertDescription } from '@components/ui/alert';
import { Button } from '@components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/card';
import { Input } from '@components/ui/input';
import { Label } from '@components/ui/label';
import { RETURN_TO_KEY } from '@lib/auth';
import { VALIDATION_DEBOUNCE_MS } from '@lib/constants';
import { signInSchema } from './auth.schema';
import { signInErrorMessage, useSignIn } from './auth.queries';

export function SignInPage() {
  const navigate = useNavigate();
  const { signIn, error, isPending } = useSignIn();

  const form = useForm({
    defaultValues: { username: '', password: '' },
    validators: {
      onBlur: signInSchema,
      onChangeAsyncDebounceMs: VALIDATION_DEBOUNCE_MS,
      onChangeAsync: signInSchema,
      onSubmit: signInSchema,
    },
    onSubmit: async ({ value }) => {
      try {
        await signIn(value);
      } catch {
        return; // the failure is shown from the mutation error below
      }
      // Go back to where a 401 sent the visitor away from, if anywhere; a saved place is used once.
      const returnTo = sessionStorage.getItem(RETURN_TO_KEY);
      sessionStorage.removeItem(RETURN_TO_KEY);
      await navigate({ to: returnTo ?? '/' });
    },
  });

  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Sign in</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            className="flex flex-col gap-4"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void form.handleSubmit();
            }}
          >
            {error && (
              <Alert variant="danger">
                <AlertDescription>{signInErrorMessage(error)}</AlertDescription>
              </Alert>
            )}

            <form.Field name="username">
              {(field) => (
                <div className="flex flex-col gap-2">
                  <Label htmlFor={field.name}>Username</Label>
                  <Input
                    id={field.name}
                    name={field.name}
                    autoComplete="username"
                    value={field.state.value}
                    onBlur={field.handleBlur}
                    onChange={(event) => field.handleChange(event.target.value)}
                  />
                  <FieldErrors errors={field.state.meta.errors} />
                </div>
              )}
            </form.Field>

            <form.Field name="password">
              {(field) => (
                <div className="flex flex-col gap-2">
                  <Label htmlFor={field.name}>Password</Label>
                  <Input
                    id={field.name}
                    name={field.name}
                    type="password"
                    autoComplete="current-password"
                    value={field.state.value}
                    onBlur={field.handleBlur}
                    onChange={(event) => field.handleChange(event.target.value)}
                  />
                  <FieldErrors errors={field.state.meta.errors} />
                </div>
              )}
            </form.Field>

            <Button type="submit" disabled={isPending}>
              Sign in
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function FieldErrors({ errors }: { errors: ReadonlyArray<{ message: string } | undefined> }) {
  const messages = [...new Set(errors.map((error) => error?.message).filter(Boolean))];
  return messages.map((message) => (
    <p key={message} className="text-danger text-sm">
      {message}
    </p>
  ));
}
