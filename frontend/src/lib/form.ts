import type { AnyFieldApi } from '@tanstack/react-form';

/**
 * Field `onChange` listener: re-runs the blur validation while a blur error is showing, so the
 * error clears as soon as the value is fixed instead of waiting for the next blur.
 */
export function revalidateBlurError({ fieldApi }: { fieldApi: AnyFieldApi }) {
  if (fieldApi.state.meta.errorMap.onBlur) void fieldApi.validate('blur');
}
