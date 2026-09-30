import { useState, useEffect } from 'react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { Progress } from './progress';
import { Button } from './button';

const meta: Meta<typeof Progress> = {
  title: 'UI/Progress',
  component: Progress,
  parameters: {
    layout: 'centered',
  },
  decorators: [
    (Story) => (
      <div className="w-80 max-w-full">
        <Story />
      </div>
    ),
  ],
};

export default meta;
type Story = StoryObj<typeof Progress>;

export const Default: Story = {
  args: {
    value: 50,
  },
};

export const Values: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <div className="text-fg-muted flex justify-between text-xs">
          <span>Starting</span>
          <span>15%</span>
        </div>
        <Progress value={15} />
      </div>
      <div className="flex flex-col gap-1">
        <div className="text-fg-muted flex justify-between text-xs">
          <span>In Progress</span>
          <span>60%</span>
        </div>
        <Progress value={60} />
      </div>
      <div className="flex flex-col gap-1">
        <div className="text-fg-muted flex justify-between text-xs">
          <span>Complete</span>
          <span>100%</span>
        </div>
        <Progress value={100} />
      </div>
    </div>
  ),
};

export const Indeterminate: Story = {
  render: () => (
    <div className="flex flex-col gap-2">
      <span className="text-fg-muted text-xs">Loading resources...</span>
      <Progress value={null} />
    </div>
  ),
};

export const InteractiveSimulation: Story = {
  render: () => {
    function SimulatedProgress() {
      const [progress, setProgress] = useState(0);
      const [isRunning, setIsRunning] = useState(false);

      useEffect(() => {
        if (!isRunning) return;
        const timer = setInterval(() => {
          setProgress((prev) => {
            if (prev >= 100) {
              setIsRunning(false);
              return 100;
            }
            return Math.min(prev + 10, 100);
          });
        }, 300);
        return () => clearInterval(timer);
      }, [isRunning]);

      return (
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <span className="text-fg text-xs font-medium">Download Simulation</span>
            <span className="text-fg-muted font-mono text-xs">{progress}%</span>
          </div>
          <Progress value={progress} />
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                setProgress(0);
                setIsRunning(true);
              }}
            >
              Restart Simulation
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setIsRunning(!isRunning)} disabled={progress >= 100}>
              {isRunning ? 'Pause' : 'Resume'}
            </Button>
          </div>
        </div>
      );
    }

    return <SimulatedProgress />;
  },
};
