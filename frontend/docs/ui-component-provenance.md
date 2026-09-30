# UI Component Provenance

`src/components/ui/` contains components from PRIZM 4.0, shadcn, and app-specific implementations.

PRIZM 4.0 is a **design/styling reference**, not a requirement to use the exact PRIZM source code. A component can come from shadcn or be custom-built and still be considered PRIZM-aligned if it follows PRIZM's design and coding conventions.

The source of truth for this information is:

`src/components/component-registry.ts`

## What the registry tracks

Each component has two fields:

### `source`

Where the component originally came from:

- `prizm` — copied from PRIZM 4.0.
- `shadcn` — copied from shadcn component.
- `demo-app` — built specifically for this application.
- `unknown` — origin cannot be confirmed.

### `prizmStatus`

Whether the current implementation follows PRIZM 4.0 conventions:

- `aligned` — follows PRIZM conventions.
- `customized` — follows PRIZM conventions but has intentional app-specific changes.
- `not-aligned` — does not currently follow PRIZM conventions.
- `needs-review` — origin/alignment is unclear and should not be guessed.

These are independent.

For example:

```ts
{
  source: 'shadcn',
  prizmStatus: 'aligned',
}
```
