import type { Meta, StoryObj } from '@storybook/react-vite';
import { Tabs, TabsList, TabsTrigger, TabsContent } from './tabs';

const meta: Meta = {
  title: 'UI/Tabs',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <Tabs defaultValue="account">
      <TabsList>
        <TabsTrigger value="account">Account</TabsTrigger>
        <TabsTrigger value="password">Password</TabsTrigger>
        <TabsTrigger value="settings">Settings</TabsTrigger>
      </TabsList>
      <TabsContent value="account">
        <p>Account settings content goes here.</p>
      </TabsContent>
      <TabsContent value="password">
        <p>Password settings content goes here.</p>
      </TabsContent>
      <TabsContent value="settings">
        <p>General settings content goes here.</p>
      </TabsContent>
    </Tabs>
  ),
};
