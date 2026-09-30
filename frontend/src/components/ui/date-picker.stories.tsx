import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { DatePicker } from './date-picker';

const meta: Meta = {
  title: 'UI/DatePicker',
};

export default meta;
type Story = StoryObj;

// Empty — no date selected, minDate defaults to today
function DefaultDatePicker() {
  const [value, setValue] = useState('');
  return (
    <div className="w-64 space-y-2">
      <DatePicker value={value} onChange={setValue} />
      <p className="text-fg-muted text-xs">value: "{value}"</p>
    </div>
  );
}

export const Default: Story = {
  render: () => <DefaultDatePicker />,
};

// Pre-filled with a value — clear button visible
function PrefilledDatePicker() {
  const [value, setValue] = useState('2026-09-15');
  return (
    <div className="w-64 space-y-2">
      <DatePicker value={value} onChange={setValue} minDate={new Date(2000, 0, 1)} />
      <p className="text-fg-muted text-xs">value: "{value}"</p>
    </div>
  );
}

export const Prefilled: Story = {
  render: () => <PrefilledDatePicker />,
};

// Custom placeholder text
function CustomPlaceholderDatePicker() {
  const [value, setValue] = useState('');
  return <DatePicker value={value} onChange={setValue} placeholder="Select a date" />;
}

export const CustomPlaceholder: Story = {
  render: () => <CustomPlaceholderDatePicker />,
};

// Disabled
export const Disabled: Story = {
  render: () => <DatePicker value="2026-09-15" onChange={() => {}} disabled />,
};

// No lower bound — past dates selectable
function NoMinDateDatePicker() {
  const [value, setValue] = useState('');
  return <DatePicker value={value} onChange={setValue} minDate={new Date(1970, 0, 1)} />;
}

export const NoMinDate: Story = {
  render: () => <NoMinDateDatePicker />,
};
