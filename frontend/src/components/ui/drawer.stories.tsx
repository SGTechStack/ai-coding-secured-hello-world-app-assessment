import type { Meta, StoryObj } from '@storybook/react-vite';
import { Drawer, DrawerTrigger, DrawerPopup, DrawerHeader, DrawerTitle, DrawerContent } from './drawer';
import { Button } from './button';

const meta: Meta = {
  title: 'UI/Drawer',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <Drawer>
      <DrawerTrigger render={<Button />}>Open Drawer</DrawerTrigger>
      <DrawerPopup>
        <DrawerHeader>
          <DrawerTitle>Drawer Title</DrawerTitle>
        </DrawerHeader>
        <DrawerContent>
          <p className="text-fg-muted text-sm">This is the drawer content area. Place any content here.</p>
        </DrawerContent>
      </DrawerPopup>
    </Drawer>
  ),
};

export const WithoutCloseButton: Story = {
  render: () => (
    <Drawer>
      <DrawerTrigger render={<Button variant="outline" />}>Open Without Close Button</DrawerTrigger>
      <DrawerPopup showCloseButton={false}>
        <DrawerHeader>
          <DrawerTitle>No Close Button</DrawerTitle>
        </DrawerHeader>
        <DrawerContent>
          <p className="text-fg-muted text-sm">This drawer has no close button in the corner.</p>
          <Button className="mt-4" variant="outline">
            Dismiss manually
          </Button>
        </DrawerContent>
      </DrawerPopup>
    </Drawer>
  ),
};
