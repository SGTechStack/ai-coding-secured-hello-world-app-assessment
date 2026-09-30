import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { Slider } from './slider';

const meta: Meta<typeof Slider> = {
  title: 'UI/Slider',
  component: Slider,
};

export default meta;
type Story = StoryObj<typeof Slider>;

export const Default: Story = {
  render: () => (
    <div className="w-64">
      <Slider defaultValue={50} min={0} max={100} />
    </div>
  ),
};

export const Stepped: Story = {
  render: () => (
    <div className="w-64">
      <Slider defaultValue={20} min={0} max={100} step={10} />
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="w-64">
      <Slider defaultValue={40} min={0} max={100} disabled />
    </div>
  ),
};

export const Controlled: Story = {
  render: () => {
    const [value, setValue] = useState(30);
    return (
      <div className="flex w-64 flex-col gap-2">
        <Slider value={value} min={0} max={100} onValueChange={(next) => setValue(next as number)} />
        <span className="text-fg-muted text-sm">{value}</span>
      </div>
    );
  },
};
