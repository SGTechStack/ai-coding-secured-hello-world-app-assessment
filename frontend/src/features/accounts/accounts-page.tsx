import { useState } from 'react';
import { useForm } from '@tanstack/react-form';
import { Plus } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@components/ui/alert';
import { Button } from '@components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@components/ui/dialog';
import { Input } from '@components/ui/input';
import { Label } from '@components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/table';
import { FieldErrors } from '@features/auth/field-errors';
import { VALIDATION_DEBOUNCE_MS } from '@lib/constants';
import { formatDate, formatTimestamp } from '@lib/locale';
import { AccountRowActions } from './account-row-actions';
import { createAccountSchema } from './accounts.schema';
import {
  ACCOUNT_ROLES,
  createAccountErrorMessage,
  resetPasswordErrorMessage,
  useAccounts,
  useCreateAccount,
  useResetPassword,
  type AccountRole,
  type AccountSummary,
  type CreatedAccount,
} from './accounts.queries';

export function AccountsPage() {
  const [created, setCreated] = useState<CreatedAccount | undefined>();

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 p-6">
      {created && <CreatedResult created={created} />}
      <AccountList onCreated={setCreated} />
    </div>
  );
}

function CreatedResult({ created }: { created: CreatedAccount }) {
  return (
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
  );
}

function CreateAccountDialog({ onCreated }: { onCreated: (account: CreatedAccount | undefined) => void }) {
  const [open, setOpen] = useState(false);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button type="button" size="icon" aria-label="Add account" />}>
        <Plus className="size-4" />
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create account</DialogTitle>
          <DialogDescription>A temporary password is generated and shown once after creation.</DialogDescription>
        </DialogHeader>
        <CreateAccountForm
          onCreated={(account) => {
            onCreated(account);
            setOpen(false);
          }}
          onStart={() => onCreated(undefined)}
        />
      </DialogContent>
    </Dialog>
  );
}

function CreateAccountForm({
  onStart,
  onCreated,
}: {
  onStart: () => void;
  onCreated: (account: CreatedAccount) => void;
}) {
  const { createAccount, error, isPending } = useCreateAccount();

  const form = useForm({
    defaultValues: { username: '', role: ACCOUNT_ROLES[0] as AccountRole },
    validators: {
      onBlur: createAccountSchema,
      onChangeAsyncDebounceMs: VALIDATION_DEBOUNCE_MS,
      onChangeAsync: createAccountSchema,
      onSubmit: createAccountSchema,
    },
    onSubmit: async ({ value }) => {
      onStart();
      try {
        onCreated(await createAccount({ username: value.username.trim(), role: value.role }));
      } catch {
        return; // the failure is shown from the mutation error below
      }
    },
  });

  return (
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
          <AlertDescription>{createAccountErrorMessage(error)}</AlertDescription>
        </Alert>
      )}

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
            <Select
              value={field.state.value}
              onValueChange={(value) => value != null && field.handleChange(value as AccountRole)}
            >
              <SelectTrigger id={field.name} onBlur={field.handleBlur}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ACCOUNT_ROLES.map((role) => (
                  <SelectItem key={role} value={role}>
                    {role}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
      </form.Field>

      <DialogFooter>
        <Button type="submit" disabled={isPending}>
          Create account
        </Button>
      </DialogFooter>
    </form>
  );
}

function AccountList({ onCreated }: { onCreated: (account: CreatedAccount | undefined) => void }) {
  const { data: accounts } = useAccounts();
  const [reset, setReset] = useState<CreatedAccount | undefined>();
  const [resetError, setResetError] = useState<unknown>();

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Accounts</CardTitle>
        <CreateAccountDialog onCreated={onCreated} />
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
              <TableHead>Status</TableHead>
              <TableHead>Created</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {accounts.map((account) => (
              <TableRow key={account.username}>
                <TableCell>{account.username}</TableCell>
                <TableCell>{account.enabled ? 'Enabled' : 'Disabled'}</TableCell>
                <TableCell>{formatDate(account.createdAt)}</TableCell>
                <TableCell>
                  <div className="flex flex-wrap items-center gap-2">
                    <ResetPasswordButton
                      account={account}
                      onStart={() => {
                        setReset(undefined);
                        setResetError(undefined);
                      }}
                      onReset={setReset}
                      onError={setResetError}
                    />
                    <AccountRowActions account={account} />
                  </div>
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
