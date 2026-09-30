import type { Meta, StoryObj } from '@storybook/react-vite';
import { Checkbox } from './checkbox';

const meta: Meta<typeof Checkbox> = {
  title: 'UI/Checkbox',
  component: Checkbox,
  argTypes: {
    disabled: { control: 'boolean' },
  },
};

export default meta;
type Story = StoryObj<typeof Checkbox>;

export const Default: Story = {};

export const Checked: Story = {
  args: { defaultChecked: true },
};

export const Disabled: Story = {
  args: { disabled: true },
};

export const DisabledChecked: Story = {
  args: { disabled: true, defaultChecked: true },
};

export const WithLabel: Story = {
  render: () => (
    <div className="flex items-center gap-2">
      <Checkbox id="terms" />
      <label htmlFor="terms" className="text-sm">
        Accept terms and conditions
      </label>
    </div>
  ),
};

export const Multiple: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2">
        <Checkbox id="email" defaultChecked />
        <label htmlFor="email" className="text-sm">
          Email notifications
        </label>
      </div>
      <div className="flex items-center gap-2">
        <Checkbox id="sms" />
        <label htmlFor="sms" className="text-sm">
          SMS notifications
        </label>
      </div>
      <div className="flex items-center gap-2">
        <Checkbox id="push" disabled />
        <label htmlFor="push" className="text-fg-muted text-sm">
          Push notifications (coming soon)
        </label>
      </div>
    </div>
  ),
};
