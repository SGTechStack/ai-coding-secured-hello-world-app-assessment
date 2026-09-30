# Adopt TanStack Form for frontend forms

Hand-rolled forms can duplicate field state, validation, and server-error mapping. We use `@tanstack/react-form` as the standard form layer to keep those concerns consistent as forms are added. Zod validation is wired through function validators; TanStack Form supports the Standard Schema spec directly.

## Decisions

**Validation triggers — `onBlur` + debounced `onChangeAsync`.** Fields validate immediately when the user leaves them (`onBlur`) and also while typing after a short pause (`onChangeAsync` with a shared `VALIDATION_DEBOUNCE_MS` constant). Submit-only validation forces the user to complete the whole form before receiving any feedback; unbounced `onChange` is too aggressive for numeric and free-text fields. The debounce constant is defined once and applied uniformly across all fields.

**Server errors via `form.setFieldMeta`.** Field errors returned by the API are injected directly into field state. This removes a separate field-error state object and means each field has a single source of truth for its error message.

**Zod schema for every form.** Each form has a `[feature].schema.ts` with a Zod schema validated through function validators, consistent with the frontend convention.

## Considered Options

**React Hook Form** — popular and mature, but not from the TanStack family. Adds a second form-management model alongside TanStack Query and TanStack Router. Rejected to keep the stack coherent.

**Keep hand-rolled forms** — avoids a new dependency, but validation and server-error handling would be duplicated across forms.

## Consequences

- `@tanstack/react-form` is the standard for frontend forms. Zod schemas are called directly inside function validators.
- Each form keeps its schema next to its feature query file.
- Bundle size increases slightly; TanStack Form is tree-shakeable so only used field types are included.