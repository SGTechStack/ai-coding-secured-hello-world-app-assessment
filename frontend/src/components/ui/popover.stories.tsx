import type { Meta, StoryObj } from '@storybook/react-vite';
import {
  Popover,
  PopoverTrigger,
  PopoverClose,
  PopoverContent,
  PopoverHeader,
  PopoverTitle,
  PopoverDescription,
} from './popover';
import { Button } from './button';

const meta: Meta = {
  title: 'UI/Popover',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <Popover>
      <PopoverTrigger render={<Button variant="outline" />}>Open popover</PopoverTrigger>
      <PopoverContent>
        <PopoverHeader>
          <PopoverTitle>Popover title</PopoverTitle>
          <PopoverDescription>A short description of what this popover shows.</PopoverDescription>
        </PopoverHeader>
        <p className="text-fg text-sm">Additional content goes here.</p>
      </PopoverContent>
    </Popover>
  ),
};

export const WithCloseButton: Story = {
  render: () => (
    <Popover>
      <PopoverTrigger render={<Button variant="outline" />}>Open popover</PopoverTrigger>
      <PopoverContent showCloseButton>
        <PopoverHeader>
          <PopoverTitle>Dismissable popover</PopoverTitle>
          <PopoverDescription>Click the X or click outside to close.</PopoverDescription>
        </PopoverHeader>
      </PopoverContent>
    </Popover>
  ),
};

export const Glass: Story = {
  render: () => (
    <Popover>
      <PopoverTrigger render={<Button variant="outline" />}>Open glass popover</PopoverTrigger>
      <PopoverContent variant="glass">
        <PopoverHeader>
          <PopoverTitle>Glass variant</PopoverTitle>
          <PopoverDescription>Uses the liquid-glass surface style.</PopoverDescription>
        </PopoverHeader>
      </PopoverContent>
    </Popover>
  ),
};

export const WithFooterActions: Story = {
  render: () => (
    <Popover>
      <PopoverTrigger render={<Button variant="outline" />}>Delete item</PopoverTrigger>
      <PopoverContent>
        <PopoverHeader>
          <PopoverTitle>Are you sure?</PopoverTitle>
          <PopoverDescription>This action cannot be undone.</PopoverDescription>
        </PopoverHeader>
        <div className="mt-3 flex justify-end gap-2">
          <PopoverClose render={<Button variant="outline" size="sm" />}>Cancel</PopoverClose>
          <PopoverClose render={<Button variant="danger" size="sm" />}>Delete</PopoverClose>
        </div>
      </PopoverContent>
    </Popover>
  ),
};
