import { useNavigate } from '@tanstack/react-router';
import { useForm } from '@tanstack/react-form';
import { Alert, AlertDescription } from '@components/ui/alert';
import { Button } from '@components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/card';
import { Input } from '@components/ui/input';
import { Label } from '@components/ui/label';
import { RETURN_TO_KEY } from '@lib/auth';
import { VALIDATION_DEBOUNCE_MS } from '@lib/constants';
import { revalidateBlurError } from '@lib/form';
import { signInSchema } from './auth.schema';
import { FieldErrors } from './field-errors';
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
    },
    onSubmit: async ({ value }) => {
      let result;
      try {
        result = await signIn(value);
      } catch {
        return; // the failure is shown from the mutation error below
      }
      // A Temporary Password allows nothing but choosing a new one, so go there first.
      if (result.mustChangePassword) {
        await navigate({ to: '/change-password' });
        return;
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

            <form.Field name="username" listeners={{ onChange: revalidateBlurError }}>
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

            <form.Field name="password" listeners={{ onChange: revalidateBlurError }}>
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
