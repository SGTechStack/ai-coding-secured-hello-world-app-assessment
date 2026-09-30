import type { Meta, StoryObj } from '@storybook/react-vite';
import { AlertCircle, CheckCircle, Info as InfoIcon, TriangleAlert } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from './alert';

const meta: Meta<typeof Alert> = {
  title: 'UI/Alert',
  component: Alert,
  argTypes: {
    variant: {
      control: 'select',
      options: ['default', 'info', 'success', 'warning', 'danger'],
    },
  },
};

export default meta;
type Story = StoryObj<typeof Alert>;

export const Default: Story = {
  render: () => (
    <Alert className="w-96">
      <AlertTitle>Heads up</AlertTitle>
      <AlertDescription>This is a default alert with no semantic color.</AlertDescription>
    </Alert>
  ),
};

export const Info: Story = {
  render: () => (
    <Alert variant="info" className="w-96">
      <InfoIcon />
      <AlertTitle>Info</AlertTitle>
      <AlertDescription>A new version of this page is available.</AlertDescription>
    </Alert>
  ),
};

export const Success: Story = {
  render: () => (
    <Alert variant="success" className="w-96">
      <CheckCircle />
      <AlertTitle>Success</AlertTitle>
      <AlertDescription>Your changes have been saved.</AlertDescription>
    </Alert>
  ),
};

export const Warning: Story = {
  render: () => (
    <Alert variant="warning" className="w-96">
      <TriangleAlert />
      <AlertTitle>Warning</AlertTitle>
      <AlertDescription>Your session will expire soon. Save your changes.</AlertDescription>
    </Alert>
  ),
};

export const Danger: Story = {
  render: () => (
    <Alert variant="danger" className="w-96">
      <AlertCircle />
      <AlertTitle>Error</AlertTitle>
      <AlertDescription>Something went wrong. Please try again.</AlertDescription>
    </Alert>
  ),
};

export const DescriptionOnly: Story = {
  render: () => (
    <Alert variant="info" className="w-96">
      <InfoIcon />
      <AlertDescription>Alerts don&apos;t require a title — description alone also works.</AlertDescription>
    </Alert>
  ),
};

export const AllVariants: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <Alert className="w-96">
        <AlertTitle>Default</AlertTitle>
        <AlertDescription>No semantic color, no icon.</AlertDescription>
      </Alert>
      <Alert variant="info" className="w-96">
        <InfoIcon />
        <AlertTitle>Info</AlertTitle>
        <AlertDescription>A new version of this page is available.</AlertDescription>
      </Alert>
      <Alert variant="success" className="w-96">
        <CheckCircle />
        <AlertTitle>Success</AlertTitle>
        <AlertDescription>Your changes have been saved.</AlertDescription>
      </Alert>
      <Alert variant="warning" className="w-96">
        <TriangleAlert />
        <AlertTitle>Warning</AlertTitle>
        <AlertDescription>Double check before proceeding.</AlertDescription>
      </Alert>
      <Alert variant="danger" className="w-96">
        <AlertCircle />
        <AlertTitle>Error</AlertTitle>
        <AlertDescription>Something went wrong. Please try again.</AlertDescription>
      </Alert>
    </div>
  ),
};
