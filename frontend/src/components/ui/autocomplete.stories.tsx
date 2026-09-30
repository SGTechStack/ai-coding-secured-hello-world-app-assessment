import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import {
  Autocomplete,
  AutocompleteContent,
  AutocompleteEmpty,
  AutocompleteGroup,
  AutocompleteInput,
  AutocompleteItem,
  AutocompleteLabel,
  AutocompleteList,
  AutocompleteSeparator,
} from './autocomplete';

const UNITS = ['EA', 'PC', 'LOT', 'KG', 'LTR', 'MTR', 'SET', 'BOX', 'PAL'];

type Framework = { label: string; value: string };

const FRAMEWORKS: Framework[] = [
  { label: 'Next.js', value: 'next' },
  { label: 'SvelteKit', value: 'sveltekit' },
  { label: 'Nuxt', value: 'nuxt' },
  { label: 'Remix', value: 'remix' },
  { label: 'Astro', value: 'astro' },
];

const GROUPED_ITEMS = [
  { group: 'Standard', items: ['EA', 'PC', 'LOT'] },
  { group: 'Weight / Volume', items: ['KG', 'LTR', 'MTR'] },
  { group: 'Packaging', items: ['SET', 'BOX', 'PAL'] },
];

const meta: Meta = {
  title: 'UI/Autocomplete',
};

export default meta;
type Story = StoryObj;

// Basic — free-text input with suggestions; typing a value not in the list is valid
export const Default: Story = {
  render: () => (
    <Autocomplete items={UNITS}>
      <AutocompleteInput placeholder="e.g. EA" />
      <AutocompleteContent>
        <AutocompleteEmpty>No matching units.</AutocompleteEmpty>
        <AutocompleteList>
          {(item: string) => (
            <AutocompleteItem key={item} value={item}>
              {item}
            </AutocompleteItem>
          )}
        </AutocompleteList>
      </AutocompleteContent>
    </Autocomplete>
  ),
};

// With clear button — pre-filled value, X resets to empty
export const WithClear: Story = {
  render: () => (
    <Autocomplete items={UNITS} defaultValue="EA">
      <AutocompleteInput placeholder="e.g. EA" showClear />
      <AutocompleteContent>
        <AutocompleteEmpty>No matching units.</AutocompleteEmpty>
        <AutocompleteList>
          {(item: string) => (
            <AutocompleteItem key={item} value={item}>
              {item}
            </AutocompleteItem>
          )}
        </AutocompleteList>
      </AutocompleteContent>
    </Autocomplete>
  ),
};

// Object items — itemToStringValue maps object → display string
export const ObjectItems: Story = {
  render: () => (
    <Autocomplete items={FRAMEWORKS} itemToStringValue={(f: Framework) => f.label}>
      <AutocompleteInput placeholder="Search frameworks…" />
      <AutocompleteContent>
        <AutocompleteEmpty>No results found.</AutocompleteEmpty>
        <AutocompleteList>
          {(framework: Framework) => (
            <AutocompleteItem key={framework.value} value={framework}>
              {framework.label}
            </AutocompleteItem>
          )}
        </AutocompleteList>
      </AutocompleteContent>
    </Autocomplete>
  ),
};

// Grouped suggestions with separators
export const WithGroups: Story = {
  render: () => {
    const allItems = GROUPED_ITEMS.flatMap(({ items }) => items);
    return (
      <Autocomplete items={allItems}>
        <AutocompleteInput placeholder="e.g. KG" />
        <AutocompleteContent>
          <AutocompleteEmpty>No matching units.</AutocompleteEmpty>
          <AutocompleteList>
            {GROUPED_ITEMS.map(({ group, items }, i) => (
              <>
                {i > 0 && <AutocompleteSeparator key={`sep-${group}`} />}
                <AutocompleteGroup key={group}>
                  <AutocompleteLabel>{group}</AutocompleteLabel>
                  {items.map((item) => (
                    <AutocompleteItem key={item} value={item}>
                      {item}
                    </AutocompleteItem>
                  ))}
                </AutocompleteGroup>
              </>
            ))}
          </AutocompleteList>
        </AutocompleteContent>
      </Autocomplete>
    );
  },
};

// Controlled — value wired to external state
function ControlledAutocomplete() {
  const [value, setValue] = useState('EA');
  return (
    <div className="space-y-2">
      <Autocomplete items={UNITS} value={value} onValueChange={setValue}>
        <AutocompleteInput placeholder="e.g. EA" showClear />
        <AutocompleteContent>
          <AutocompleteEmpty>No matching units.</AutocompleteEmpty>
          <AutocompleteList>
            {(item: string) => (
              <AutocompleteItem key={item} value={item}>
                {item}
              </AutocompleteItem>
            )}
          </AutocompleteList>
        </AutocompleteContent>
      </Autocomplete>
      <p className="text-fg-muted text-xs">value: "{value}"</p>
    </div>
  );
}

export const Controlled: Story = {
  render: () => <ControlledAutocomplete />,
};

// Disabled
export const Disabled: Story = {
  render: () => (
    <Autocomplete items={UNITS} defaultValue="EA">
      <AutocompleteInput placeholder="e.g. EA" disabled />
      <AutocompleteContent>
        <AutocompleteList>
          {(item: string) => (
            <AutocompleteItem key={item} value={item}>
              {item}
            </AutocompleteItem>
          )}
        </AutocompleteList>
      </AutocompleteContent>
    </Autocomplete>
  ),
};
