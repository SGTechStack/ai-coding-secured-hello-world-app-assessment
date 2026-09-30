import { useState } from 'react';
import { useForm } from '@tanstack/react-form';
import { Alert, AlertDescription, AlertTitle } from '@components/ui/alert';
import { Button } from '@components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/card';
import { Input } from '@components/ui/input';
import { Label } from '@components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/table';
import { VALIDATION_DEBOUNCE_MS } from '@lib/constants';
import { formatDate, formatTimestamp } from '@lib/locale';
import { createAccountSchema } from './accounts.schema';
import {
  ACCOUNT_ROLES,
  createAccountErrorMessage,
  resetPasswordErrorMessage,
  useAccounts,
  useCreateAccount,
  useResetPassword,
  type AccountSummary,
  type CreatedAccount,
} from './accounts.queries';

export function AccountsPage() {
  const [created, setCreated] = useState<CreatedAccount | undefined>();

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 p-6">
      <CreateAccountCard created={created} onCreated={setCreated} />
      <AccountList />
    </div>
  );
}

function CreateAccountCard({
  created,
  onCreated,
}: {
  created: CreatedAccount | undefined;
  onCreated: (account: CreatedAccount | undefined) => void;
}) {
  const { createAccount, error, isPending, reset } = useCreateAccount();

  const form = useForm({
    defaultValues: { username: '', role: ACCOUNT_ROLES[0] as (typeof ACCOUNT_ROLES)[number] },
    validators: {
      onBlur: createAccountSchema,
      onChangeAsyncDebounceMs: VALIDATION_DEBOUNCE_MS,
      onChangeAsync: createAccountSchema,
      onSubmit: createAccountSchema,
    },
    onSubmit: async ({ value }) => {
      onCreated(undefined);
      try {
        onCreated(await createAccount({ username: value.username.trim(), role: value.role }));
      } catch {
        return; // the failure is shown from the mutation error below
      }
      reset();
      form.reset();
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Create account</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {created && (
          <Alert variant="success">
            <AlertTitle>Account created for {created.username}</AlertTitle>
            <AlertDescription>
              <p>
                Temporary password: <code className="font-mono font-semibold">{created.temporaryPassword}</code>
              </p>
              <p>
                It is shown only once. Copy it now and hand it to {created.username}; it expires{' '}
                {formatTimestamp(created.temporaryPasswordExpiresAt)}.
              </p>
            </AlertDescription>
          </Alert>
        )}

        {error != null && (
          <Alert variant="danger">
            <AlertDescription>{createAccountErrorMessage(error)}</AlertDescription>
          </Alert>
        )}

        <form
          className="flex flex-col gap-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
        >
          <form.Field name="username">
            {(field) => (
              <div className="flex flex-col gap-2">
                <Label htmlFor={field.name}>Username</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  autoComplete="off"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                />
                <FieldErrors errors={field.state.meta.errors} />
              </div>
            )}
          </form.Field>

          <form.Field name="role">
            {(field) => (
              <div className="flex flex-col gap-2">
                <Label htmlFor={field.name}>Role</Label>
                <select
                  id={field.name}
                  name={field.name}
                  className="bg-surface border-input h-9 rounded-md border px-3 text-sm"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value as (typeof ACCOUNT_ROLES)[number])}
                >
                  {ACCOUNT_ROLES.map((role) => (
                    <option key={role} value={role}>
                      {role}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </form.Field>

          <Button type="submit" disabled={isPending}>
            Create account
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function AccountList() {
  const { data: accounts } = useAccounts();
  const [reset, setReset] = useState<CreatedAccount | undefined>();
  const [resetError, setResetError] = useState<unknown>();

  return (
    <Card>
      <CardHeader>
        <CardTitle>Accounts</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {reset && <ResetResult reset={reset} />}
        {resetError != null && (
          <Alert variant="danger">
            <AlertDescription>{resetPasswordErrorMessage(resetError)}</AlertDescription>
          </Alert>
        )}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Username</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Created</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {accounts.map((account) => (
              <TableRow key={account.username}>
                <TableCell>{account.username}</TableCell>
                <TableCell>{account.role}</TableCell>
                <TableCell>{account.enabled ? 'Enabled' : 'Disabled'}</TableCell>
                <TableCell>{formatDate(account.createdAt)}</TableCell>
                <TableCell>
                  <ResetPasswordButton
                    account={account}
                    onStart={() => {
                      setReset(undefined);
                      setResetError(undefined);
                    }}
                    onReset={setReset}
                    onError={setResetError}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function ResetPasswordButton({
  account,
  onStart,
  onReset,
  onError,
}: {
  account: AccountSummary;
  onStart: () => void;
  onReset: (reset: CreatedAccount) => void;
  onError: (error: unknown) => void;
}) {
  const { resetPassword, isPending } = useResetPassword();

  return (
    <Button
      type="button"
      variant="outline"
      size="sm"
      disabled={isPending}
      onClick={async () => {
        onStart();
        try {
          onReset(await resetPassword({ id: account.id }));
        } catch (error) {
          onError(error);
        }
      }}
    >
      Reset password
    </Button>
  );
}

function ResetResult({ reset }: { reset: CreatedAccount }) {
  return (
    <Alert variant="success">
      <AlertTitle>Password reset for {reset.username}</AlertTitle>
      <AlertDescription>
        <p>
          Temporary password: <code className="font-mono font-semibold">{reset.temporaryPassword}</code>
        </p>
        <p>
          It is shown only once. Copy it now and hand it to {reset.username}; it expires{' '}
          {formatTimestamp(reset.temporaryPasswordExpiresAt)}. Their sessions have ended.
        </p>
      </AlertDescription>
    </Alert>
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
