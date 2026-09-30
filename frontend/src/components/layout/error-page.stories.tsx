import type { Meta, StoryObj } from '@storybook/react-vite';
import { ErrorPage } from './error-page';

const meta: Meta<typeof ErrorPage> = {
  title: 'Layout/ErrorPage',
  component: ErrorPage,
  parameters: {
    layout: 'fullscreen',
  },
  args: {
    reset: () => {},
  },
};

export default meta;
type Story = StoryObj<typeof ErrorPage>;

export const Default: Story = {};
