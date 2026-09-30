import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { registerAccount, toRegistrationRejection } from '../api/registration-api';
import {
  registrationFeedbackFor,
  registrationSchema,
  type RegistrationBanner,
  type RegistrationField,
  type RegistrationInput,
} from '../model/registration';
import { delay, usePendingSubmit } from '../../../../common/hooks/usePendingSubmit';
import { passwordChecklist } from '../../core';
import { usePasswordStrength } from '../../password';

const SUCCESS_REDIRECT_MS = 1500;

/**
 * The registration form: client-side validation on submit, live password guidance, the registration request, the
 * minimum pending time, server rejection feedback and the hand-off to login. The page only renders it.
 */
export function useRegistrationForm() {
  const [banner, setBanner] = useState<RegistrationBanner | 'created' | null>(null);
  const navigate = useNavigate();
  const submission = usePendingSubmit();
  const {
    register,
    handleSubmit,
    clearErrors,
    setError,
    control,
    formState: { errors },
  } = useForm<RegistrationInput>({
    resolver: zodResolver(registrationSchema),
    reValidateMode: 'onSubmit',
    defaultValues: { username: '', email: '', password: '', confirmPassword: '' },
  });
  const [username, email, password] = useWatch({ control, name: ['username', 'email', 'password'] });
  const strength = usePasswordStrength(password, username, email);

  const submit = async ({ confirmPassword: _, ...request }: RegistrationInput) => {
    setBanner(null);
    clearErrors();
    // The confirmation stays in the browser; only these three fields are sent, unaltered.
    const rejection = await submission.run(() => registerAccount(request), toRegistrationRejection);
    if (rejection) {
      submission.stop();
      const feedback = registrationFeedbackFor(rejection);
      setBanner(feedback.banner ?? null);
      for (const [field, messages] of Object.entries(feedback.fieldMessages) as [RegistrationField, string[]][]) {
        setError(field, { type: 'server', message: messages.join('\n') });
      }
      return;
    }
    setBanner('created');
    await delay(SUCCESS_REDIRECT_MS);
    await navigate({ to: '/login' });
  };

  const created = banner === 'created';
  return {
    field: (name: keyof RegistrationInput) =>
      register(name, {
        onChange: () => {
          clearErrors(name);
          if (!created) setBanner(null);
        },
      }),
    errors,
    banner: created ? null : banner,
    created,
    disabled: submission.pending || created,
    submitting: submission.pending,
    loadingFrame: submission.frame,
    checklist: passwordChecklist(password, username, email),
    passwordEntered: password.length > 0,
    strength,
    onSubmit: handleSubmit(submit, () => setBanner(null)),
  };
}
