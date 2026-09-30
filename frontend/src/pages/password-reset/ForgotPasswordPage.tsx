import { Link } from '@tanstack/react-router';
import { Alert } from '../../common/ui/alert';
import { Button } from '../../common/ui/button';
import { LINK_CLASSES } from '../../common/ui/styles';
import { TextField } from '../../common/ui/text-field';
import { FORGOT_PASSWORD_BANNER_TEXT, useForgotPasswordForm } from '../../features/account/password-reset';
import { CenteredCardLayout } from '../../common/ui/centered-card-layout';

/** Public page where a Visitor asks for a Password reset link by email. */
export function ForgotPasswordPage() {
  const { field, errors, banner, submitting, loadingFrame, onSubmit } = useForgotPasswordForm();
  const buttonText = submitting ? `Sending${'.'.repeat(loadingFrame + 1)}` : 'Send reset link';

  return (
    <CenteredCardLayout
      description="Enter the email you registered with and we will send you a reset link."
      footer={
        <>
          Remembered it?{' '}
          <Link className={LINK_CLASSES} to="/login">
            Log in
          </Link>
        </>
      }
      headingId="forgot-password-heading"
      title="Reset your password"
    >
      {banner && (
        <Alert className="mb-5" tone={banner === 'requested' ? 'success' : 'danger'}>
          {FORGOT_PASSWORD_BANNER_TEXT[banner]}
        </Alert>
      )}
      <form className="grid gap-5" noValidate onSubmit={(event) => void onSubmit(event)}>
        <TextField
          autoComplete="email"
          disabled={submitting}
          error={errors.email?.message}
          id="email"
          label="Email"
          type="email"
          {...field}
        />
        <Button block className="mt-1" disabled={submitting} type="submit">
          {buttonText}
        </Button>
      </form>
    </CenteredCardLayout>
  );
}
