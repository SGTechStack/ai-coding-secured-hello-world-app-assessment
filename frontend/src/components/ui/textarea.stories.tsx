import type { Meta, StoryObj } from '@storybook/react-vite';
import { Textarea } from './textarea';

const meta: Meta<typeof Textarea> = {
  title: 'UI/Textarea',
  component: Textarea,
  argTypes: {
    disabled: { control: 'boolean' },
    placeholder: { control: 'text' },
    rows: { control: 'number' },
  },
};

export default meta;
type Story = StoryObj<typeof Textarea>;

export const Default: Story = {
  args: { placeholder: 'Type your message here...' },
};

export const WithValue: Story = {
  args: { defaultValue: 'This textarea already has some content in it.' },
};

export const Disabled: Story = {
  args: { placeholder: 'Disabled', disabled: true },
};

export const TallRows: Story = {
  args: { placeholder: 'A taller textarea...', rows: 8 },
};
