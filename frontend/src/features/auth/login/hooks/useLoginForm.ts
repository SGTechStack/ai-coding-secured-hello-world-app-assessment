import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { usePendingSubmit } from '../../../../common/hooks/usePendingSubmit';
import { toLoginRejection } from '../api/rejection';
import { loginSchema, type LoginInput } from '../model/login';
import { useLogin } from './useLogin';

// A login 429 only ever comes from the Login rate limit; a Login lockout is a plain 401 (ADR 0003).
export type LoginFeedback = 'invalid-credentials' | 'too-many-attempts' | 'infrastructure';

/**
 * The login form: client-side validation on submit, the Login attempt, the minimum pending time, Authentication
 * failure feedback and entry into the protected application. The page only renders it.
 */
export function useLoginForm() {
  const [feedback, setFeedback] = useState<LoginFeedback | null>(null);
  const navigate = useNavigate();
  const login = useLogin();
  const submission = usePendingSubmit();
  const {
    register,
    handleSubmit,
    clearErrors,
    formState: { errors },
  } = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    reValidateMode: 'onSubmit',
  });

  const submit = async (input: LoginInput) => {
    setFeedback(null);
    clearErrors();
    const rejection = await submission.run(async () => {
      try {
        await login.mutateAsync(input);
      } catch (error) {
        // Shown at once, while the form stays pending for the rest of the minimum time.
        const { kind } = toLoginRejection(error);
        setFeedback(kind === 'invalid-credentials' || kind === 'too-many-attempts' ? kind : 'infrastructure');
        throw error;
      }
    }, toLoginRejection);
    submission.stop();
    if (!rejection) await navigate({ to: '/home' });
  };

  return {
    field: (name: keyof LoginInput) =>
      register(name, {
        onChange: () => {
          clearErrors(name);
          setFeedback(null);
        },
      }),
    errors,
    feedback,
    submitting: submission.pending,
    loadingFrame: submission.frame,
    onSubmit: handleSubmit(submit, () => setFeedback(null)),
  };
}
