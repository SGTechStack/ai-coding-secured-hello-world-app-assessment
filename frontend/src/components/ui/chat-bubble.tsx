import type * as React from 'react';
import { Sparkles } from 'lucide-react';
import { cva, type VariantProps } from 'class-variance-authority';

import { cn } from '@lib/utils';

export type ChatRole = 'user' | 'assistant';

const chatBubbleVariants = cva('max-w-[85%] rounded-xl px-3 py-2 text-sm whitespace-pre-wrap', {
  variants: {
    role: {
      user: 'bg-accent text-accent-fg self-end rounded-br-xs',
      assistant: 'bg-bg-muted rounded-bl-xs whitespace-normal',
    },
  },
  defaultVariants: {
    role: 'assistant',
  },
});

/** Circular AI avatar shown beside assistant bubbles. */
function ChatAvatar({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'bg-bg-muted text-fg-muted flex size-7 shrink-0 items-center justify-center rounded-full',
        className,
      )}
    >
      <Sparkles className="size-4" />
    </span>
  );
}

/**
 * A single chat message bubble. Assistant bubbles are prefixed with an AI avatar and align
 * left; user bubbles align right. Pass any content as children (streamed text, typing dots, …).
 */
function ChatBubble({
  role,
  className,
  children,
  ...props
}: React.ComponentProps<'div'> & VariantProps<typeof chatBubbleVariants>) {
  if (role === 'user') {
    return (
      <div className={cn(chatBubbleVariants({ role }), className)} {...props}>
        {children}
      </div>
    );
  }

  return (
    <div className="flex max-w-[85%] items-start gap-2 self-start">
      <ChatAvatar />
      <div className={cn(chatBubbleVariants({ role }), 'max-w-full', className)} {...props}>
        {children}
      </div>
    </div>
  );
}

/**
 * Bouncing dots shown in an assistant bubble while awaiting the first reply chunk. An optional
 * `label` (the agent's current progress step, e.g. "Parsing query…") renders beside the dots so
 * the pre-reply wait shows what the agent is doing rather than a silent indicator.
 */
function ChatTypingIndicator({ label }: { label?: string }) {
  return (
    <ChatBubble role="assistant" className="flex items-center gap-2">
      {/* Keyed on the label so each new step remounts and replays the enter animation, giving a
          subtle fade/slide as the agent's progress advances. Falls back to a generic waiting line
          before the first status arrives. */}
      <span
        key={label ?? 'thinking'}
        className="text-fg-muted animate-in fade-in slide-in-from-bottom-1 text-xs duration-300"
      >
        {label ?? 'Thinking…'}
      </span>
    </ChatBubble>
  );
}

export { ChatBubble, ChatAvatar, ChatTypingIndicator, chatBubbleVariants };
