import { useNavigate } from '@tanstack/react-router';
import { useForm } from '@tanstack/react-form';
import { Alert, AlertDescription } from '@components/ui/alert';
import { Button } from '@components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/card';
import { Input } from '@components/ui/input';
import { Label } from '@components/ui/label';
import { VALIDATION_DEBOUNCE_MS } from '@lib/constants';
import { changePasswordSchema } from './auth.schema';
import { changePasswordErrorMessage, useChangePassword } from './auth.queries';
import { FieldErrors } from './field-errors';

/** Forced step after signing in with a Temporary Password: choose your own, typed twice. */
export function ChangePasswordPage() {
  const navigate = useNavigate();
  const { changePassword, error, isPending } = useChangePassword();

  const form = useForm({
    defaultValues: { newPassword: '', confirmPassword: '' },
    validators: {
      onBlur: changePasswordSchema,
      onChangeAsyncDebounceMs: VALIDATION_DEBOUNCE_MS,
      onChangeAsync: changePasswordSchema,
      onSubmit: changePasswordSchema,
    },
    onSubmit: async ({ value }) => {
      try {
        await changePassword(value);
      } catch {
        return; // the failure is shown from the mutation error below
      }
      await navigate({ to: '/' });
    },
  });

  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Choose a new password</CardTitle>
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
            {error != null && (
              <Alert variant="danger">
                <AlertDescription>{changePasswordErrorMessage(error)}</AlertDescription>
              </Alert>
            )}

            <form.Field name="newPassword">
              {(field) => (
                <div className="flex flex-col gap-2">
                  <Label htmlFor={field.name}>New password</Label>
                  <Input
                    id={field.name}
                    name={field.name}
                    type="password"
                    autoComplete="new-password"
                    value={field.state.value}
                    onBlur={field.handleBlur}
                    onChange={(event) => field.handleChange(event.target.value)}
                  />
                  <FieldErrors errors={field.state.meta.errors} />
                </div>
              )}
            </form.Field>

            <form.Field name="confirmPassword">
              {(field) => (
                <div className="flex flex-col gap-2">
                  <Label htmlFor={field.name}>Confirm new password</Label>
                  <Input
                    id={field.name}
                    name={field.name}
                    type="password"
                    autoComplete="new-password"
                    value={field.state.value}
                    onBlur={field.handleBlur}
                    onChange={(event) => field.handleChange(event.target.value)}
                  />
                  <FieldErrors errors={field.state.meta.errors} />
                </div>
              )}
            </form.Field>

            <Button type="submit" disabled={isPending}>
              Change password
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
