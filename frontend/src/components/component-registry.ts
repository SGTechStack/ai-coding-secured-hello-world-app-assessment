/**
 * Tracks the origin and PRIZM 4.0 alignment of components in
 * src/components/ui/.
 *
 * `source` = where the component originally came from.
 * `prizmStatus` = whether the current implementation follows PRIZM 4.0.
 *
 * These are independent. A shadcn component can be PRIZM-aligned after
 * being adapted to PRIZM's design conventions.
 *
 * Check this registry before modifying UI components. Update the entry
 * when a component's source or PRIZM status changes.
 */
export type ComponentSource = 'prizm' | 'shadcn' | 'demo-app' | 'unknown';

export type PrizmStatus = 'aligned' | 'customized' | 'not-aligned' | 'needs-review';

export interface ComponentMetadata {
  /** Where the component's code originated. */
  source: ComponentSource;
  /** Whether the current implementation follows PRIZM 4.0 conventions today. */
  prizmStatus: PrizmStatus;
  /** PRIZM version the component was authored/migrated against, if known. */
  prizmVersion?: string;
  notes?: string;
}

export const COMPONENT_REGISTRY: Record<string, ComponentMetadata> = {
  alert: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  autocomplete: {
    source: 'shadcn',
    prizmStatus: 'customized',
    prizmVersion: '4.0',
    notes:
      'App-specific customization: showClear behavior, border removal, and input-group restructuring for unit-of-measure fields — predates the PRIZM token migration.',
  },
  avatar: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  badge: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  button: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  calendar: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  card: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  'chat-bubble': {
    source: 'demo-app',
    prizmStatus: 'aligned',
    notes:
      'Domain-specific chat UI with no PRIZM 4.0 or shadcn catalog equivalent — a custom composition that follows PRIZM conventions, not a PRIZM primitive.',
  },
  checkbox: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  combobox: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  'data-table': {
    source: 'demo-app',
    prizmStatus: 'aligned',
    notes:
      'Composes the generic Table primitive with @tanstack/react-table; carries no color tokens of its own. Unused in production — only its own Storybook consumes it; the real production table is a separate, unrelated component.',
  },
  'date-picker': {
    source: 'shadcn',
    prizmStatus: 'aligned',
    prizmVersion: '4.0',
    notes:
      'App-specific composition (ISO date parsing, minDate business rule) over PRIZM Popover + hand-rolled Calendar. Live in production forms with app-specific date-field wiring.',
  },
  dialog: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  drawer: {
    source: 'shadcn',
    prizmStatus: 'aligned',
    prizmVersion: '4.0',
    notes:
      'Naming note: PRIZM 4.0 calls this component "Sheet" — demo-app kept the pre-existing "Drawer" name. Naming divergence only, not a styling one.',
  },
  'dropdown-menu': {
    source: 'shadcn',
    prizmStatus: 'customized',
    prizmVersion: '4.0',
    notes:
      "Fully PRIZM-token-compliant. One deliberate customization: an `asChild` boolean prop grafted onto Base UI's `render`-prop composition on the trigger — an intentional app-facing API convenience, not leftover shadcn styling.",
  },
  'empty-state': { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  input: { source: 'shadcn', prizmStatus: 'aligned', prizmVersion: '4.0' },
  'input-group': {
    source: 'shadcn',
    prizmStatus: 'customized',
    prizmVersion: '4.0',
    notes:
      'App-specific composition to combine qty + unit-of-measure fields and restructure the item form layout, later token-migrated to PRIZM.',
  },
  label: {
    source: 'shadcn',
    prizmStatus: 'not-aligned',
    notes:
      'Never migrated to PRIZM — a pre-PRIZM shadcn scaffold (data-slot="label", peer/group selectors, zero color tokens). Paired with PRIZM Input in production forms with no visible clash today, but has no PRIZM danger/required-state variant.',
  },
  popover: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  progress: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  'radio-group': { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  'scroll-area': { source: 'shadcn', prizmStatus: 'aligned', prizmVersion: '4.0' },
  select: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  separator: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  skeleton: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  slider: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  spinner: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  switch: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  table: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  tabs: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  text: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  textarea: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  toast: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
  toggle: { source: 'shadcn', prizmStatus: 'aligned', prizmVersion: '4.0' },
  'toggle-group': {
    source: 'shadcn',
    prizmStatus: 'needs-review',
    notes:
      'Never touched by a PRIZM migration commit. Has no color tokens of its own (delegates to toggle.tsx), but has two confirmed functional bugs (dropped orientation prop; dead data-vertical/data-horizontal selectors) that a migration review would likely have caught. Ambiguous whether to call this reviewed-and-skipped or simply missed — flagging rather than guessing.',
  },
  tooltip: { source: 'prizm', prizmStatus: 'aligned', prizmVersion: '4.0' },
};
