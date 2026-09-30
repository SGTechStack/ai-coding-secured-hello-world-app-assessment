import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { confirmPasswordReset, toPasswordResetRejection } from '../api/password-reset-api';
import {
  resetPasswordFeedbackFor,
  resetPasswordSchema,
  type ResetPasswordBanner,
  type ResetPasswordInput,
} from '../model/password-reset';
import { passwordChecklist } from '../../core';
import { usePasswordStrength } from '../../password';
import { usePendingSubmit } from '../../../../common/hooks/usePendingSubmit';

/** The Password reset token from the link's fragment (`#token=…`), which never reaches the server's logs. */
function readLinkToken(): string | null {
  return new URLSearchParams(window.location.hash.slice(1)).get('token') || null;
}

/**
 * The reset-password form: reads the token from the link, then removes it from the address bar and history; checks
 * the new password as registration does; submits once per click; and hands off to login. The page only renders it.
 */
export function useResetPasswordForm() {
  const [token] = useState(readLinkToken);
  const [linkRejected, setLinkRejected] = useState(false);
  const [banner, setBanner] = useState<ResetPasswordBanner | null>(null);
  const navigate = useNavigate();
  const submission = usePendingSubmit();
  const {
    register,
    handleSubmit,
    clearErrors,
    setError,
    control,
    formState: { errors },
  } = useForm<ResetPasswordInput>({
    resolver: zodResolver(resetPasswordSchema),
    reValidateMode: 'onSubmit',
    defaultValues: { newPassword: '', confirmPassword: '' },
  });
  const newPassword = useWatch({ control, name: 'newPassword' });
  const strength = usePasswordStrength(newPassword, '', '');

  useEffect(() => {
    // Keeps the router's own history state; only the fragment goes.
    if (window.location.hash) {
      window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
    }
  }, []);

  const submit = async ({ newPassword: password }: ResetPasswordInput) => {
    if (!token) return;
    setBanner(null);
    clearErrors();
    const rejection = await submission.run(
      () => confirmPasswordReset({ token, newPassword: password }),
      toPasswordResetRejection,
    );
    submission.stop();
    if (!rejection) {
      await navigate({ to: '/login', search: { reset: 'done' } });
      return;
    }
    const feedback = resetPasswordFeedbackFor(rejection);
    if (feedback.invalidLink) setLinkRejected(true);
    setBanner(feedback.banner ?? null);
    if (feedback.passwordMessages.length > 0) {
      setError('newPassword', { type: 'server', message: feedback.passwordMessages.join('\n') });
    }
  };

  return {
    invalidLink: token === null || linkRejected,
    field: (name: keyof ResetPasswordInput) =>
      register(name, {
        onChange: () => {
          clearErrors(name);
          setBanner(null);
        },
      }),
    errors,
    banner,
    submitting: submission.pending,
    loadingFrame: submission.frame,
    checklist: passwordChecklist(newPassword),
    passwordEntered: newPassword.length > 0,
    strength,
    onSubmit: handleSubmit(submit, () => setBanner(null)),
  };
}
