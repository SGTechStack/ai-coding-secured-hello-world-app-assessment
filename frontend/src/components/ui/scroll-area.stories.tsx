import type { Meta, StoryObj } from '@storybook/react-vite';
import { ScrollArea } from './scroll-area';

const meta: Meta = {
  title: 'UI/ScrollArea',
};

export default meta;
type Story = StoryObj;

const TAGS = Array.from({ length: 30 }, (_, i) => `Tag ${i + 1}`);

export const Vertical: Story = {
  render: () => (
    <ScrollArea className="border-border h-72 w-56 rounded-lg border p-4">
      <div className="space-y-2">
        {TAGS.map((tag) => (
          <div key={tag} className="text-fg text-sm">
            {tag}
          </div>
        ))}
      </div>
    </ScrollArea>
  ),
};

export const Horizontal: Story = {
  render: () => (
    <ScrollArea className="border-border w-96 rounded-lg border p-4">
      <div className="flex w-max gap-3 pb-3">
        {TAGS.slice(0, 12).map((tag) => (
          <div
            key={tag}
            className="bg-bg-muted text-fg flex h-16 w-24 shrink-0 items-center justify-center rounded-md text-sm"
          >
            {tag}
          </div>
        ))}
      </div>
    </ScrollArea>
  ),
};
