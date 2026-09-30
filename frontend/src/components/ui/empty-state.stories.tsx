import type { Meta, StoryObj } from '@storybook/react-vite';
import { Inbox, SearchX } from 'lucide-react';
import { Button } from './button';
import { EmptyState } from './empty-state';

const meta: Meta<typeof EmptyState> = {
  title: 'UI/EmptyState',
  component: EmptyState,
};

export default meta;
type Story = StoryObj<typeof EmptyState>;

export const Default: Story = {
  args: { title: 'No results found' },
};

export const WithDescription: Story = {
  args: {
    title: 'No items yet',
    description: 'Items you add to this list will show up here.',
  },
};

export const WithIcon: Story = {
  render: () => (
    <EmptyState icon={<Inbox className="h-6 w-6" />} title="Nothing to show" description="Your inbox is empty." />
  ),
};

export const WithAction: Story = {
  render: () => (
    <EmptyState
      icon={<SearchX className="h-6 w-6" />}
      title="No matching users found"
      description="Try adjusting your filters or search terms."
      action={<Button variant="outline">Clear filters</Button>}
    />
  ),
};
