import type { Meta, StoryObj } from '@storybook/react-vite';
import { Button } from './button';
import { toast, ToastProvider } from './toast';

const meta: Meta<typeof ToastProvider> = {
  title: 'UI/Toast',
  component: ToastProvider,
  decorators: [
    (Story) => (
      <ToastProvider>
        <Story />
      </ToastProvider>
    ),
  ],
};

export default meta;
type Story = StoryObj<typeof ToastProvider>;

export const Default: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      <Button
        variant="outline"
        size="sm"
        onClick={() =>
          toast.add({
            title: 'Saved',
            description: 'Your changes have been saved.',
            type: 'success',
          })
        }
      >
        Success
      </Button>
      <Button
        variant="outline"
        size="sm"
        onClick={() =>
          toast.add({
            title: "Couldn't save",
            description: 'Network error. Try again.',
            type: 'error',
          })
        }
      >
        Error
      </Button>
      <Button
        variant="outline"
        size="sm"
        onClick={() =>
          toast.add({
            title: 'Heads up',
            description: 'A new version is available.',
            type: 'info',
          })
        }
      >
        Info
      </Button>
    </div>
  ),
};
