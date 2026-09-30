import type { Meta, StoryObj } from '@storybook/react-vite';
import { AlignLeft, AlignCenter, AlignRight, Bold, Italic, Underline } from 'lucide-react';
import { ToggleGroup, ToggleGroupItem } from './toggle-group';

const meta: Meta = {
  title: 'UI/ToggleGroup',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <ToggleGroup>
      <ToggleGroupItem value="bold" aria-label="Bold">
        <Bold />
      </ToggleGroupItem>
      <ToggleGroupItem value="italic" aria-label="Italic">
        <Italic />
      </ToggleGroupItem>
      <ToggleGroupItem value="underline" aria-label="Underline">
        <Underline />
      </ToggleGroupItem>
    </ToggleGroup>
  ),
};

export const Outline: Story = {
  render: () => (
    <ToggleGroup variant="outline">
      <ToggleGroupItem value="left" aria-label="Align left">
        <AlignLeft />
      </ToggleGroupItem>
      <ToggleGroupItem value="center" aria-label="Align center">
        <AlignCenter />
      </ToggleGroupItem>
      <ToggleGroupItem value="right" aria-label="Align right">
        <AlignRight />
      </ToggleGroupItem>
    </ToggleGroup>
  ),
};

export const NoSpacing: Story = {
  render: () => (
    <ToggleGroup variant="outline" spacing={0}>
      <ToggleGroupItem value="left" aria-label="Align left">
        <AlignLeft />
      </ToggleGroupItem>
      <ToggleGroupItem value="center" aria-label="Align center">
        <AlignCenter />
      </ToggleGroupItem>
      <ToggleGroupItem value="right" aria-label="Align right">
        <AlignRight />
      </ToggleGroupItem>
    </ToggleGroup>
  ),
};

export const Vertical: Story = {
  render: () => (
    <ToggleGroup orientation="vertical">
      <ToggleGroupItem value="bold" aria-label="Bold">
        <Bold /> Bold
      </ToggleGroupItem>
      <ToggleGroupItem value="italic" aria-label="Italic">
        <Italic /> Italic
      </ToggleGroupItem>
      <ToggleGroupItem value="underline" aria-label="Underline">
        <Underline /> Underline
      </ToggleGroupItem>
    </ToggleGroup>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <ToggleGroup size="sm">
        <ToggleGroupItem value="b">
          <Bold />
        </ToggleGroupItem>
        <ToggleGroupItem value="i">
          <Italic />
        </ToggleGroupItem>
      </ToggleGroup>
      <ToggleGroup size="default">
        <ToggleGroupItem value="b">
          <Bold />
        </ToggleGroupItem>
        <ToggleGroupItem value="i">
          <Italic />
        </ToggleGroupItem>
      </ToggleGroup>
      <ToggleGroup size="lg">
        <ToggleGroupItem value="b">
          <Bold />
        </ToggleGroupItem>
        <ToggleGroupItem value="i">
          <Italic />
        </ToggleGroupItem>
      </ToggleGroup>
    </div>
  ),
};
