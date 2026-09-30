import type { Meta, StoryObj } from '@storybook/react-vite';
import { Bold, Italic, Underline } from 'lucide-react';
import { Toggle } from './toggle';

const meta: Meta<typeof Toggle> = {
  title: 'UI/Toggle',
  component: Toggle,
  argTypes: {
    variant: {
      control: 'select',
      options: ['default', 'outline'],
    },
    size: {
      control: 'select',
      options: ['default', 'sm', 'lg'],
    },
    disabled: { control: 'boolean' },
  },
};

export default meta;
type Story = StoryObj<typeof Toggle>;

export const Default: Story = {
  args: { children: 'Bold', pressed: false },
};

export const Outline: Story = {
  args: { children: 'Toggle', variant: 'outline' },
};

export const WithIcon: Story = {
  render: () => (
    <Toggle aria-label="Bold">
      <Bold />
    </Toggle>
  ),
};

export const Disabled: Story = {
  args: { children: 'Disabled', disabled: true },
};

export const AllVariants: Story = {
  render: () => (
    <div className="flex flex-wrap gap-3">
      <Toggle variant="default">Default</Toggle>
      <Toggle variant="outline">Outline</Toggle>
    </div>
  ),
};

export const AllSizes: Story = {
  render: () => (
    <div className="flex flex-wrap items-center gap-3">
      <Toggle size="sm">
        <Bold /> Small
      </Toggle>
      <Toggle size="default">
        <Italic /> Default
      </Toggle>
      <Toggle size="lg">
        <Underline /> Large
      </Toggle>
    </div>
  ),
};
