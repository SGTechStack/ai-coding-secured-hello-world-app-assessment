import { Link } from '@tanstack/react-router';
import { Alert } from '../../common/ui/alert';
import { Button } from '../../common/ui/button';
import { LINK_CLASSES } from '../../common/ui/styles';
import { CenteredCardLayout } from '../../common/ui/centered-card-layout';
import { NewPasswordField, PasswordField } from '../../features/account/password';
import {
  INVALID_RESET_LINK_TEXT,
  RESET_PASSWORD_BANNER_TEXT,
  useResetPasswordForm,
} from '../../features/account/password-reset';

/** Public page, opened from the emailed link, where a Visitor chooses a new password. */
export function ResetPasswordPage() {
  const {
    invalidLink,
    field,
    errors,
    banner,
    submitting,
    loadingFrame,
    checklist,
    passwordEntered,
    strength,
    onSubmit,
  } = useResetPasswordForm();
  const buttonText = submitting ? `Resetting password${'.'.repeat(loadingFrame + 1)}` : 'Reset password';
  const footer = (
    <>
      Remembered it?{' '}
      <Link className={LINK_CLASSES} to="/login">
        Log in
      </Link>
    </>
  );

  if (invalidLink) {
    return (
      <CenteredCardLayout
        description="Reset links work once and expire after 30 minutes."
        footer={footer}
        headingId="reset-password-heading"
        title="Choose a new password"
      >
        <Alert className="mb-5" tone="danger">
          {INVALID_RESET_LINK_TEXT}
        </Alert>
        <Link className={LINK_CLASSES} to="/forgot-password">
          Request a new reset link
        </Link>
      </CenteredCardLayout>
    );
  }

  return (
    <CenteredCardLayout
      description="Choose a password you have not used before."
      footer={footer}
      headingId="reset-password-heading"
      title="Choose a new password"
    >
      {banner && (
        <Alert className="mb-5" tone="danger">
          {RESET_PASSWORD_BANNER_TEXT[banner]}
        </Alert>
      )}
      <form className="grid gap-5" noValidate onSubmit={(event) => void onSubmit(event)}>
        <NewPasswordField
          checklist={checklist}
          disabled={submitting}
          error={errors.newPassword?.message}
          id="newPassword"
          label="New password"
          passwordEntered={passwordEntered}
          strength={strength}
          toggleLabel="new password"
          {...field('newPassword')}
        />
        <PasswordField
          disabled={submitting}
          error={errors.confirmPassword?.message}
          id="confirmPassword"
          label="Confirm new password"
          toggleLabel="password confirmation"
          {...field('confirmPassword')}
        />
        <Button block className="mt-1" disabled={submitting} type="submit">
          {buttonText}
        </Button>
      </form>
    </CenteredCardLayout>
  );
}
