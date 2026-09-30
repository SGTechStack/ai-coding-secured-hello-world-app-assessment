import { useEffect } from 'react';
import type { Preview } from '@storybook/react-vite';
import { initialize, mswLoader } from 'msw-storybook-addon';
import '../src/index.css';

initialize();

const preview: Preview = {
  loaders: [mswLoader],
  globalTypes: {
    theme: {
      description: 'Toggle dark mode',
      toolbar: {
        title: 'Theme',
        icon: 'mirror',
        items: [
          { value: 'light', title: 'Light' },
          { value: 'dark', title: 'Dark' },
        ],
        dynamicTitle: true,
      },
    },
  },
  initialGlobals: {
    theme: 'light',
  },
  decorators: [
    (Story, context) => {
      const theme = context.globals.theme;
      useEffect(() => {
        document.documentElement.dataset.mode = theme;
        document.documentElement.dataset.zone = 'enterprise';
      }, [theme]);
      return (
        <div className="bg-bg text-fg p-4">
          <Story />
        </div>
      );
    },
  ],
  parameters: {
    options: {
      storySort: {
        method: 'alphabetical',
      },
    },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
};

export default preview;
