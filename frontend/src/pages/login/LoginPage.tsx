import { Link, useSearch } from '@tanstack/react-router';
import { Alert } from '../../common/ui/alert';
import { Button } from '../../common/ui/button';
import { LINK_CLASSES } from '../../common/ui/styles';
import { TextField } from '../../common/ui/text-field';
import { INFRASTRUCTURE_TEXT } from '../../common/http/rejection';
import { PASSWORD_UPDATED_TEXT } from '../../features/account/password-reset';
import { useLoginForm, type LoginFeedback } from '../../features/auth/login';
import { CenteredCardLayout } from '../../common/ui/centered-card-layout';

const FEEDBACK_TEXT: Record<LoginFeedback, string> = {
  'invalid-credentials': 'Invalid username or password',
  'too-many-attempts': 'Too many sign-in attempts. Please try again later.',
  infrastructure: INFRASTRUCTURE_TEXT,
};

/** Responsive entry page for establishing an authenticated session. Renders without contacting the server. */
export function LoginPage() {
  const { field, errors, feedback, attempted, submitting, loadingFrame, onSubmit } = useLoginForm();
  // Set only by a completed Password reset; gone for good after the first login attempt, so it never sits
  // beside (or reappears after) that attempt's feedback.
  const passwordUpdated = useSearch({ from: '/login' }).reset === 'done' && !attempted;
  const buttonText = submitting ? `Logging in${'.'.repeat(loadingFrame + 1)}` : 'Log in';
  // A form-level failure replaces field messages so only one alert is announced.
  const fieldError = (name: 'username' | 'password') => (feedback ? undefined : errors[name]?.message);

  return (
    <CenteredCardLayout
      description="Welcome back. Enter your details to continue."
      footer={
        <>
          New here?{' '}
          <Link className={LINK_CLASSES} to="/register">
            Create an account
          </Link>
        </>
      }
      headingId="login-heading"
      title="Log in"
    >
      {passwordUpdated && (
        <Alert className="mb-5" tone="success">
          {PASSWORD_UPDATED_TEXT}
        </Alert>
      )}
      {feedback && (
        <Alert className="mb-5" tone="danger">
          {FEEDBACK_TEXT[feedback]}
        </Alert>
      )}
      <form className="grid gap-5" onSubmit={(event) => void onSubmit(event)}>
        <TextField
          autoComplete="username"
          disabled={submitting}
          error={fieldError('username')}
          id="username"
          label="Username"
          {...field('username')}
        />
        <TextField
          autoComplete="current-password"
          disabled={submitting}
          error={fieldError('password')}
          id="password"
          label="Password"
          type="password"
          {...field('password')}
        />
        <Button block className="mt-1" disabled={submitting} type="submit">
          {buttonText}
        </Button>
        {/* After the button, so Tab still goes Username → Password → Log in (login-flow AC3). */}
        <p className="-mt-2 text-center text-sm">
          <Link className={LINK_CLASSES} to="/forgot-password">
            Forgot password?
          </Link>
        </p>
      </form>
    </CenteredCardLayout>
  );
}
