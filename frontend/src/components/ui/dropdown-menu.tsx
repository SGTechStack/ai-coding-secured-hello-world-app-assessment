import { Menu } from '@base-ui/react/menu';
import { cn } from '@lib/utils';
import { Check } from 'lucide-react';

function DropdownMenu({ children, ...props }: Menu.Root.Props) {
  return <Menu.Root {...props}>{children}</Menu.Root>;
}

function DropdownMenuTrigger({ children, className, asChild, ...props }: Menu.Trigger.Props & { asChild?: boolean }) {
  if (asChild) {
    return (
      <Menu.Trigger render={children as React.ReactElement} className={cn('outline-none', className)} {...props} />
    );
  }
  return (
    <Menu.Trigger className={cn('outline-none', className)} {...props}>
      {children}
    </Menu.Trigger>
  );
}

function DropdownMenuContent({ className, children, ...props }: Menu.Positioner.Props) {
  return (
    <Menu.Portal>
      <Menu.Positioner side="bottom" align="end" sideOffset={8} className="z-50" {...props}>
        <Menu.Popup
          className={cn(
            'bg-surface-elevated text-fg min-w-40 overflow-hidden rounded-md border p-1 shadow-md',
            'transition-opacity duration-100 data-ending-style:opacity-0 data-starting-style:opacity-0',
            className,
          )}
        >
          {children}
        </Menu.Popup>
      </Menu.Positioner>
    </Menu.Portal>
  );
}

function DropdownMenuLabel({ className, ...props }: { className?: string; children?: React.ReactNode }) {
  return <div className={cn('px-2 py-1.5 text-sm font-semibold', className)} {...props} />;
}

function DropdownMenuSeparator({ className, ...props }: Menu.Separator.Props) {
  return <Menu.Separator className={cn('bg-border -mx-1 my-1 h-px', className)} {...props} />;
}

function DropdownMenuItem({ className, ...props }: Menu.Item.Props) {
  return (
    <Menu.Item
      className={cn(
        'relative flex cursor-default items-center gap-2 rounded-sm px-2 py-1.5 text-sm transition-colors outline-none select-none',
        'focus:bg-accent focus:text-accent-fg data-disabled:pointer-events-none data-disabled:opacity-50',
        '[&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*=size-])]:size-4',
        className,
      )}
      {...props}
    />
  );
}

function DropdownMenuCheckboxItem({
  className,
  children,
  checked,
  onCheckedChange,
  ...props
}: Menu.CheckboxItem.Props & {
  checked?: boolean;
  onCheckedChange?: (checked: boolean) => void;
}) {
  const handleClick: NonNullable<Menu.CheckboxItem.Props['onClick']> = (e) => {
    e.preventDefault();
    e.stopPropagation();
    onCheckedChange?.(!checked);
  };

  return (
    <Menu.CheckboxItem
      checked={checked}
      onClick={handleClick}
      className={cn(
        'relative flex cursor-default items-center gap-2 rounded-sm py-1.5 pr-2 pl-8 text-sm transition-colors outline-none select-none',
        'focus:bg-accent focus:text-accent-fg data-disabled:pointer-events-none data-disabled:opacity-50',
        className,
      )}
      {...props}
    >
      <span className="absolute left-2 flex size-3.5 items-center justify-center">
        <Menu.CheckboxItemIndicator>
          <Check className="size-4" />
        </Menu.CheckboxItemIndicator>
      </span>
      {children}
    </Menu.CheckboxItem>
  );
}

export {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuItem,
  DropdownMenuCheckboxItem,
};
