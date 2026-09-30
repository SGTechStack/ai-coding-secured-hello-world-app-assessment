import type { Meta, StoryObj } from '@storybook/react-vite';
import { Text } from './text';

const meta: Meta<typeof Text> = {
  title: 'UI/Text',
  component: Text,
  argTypes: {
    size: {
      control: 'select',
      options: ['xs', 'sm', 'md', 'lg'],
    },
    variant: {
      control: 'select',
      options: ['default', 'muted', 'subtle'],
    },
    weight: {
      control: 'select',
      options: ['normal', 'medium', 'semibold'],
    },
    as: {
      control: 'select',
      options: ['p', 'span', 'div'],
    },
  },
};

export default meta;
type Story = StoryObj<typeof Text>;

export const Default: Story = {
  args: { children: 'Default body text at base size.' },
};

export const Muted: Story = {
  args: { children: 'Muted small text for secondary info.', variant: 'muted', size: 'sm' },
};

export const Subtle: Story = {
  args: { children: 'Subtle text.', variant: 'subtle' },
};

export const Semibold: Story = {
  args: { children: 'Semibold emphasis.', weight: 'semibold' },
};

export const AllSizes: Story = {
  render: () => (
    <div className="space-y-2">
      <Text size="xs">Extra small text</Text>
      <Text size="sm">Small text</Text>
      <Text size="md">Medium text</Text>
      <Text size="lg">Large text</Text>
    </div>
  ),
};

export const AllVariants: Story = {
  render: () => (
    <div className="space-y-2">
      <Text variant="default">Default text</Text>
      <Text variant="muted">Muted text</Text>
      <Text variant="subtle">Subtle text</Text>
    </div>
  ),
};

export const Example: Story = {
  render: () => (
    <div className="space-y-2">
      <Text>Default body text at base size.</Text>
      <Text variant="muted" size="sm">
        Muted small text for secondary info.
      </Text>
      <Text weight="semibold">Semibold emphasis.</Text>
    </div>
  ),
};
