import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { requestPasswordReset, toPasswordResetRejection } from '../api/password-reset-api';
import {
  forgotPasswordFeedbackFor,
  forgotPasswordSchema,
  type ForgotPasswordBanner,
  type ForgotPasswordInput,
} from '../model/password-reset';
import { usePendingSubmit } from '../../../../common/hooks/usePendingSubmit';

/**
 * The forgot-password form: client-side email validation on submit, the reset request, the minimum pending time, and
 * the one generic confirmation. The page only renders it.
 */
export function useForgotPasswordForm() {
  const [banner, setBanner] = useState<ForgotPasswordBanner | null>(null);
  const submission = usePendingSubmit();
  const {
    register,
    handleSubmit,
    clearErrors,
    setError,
    formState: { errors },
  } = useForm<ForgotPasswordInput>({
    resolver: zodResolver(forgotPasswordSchema),
    reValidateMode: 'onSubmit',
    defaultValues: { email: '' },
  });

  const submit = async (input: ForgotPasswordInput) => {
    setBanner(null);
    clearErrors();
    const rejection = await submission.run(() => requestPasswordReset(input), toPasswordResetRejection);
    submission.stop();
    if (!rejection) {
      setBanner('requested');
      return;
    }
    const feedback = forgotPasswordFeedbackFor(rejection);
    setBanner(feedback.banner ?? null);
    if (feedback.emailMessages.length > 0)
      setError('email', { type: 'server', message: feedback.emailMessages.join('\n') });
  };

  return {
    field: register('email', {
      onChange: () => {
        clearErrors('email');
        setBanner(null);
      },
    }),
    errors,
    banner,
    submitting: submission.pending,
    loadingFrame: submission.frame,
    onSubmit: handleSubmit(submit, () => setBanner(null)),
  };
}
