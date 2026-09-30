import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { Calendar } from './calendar';
import { Popover, PopoverTrigger, PopoverContent } from './popover';
import { Button } from './button';

const meta: Meta<typeof Calendar> = {
  title: 'UI/Calendar',
  component: Calendar,
};

export default meta;
type Story = StoryObj<typeof Calendar>;

export const Default: Story = {};

export const WithDefaultSelected: Story = {
  args: { defaultSelected: new Date() },
};

export const Controlled: Story = {
  render: () => {
    const [date, setDate] = useState<Date | undefined>(new Date());
    return (
      <div className="flex flex-col items-center gap-2">
        <Calendar selected={date} onSelect={setDate} />
        <span className="text-fg-muted text-sm">{date ? date.toLocaleDateString() : 'No date selected'}</span>
      </div>
    );
  },
};

export const DisabledWeekends: Story = {
  args: {
    disabled: (date: Date) => date.getDay() === 0 || date.getDay() === 6,
  },
};

export const InPopover: Story = {
  render: () => {
    const [date, setDate] = useState<Date | undefined>();
    return (
      <Popover>
        <PopoverTrigger render={<Button variant="outline" />}>
          {date ? date.toLocaleDateString() : 'Pick a date'}
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0">
          <Calendar selected={date} onSelect={setDate} />
        </PopoverContent>
      </Popover>
    );
  },
};
