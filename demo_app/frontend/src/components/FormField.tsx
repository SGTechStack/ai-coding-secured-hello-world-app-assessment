import type { ComponentProps } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type FormFieldProps = Omit<
  ComponentProps<typeof Input>,
  "id" | "name" | "value" | "onChange"
> & {
  name: string;
  label: string;
  value: string;
  error: string | undefined;
  onChange: (value: string) => void;
};

/** A labelled input with its inline error, linked via aria-describedby and aria-invalid. */
export function FormField({
  name,
  label,
  error,
  onChange,
  ...inputProps
}: FormFieldProps) {
  const errorId = `${name}-error`;
  return (
    <div className="grid gap-2">
      <Label htmlFor={name}>{label}</Label>
      <Input
        {...inputProps}
        id={name}
        name={name}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
      />
      {error && (
        <p id={errorId} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
