import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxGroup,
  ComboboxGroupLabel,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxTrigger,
} from './combobox';

const FRUITS = ['Apple', 'Banana', 'Cherry', 'Grape', 'Mango', 'Orange', 'Pear', 'Strawberry'];

type Framework = { label: string; value: string };

const FRAMEWORKS: Framework[] = [
  { label: 'Next.js', value: 'next' },
  { label: 'SvelteKit', value: 'sveltekit' },
  { label: 'Nuxt', value: 'nuxt' },
  { label: 'Remix', value: 'remix' },
  { label: 'Astro', value: 'astro' },
];

const GROUPED_ITEMS = [
  { group: 'Fruits', items: ['Apple', 'Banana', 'Cherry', 'Grape'] },
  { group: 'Vegetables', items: ['Broccoli', 'Carrot', 'Pea', 'Spinach'] },
];

const meta: Meta = {
  title: 'UI/Combobox',
};

export default meta;
type Story = StoryObj;

// Select-style — closed by default, opens on trigger click
export const Default: Story = {
  render: () => (
    <Combobox items={FRUITS}>
      <ComboboxInput placeholder="Search products…" className="w-56" />
      <ComboboxContent>
        <ComboboxList>
          {(item: string) => (
            <ComboboxItem key={item} value={item}>
              {item}
            </ComboboxItem>
          )}
        </ComboboxList>
        <ComboboxEmpty>No results found.</ComboboxEmpty>
      </ComboboxContent>
    </Combobox>
  ),
};

// Autocomplete-style — always-visible input, filters as you type
export const Autocomplete: Story = {
  render: () => (
    <Combobox items={FRUITS}>
      <ComboboxInput placeholder="Search fruits…" className="w-56" />
      <ComboboxContent>
        <ComboboxEmpty>No results found.</ComboboxEmpty>
        <ComboboxList>
          {(item: string) => (
            <ComboboxItem key={item} value={item}>
              {item}
            </ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  ),
};

// Object items — itemToStringLabel shows the label in the input once selected
export const ObjectItems: Story = {
  render: () => (
    <Combobox items={FRAMEWORKS} itemToStringLabel={(f: Framework) => f.label}>
      <ComboboxInput placeholder="Select a framework…" className="w-56" />
      <ComboboxContent>
        <ComboboxEmpty>No results found.</ComboboxEmpty>
        <ComboboxList>
          {(framework: Framework) => (
            <ComboboxItem key={framework.value} value={framework}>
              {framework.label}
            </ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  ),
};

// Grouped items
export const WithGroups: Story = {
  render: () => {
    const allItems = GROUPED_ITEMS.flatMap(({ items }) => items);
    return (
      <Combobox items={allItems}>
        <ComboboxInput placeholder="Select a food…" className="w-56" />
        <ComboboxContent>
          <ComboboxEmpty>No results found.</ComboboxEmpty>
          <ComboboxList>
            {GROUPED_ITEMS.map(({ group, items }) => (
              <ComboboxGroup key={group}>
                <ComboboxGroupLabel>{group}</ComboboxGroupLabel>
                {items.map((item) => (
                  <ComboboxItem key={item} value={item}>
                    {item}
                  </ComboboxItem>
                ))}
              </ComboboxGroup>
            ))}
          </ComboboxList>
        </ComboboxContent>
      </Combobox>
    );
  },
};

// Multi-select — no chips UI, trigger shows a plain selection summary
function MultiSelectCombobox() {
  const [value, setValue] = useState<string[]>([]);
  return (
    <Combobox items={FRUITS} multiple value={value} onValueChange={setValue}>
      <ComboboxTrigger className="w-56">
        {value.length > 0 ? `${value.length} selected` : 'Select fruits…'}
      </ComboboxTrigger>
      <ComboboxContent>
        <ComboboxEmpty>No results found.</ComboboxEmpty>
        <ComboboxList>
          {(item: string) => (
            <ComboboxItem key={item} value={item}>
              {item}
            </ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  );
}

export const MultiSelect: Story = {
  render: () => <MultiSelectCombobox />,
};

// Disabled state
export const Disabled: Story = {
  render: () => (
    <Combobox items={FRUITS} defaultValue="Apple">
      <ComboboxTrigger className="w-56" disabled />
      <ComboboxContent>
        <ComboboxList>
          {(item: string) => (
            <ComboboxItem key={item} value={item}>
              {item}
            </ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  ),
};

// Auto-highlight first match while typing
export const AutoHighlight: Story = {
  render: () => (
    <Combobox items={FRUITS} autoHighlight>
      <ComboboxInput placeholder="Type to search…" className="w-56" />
      <ComboboxContent>
        <ComboboxEmpty>No results found.</ComboboxEmpty>
        <ComboboxList>
          {(item: string) => (
            <ComboboxItem key={item} value={item}>
              {item}
            </ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  ),
};
