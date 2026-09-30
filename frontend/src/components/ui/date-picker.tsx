import { useState } from 'react';
import { parseISO, format } from 'date-fns';
import { CalendarIcon, XIcon } from 'lucide-react';

import { cn } from '@lib/utils';
import { Calendar } from '@ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from '@ui/popover';

const todayDate = new Date(new Date().toISOString().split('T')[0]);

interface DatePickerProps {
  value: string;
  onChange: (iso: string) => void;
  disabled?: boolean;
  minDate?: Date;
  placeholder?: string;
  className?: string;
}

function DatePicker({
  value,
  onChange,
  disabled,
  minDate = todayDate,
  placeholder = 'DD MMM YYYY',
  className,
}: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const selected = value ? parseISO(value) : undefined;
  const display = selected ? format(selected, 'dd MMM yyyy') : undefined;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        disabled={disabled}
        className={cn(
          'border-border focus-visible:outline-accent flex h-8 w-full items-center gap-2 rounded-lg border bg-transparent px-3 py-2 text-sm focus-visible:outline-1 focus-visible:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-50',
          className,
        )}
      >
        <CalendarIcon className="size-4 shrink-0 opacity-50" />
        <span className={cn('flex-1 text-left', !display && 'text-fg-muted')}>{display ?? placeholder}</span>
        {display && (
          <XIcon
            className="size-4 shrink-0 opacity-50 hover:opacity-100"
            onClick={(e) => {
              e.stopPropagation();
              onChange('');
            }}
          />
        )}
      </PopoverTrigger>
      <PopoverContent className="w-auto overflow-hidden p-0">
        <Calendar
          selected={selected}
          onSelect={(date) => {
            onChange(date ? format(date, 'yyyy-MM-dd') : '');
            setOpen(false);
          }}
          disabled={(date) => date < minDate}
        />
      </PopoverContent>
    </Popover>
  );
}

export { DatePicker };
