import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import {
  Autocomplete,
  AutocompleteAddon,
  AutocompleteContent,
  AutocompleteEmpty,
  AutocompleteItem,
  AutocompleteList,
} from './autocomplete';
import { InputGroup, InputGroupAddon, InputGroupInput, InputGroupText } from './input-group';
import { Label } from './label';

const UOM_OPTIONS = ['EA', 'PC', 'LOT', 'KG', 'LTR', 'MTR', 'SET', 'BOX', 'PAL'];

const meta: Meta = {
  title: 'UI/InputGroup',
};

export default meta;
type Story = StoryObj;

// Plain text addon — e.g. currency prefix
export const WithTextAddon: Story = {
  render: () => (
    <InputGroup className="w-48">
      <InputGroupAddon align="inline-start">
        <InputGroupText>$</InputGroupText>
      </InputGroupAddon>
      <InputGroupInput type="number" placeholder="0.00" />
    </InputGroup>
  ),
};

// Quantity with unit of measure — number input + autocomplete addon
function QuantityWithUnitField() {
  const [qty, setQty] = useState<number | ''>('');
  const [uom, setUom] = useState('EA');

  return (
    <div className="space-y-2">
      <Label htmlFor="qty-story">
        Quantity <span className="text-danger">*</span>
      </Label>
      <Autocomplete items={UOM_OPTIONS} openOnInputClick value={uom} onValueChange={setUom}>
        <InputGroup className="w-64">
          <InputGroupInput
            id="qty-story"
            type="number"
            value={qty}
            onChange={(e) => setQty(parseInt(e.target.value, 10) || '')}
            placeholder="Qty"
            min="1"
          />
          <span className="bg-border h-4 w-px shrink-0 self-center" />
          <InputGroupAddon align="inline-end" className="px-0">
            <AutocompleteAddon placeholder="Unit" />
          </InputGroupAddon>
        </InputGroup>
        <AutocompleteContent align="end">
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
      <p className="text-fg-muted text-xs">{qty !== '' ? `${qty} ${uom}` : 'Enter a quantity'}</p>
    </div>
  );
}

export const QuantityWithUnit: Story = {
  render: () => <QuantityWithUnitField />,
};

// Disabled state
export const QuantityWithUnitDisabled: Story = {
  render: () => (
    <div className="space-y-2">
      <Label htmlFor="qty-disabled">Quantity</Label>
      <Autocomplete items={UOM_OPTIONS} value="EA">
        <InputGroup className="w-64">
          <InputGroupInput id="qty-disabled" type="number" value={10} placeholder="Qty" min="1" disabled />
          <span className="bg-border h-4 w-px shrink-0 self-center" />
          <InputGroupAddon align="inline-end" className="px-0">
            <AutocompleteAddon placeholder="Unit" disabled />
          </InputGroupAddon>
        </InputGroup>
        <AutocompleteContent align="end">
          <AutocompleteList>
            {(item: string) => (
              <AutocompleteItem key={item} value={item}>
                {item}
              </AutocompleteItem>
            )}
          </AutocompleteList>
        </AutocompleteContent>
      </Autocomplete>
    </div>
  ),
};
