import type { Meta, StoryObj } from '@storybook/react-vite';
import { ChatAvatar, ChatBubble, ChatTypingIndicator } from './chat-bubble';

const meta: Meta = {
  title: 'UI/ChatBubble',
};

export default meta;
type Story = StoryObj;

export const UserMessage: Story = {
  render: () => (
    <div className="flex w-full max-w-md flex-col">
      <ChatBubble role="user">Can you help me update my profile?</ChatBubble>
    </div>
  ),
};

export const AssistantMessage: Story = {
  render: () => (
    <div className="flex w-full max-w-md flex-col">
      <ChatBubble role="assistant">
        Your profile details are up to date. You can change them from your account settings.
      </ChatBubble>
    </div>
  ),
};

export const Conversation: Story = {
  render: () => (
    <div className="flex w-full max-w-md flex-col gap-3">
      <ChatBubble role="user">Can you help me update my profile?</ChatBubble>
      <ChatBubble role="assistant">
        Your profile details are up to date. You can change them from your account settings.
      </ChatBubble>
      <ChatBubble role="user">Thanks, that answers my question.</ChatBubble>
      <ChatBubble role="assistant">You are welcome.</ChatBubble>
    </div>
  ),
};

export const TypingIndicator: Story = {
  render: () => (
    <div className="flex w-full max-w-md flex-col">
      <ChatTypingIndicator />
    </div>
  ),
};

export const TypingIndicatorWithLabel: Story = {
  render: () => (
    <div className="flex w-full max-w-md flex-col">
      <ChatTypingIndicator label="Preparing an answer…" />
    </div>
  ),
};

export const Avatar: Story = {
  render: () => <ChatAvatar />,
};
