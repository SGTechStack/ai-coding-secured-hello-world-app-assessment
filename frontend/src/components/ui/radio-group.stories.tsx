import type { Meta, StoryObj } from '@storybook/react-vite';
import { RadioGroup, RadioGroupItem } from './radio-group';
import { Label } from './label';

const meta: Meta<typeof RadioGroup> = {
  title: 'UI/RadioGroup',
  component: RadioGroup,
};

export default meta;
type Story = StoryObj<typeof RadioGroup>;

export const Default: Story = {
  args: { defaultValue: 'medium' },
  render: (args) => (
    <RadioGroup {...args}>
      <div className="flex items-center gap-3">
        <RadioGroupItem id="critical" value="critical" />
        <Label htmlFor="critical">Critical</Label>
      </div>
      <div className="flex items-center gap-3">
        <RadioGroupItem id="high" value="high" />
        <Label htmlFor="high">High</Label>
      </div>
      <div className="flex items-center gap-3">
        <RadioGroupItem id="medium" value="medium" />
        <Label htmlFor="medium">Medium</Label>
      </div>
    </RadioGroup>
  ),
};
