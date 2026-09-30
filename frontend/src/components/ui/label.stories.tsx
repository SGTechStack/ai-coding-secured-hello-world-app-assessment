import type { Meta, StoryObj } from '@storybook/react-vite';
import { Label } from './label';

const meta: Meta<typeof Label> = {
  title: 'UI/Label',
  component: Label,
};

export default meta;
type Story = StoryObj<typeof Label>;

export const Default: Story = {
  args: { children: 'Email address' },
};

export const WithInput: Story = {
  render: () => (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor="email">Email address</Label>
      <input id="email" className="rounded border px-3 py-1.5 text-sm" placeholder="you@example.com" />
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="group flex flex-col gap-1.5" data-disabled="true">
      <Label htmlFor="disabled-input">Disabled field</Label>
      <input
        id="disabled-input"
        className="rounded border px-3 py-1.5 text-sm opacity-50"
        placeholder="Cannot edit"
        disabled
      />
    </div>
  ),
};
