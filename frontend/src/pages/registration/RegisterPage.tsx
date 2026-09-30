import { Link } from '@tanstack/react-router';
import { Alert } from '../../common/ui/alert';
import { Button } from '../../common/ui/button';
import { LINK_CLASSES } from '../../common/ui/styles';
import { TextField } from '../../common/ui/text-field';
import { CenteredCardLayout } from '../../common/ui/centered-card-layout';
import { NewPasswordField, PasswordField } from '../../features/account/password';
import {
  REGISTRATION_BANNER_TEXT,
  useRegistrationForm,
  type RegistrationInput,
} from '../../features/account/registration';

const CREATED_MESSAGE = 'Account created. You can now log in.';

type FieldName = keyof RegistrationInput;

/** Public page where a Visitor creates an account, then continues to the normal login flow. */
export function RegisterPage() {
  const {
    field,
    errors,
    banner,
    created,
    disabled,
    submitting,
    loadingFrame,
    checklist,
    passwordEntered,
    strength,
    onSubmit,
  } = useRegistrationForm();
  const buttonText = submitting ? `Creating account${'.'.repeat(loadingFrame + 1)}` : 'Create account';
  const errorOf = (name: FieldName) => errors[name]?.message;

  return (
    <CenteredCardLayout
      description="Set up your account in under a minute."
      footer={
        <>
          Already have an account?{' '}
          <Link className={LINK_CLASSES} to="/login">
            Log in
          </Link>
        </>
      }
      headingId="register-heading"
      title="Create account"
    >
      {created && (
        <Alert className="mb-5" tone="success">
          {CREATED_MESSAGE}
        </Alert>
      )}
      {banner && (
        <Alert className="mb-5" tone="danger">
          {REGISTRATION_BANNER_TEXT[banner]}
        </Alert>
      )}
      <form className="grid gap-5" noValidate onSubmit={(event) => void onSubmit(event)}>
        <TextField
          autoComplete="username"
          disabled={disabled}
          error={errorOf('username')}
          id="username"
          label="Username"
          {...field('username')}
        />
        <TextField
          autoComplete="email"
          disabled={disabled}
          error={errorOf('email')}
          id="email"
          label="Email"
          type="email"
          {...field('email')}
        />
        <NewPasswordField
          checklist={checklist}
          disabled={disabled}
          error={errorOf('password')}
          id="password"
          label="Password"
          passwordEntered={passwordEntered}
          strength={strength}
          toggleLabel="password"
          {...field('password')}
        />
        <PasswordField
          disabled={disabled}
          error={errorOf('confirmPassword')}
          id="confirmPassword"
          label="Confirm password"
          toggleLabel="password confirmation"
          {...field('confirmPassword')}
        />

        <Button block className="mt-1" disabled={disabled} type="submit">
          {buttonText}
        </Button>
      </form>
    </CenteredCardLayout>
  );
}
