import { useState } from 'react';
import { useForm } from '@tanstack/react-form';
import { KeyRound, Plus } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@components/ui/alert';
import { Badge } from '@components/ui/badge';
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/table';
import { FieldErrors } from '@features/auth/field-errors';
import { VALIDATION_DEBOUNCE_MS } from '@lib/constants';
import { formatDate, formatTimestamp } from '@lib/locale';
import { ActionTooltip } from './action-tooltip';
import { AccountRoleSelect, AccountRowActions } from './account-row-actions';
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

/** Accounts shown per page in each Role tab. */
const ACCOUNTS_PAGE_SIZE = 10;

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
      <ActionTooltip label="Add account">
        <DialogTrigger render={<Button type="button" size="icon" aria-label="Add account" />}>
          <Plus className="size-4" />
        </DialogTrigger>
      </ActionTooltip>
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
        <Tabs defaultValue={ACCOUNT_ROLES[0]}>
          <TabsList>
            {ACCOUNT_ROLES.map((role) => (
              <TabsTrigger key={role} value={role}>
                {role} ({accounts.filter((account) => account.role === role).length})
              </TabsTrigger>
            ))}
          </TabsList>
          {ACCOUNT_ROLES.map((role) => (
            <TabsContent key={role} value={role}>
              <RoleAccountsTable
                accounts={accounts.filter((account) => account.role === role)}
                onResetStart={() => {
                  setReset(undefined);
                  setResetError(undefined);
                }}
                onReset={setReset}
                onResetError={setResetError}
              />
            </TabsContent>
          ))}
        </Tabs>
      </CardContent>
    </Card>
  );
}

/** Accounts of one Role, paged client-side; the page resets when its tab is reopened. */
function RoleAccountsTable({
  accounts,
  onResetStart,
  onReset,
  onResetError,
}: {
  accounts: AccountSummary[];
  onResetStart: () => void;
  onReset: (reset: CreatedAccount) => void;
  onResetError: (error: unknown) => void;
}) {
  const [requestedPage, setPage] = useState(0);
  const pageCount = Math.max(1, Math.ceil(accounts.length / ACCOUNTS_PAGE_SIZE));
  // Deleting or re-roling the last row of the last page must not strand the view past the end.
  const page = Math.min(requestedPage, pageCount - 1);
  const pageAccounts = accounts.slice(page * ACCOUNTS_PAGE_SIZE, (page + 1) * ACCOUNTS_PAGE_SIZE);

  if (accounts.length === 0) {
    return <p className="text-fg-muted py-6 text-center text-sm">No accounts with this role.</p>;
  }

  return (
    <div className="flex flex-col gap-4 pt-4">
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
          {pageAccounts.map((account) => (
            <TableRow key={account.username}>
              <TableCell>{account.username}</TableCell>
              <TableCell>
                <AccountRoleSelect account={account} />
              </TableCell>
              <TableCell>
                <Badge variant={account.enabled ? 'success' : 'subtle'}>
                  {account.enabled ? 'Enabled' : 'Disabled'}
                </Badge>
              </TableCell>
              <TableCell>{formatDate(account.createdAt)}</TableCell>
              <TableCell>
                <div className="flex flex-wrap items-center gap-2">
                  <ResetPasswordButton
                    account={account}
                    onStart={onResetStart}
                    onReset={onReset}
                    onError={onResetError}
                  />
                  <AccountRowActions account={account} />
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <div className="flex items-center justify-between text-sm">
        <span className="text-fg-muted">
          Page {page + 1} of {pageCount}
        </span>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page >= pageCount - 1}
            onClick={() => setPage(page + 1)}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
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
    <ActionTooltip label="Reset password">
      <Button
        type="button"
        variant="outline"
        size="icon"
        className="size-8"
        aria-label="Reset password"
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
        <KeyRound className="size-4" />
      </Button>
    </ActionTooltip>
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
