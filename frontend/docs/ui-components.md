# UI components (PRIZM 4.0 / Base UI)

Read before adding or modifying anything under `src/components/ui/`.

PRIZM 4.0 is a **copy-paste model**, not an installed dependency. Components come from PRIZM or the shadcn Base UI registry (`npx shadcn@latest add --base base <component>`), must follow PRIZM's design and coding conventions, and may carry local customizations.

## Rules

- Wrap Base UI primitives (`@base-ui/react/<name>`) with `cva` variants and `cn()` from `@lib/utils`.
- Compose with Base UI's `render` prop, not Radix's `asChild`.
- Style with PRIZM semantic tokens (`bg-bg`, `text-fg`, `text-fg-muted`, `bg-surface`, `bg-accent`/`text-accent-fg`, `text-danger`, `text-success`, `text-warning`, `text-info`). shadcn default tokens (`bg-background`, `bg-primary`, …) are undefined in this theme.
- Dark mode is a `.dark` class toggle; CSS variables map `--prizm-color-*` to Tailwind tokens in `src/index.css`.
- Export named exports, following the existing Button pattern.

```tsx
// Replace the root element
<Dialog.Popup render={<div className="my-popup" />} />

// Access component state
<Checkbox.Indicator render={(props, state) => (
  <span {...props} className={cn(props.className, state.checked && 'text-accent')} />
)} />
```

## Component provenance

`src/components/component-registry.ts` (`COMPONENT_REGISTRY`) records each component's `source` and `prizmStatus`. Field definitions: [ui-component-provenance.md](ui-component-provenance.md).

Before modifying a component:

1. Read its registry entry.
2. Preserve PRIZM conventions and intentional customizations.
3. Do not infer `source: 'prizm'` from Base UI usage, or `not-aligned` from divergence from upstream.
4. Classify the change: PRIZM update, app customization, or bug fix.

Every add, modify, or remove under `ui/` updates `COMPONENT_REGISTRY` in the same change.
