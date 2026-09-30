'use client';

import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useState } from 'react';

const DAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

function startOfMonth(year: number, month: number) {
  return new Date(year, month, 1).getDay();
}

function daysInMonth(year: number, month: number) {
  return new Date(year, month + 1, 0).getDate();
}

function isSameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

export interface CalendarProps {
  className?: string;
  /** Controlled selected date. Pair with `onSelect`. */
  selected?: Date;
  /** Initial selection in uncontrolled mode. */
  defaultSelected?: Date;
  onSelect?: (date: Date) => void;
  disabled?: (date: Date) => boolean;
  defaultMonth?: Date;
}

export function Calendar({ className, selected, defaultSelected, onSelect, disabled, defaultMonth }: CalendarProps) {
  const today = new Date();
  const isControlled = selected !== undefined;
  const [internalSelected, setInternalSelected] = useState<Date | undefined>(defaultSelected);
  const activeSelected = isControlled ? selected : internalSelected;
  const [viewDate, setViewDate] = useState(defaultMonth ?? activeSelected ?? today);

  const year = viewDate.getFullYear();
  const month = viewDate.getMonth();
  const firstDay = startOfMonth(year, month);
  const totalDays = daysInMonth(year, month);

  const prevMonth = () => setViewDate(new Date(year, month - 1, 1));
  const nextMonth = () => setViewDate(new Date(year, month + 1, 1));

  const cells: (number | null)[] = [
    ...Array(firstDay).fill(null),
    ...Array.from({ length: totalDays }, (_, i) => i + 1),
  ];

  // Pad to full rows
  while (cells.length % 7 !== 0) cells.push(null);

  return (
    <div className={cn('w-fit p-3 select-none', className)}>
      {/* Header */}
      <div className="mb-2 flex items-center justify-between">
        <button
          type="button"
          onClick={prevMonth}
          className={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'h-7 w-7')}
          aria-label="Previous month"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        <span className="text-fg text-sm font-medium">
          {MONTHS[month]} {year}
        </span>
        <button
          type="button"
          onClick={nextMonth}
          className={cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'h-7 w-7')}
          aria-label="Next month"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>

      {/* Day names */}
      <div className="mb-1 grid grid-cols-7">
        {DAYS.map((d) => (
          <div key={d} className="text-fg-muted flex h-8 items-center justify-center text-xs font-medium">
            {d}
          </div>
        ))}
      </div>

      {/* Day cells */}
      <div className="grid grid-cols-7 gap-y-0.5">
        {cells.map((day, idx) => {
          if (day === null) return <div key={`empty-${idx}`} />;
          const date = new Date(year, month, day);
          const isToday = isSameDay(date, today);
          const isSelected = activeSelected ? isSameDay(date, activeSelected) : false;
          const isDisabled = disabled?.(date) ?? false;

          const handleClick = () => {
            if (!isControlled) setInternalSelected(date);
            onSelect?.(date);
          };

          return (
            <button
              key={day}
              type="button"
              disabled={isDisabled}
              onClick={handleClick}
              aria-selected={isSelected}
              aria-label={date.toLocaleDateString()}
              className={cn(
                'flex h-8 w-8 items-center justify-center rounded-md text-sm transition-colors',
                'focus-visible:outline-accent focus-visible:outline-1 focus-visible:outline-offset-0',
                'disabled:pointer-events-none disabled:opacity-30',
                isSelected && 'bg-accent text-accent-fg font-semibold',
                !isSelected && isToday && 'border-accent text-accent border font-semibold',
                !isSelected && !isToday && 'text-fg hover:bg-bg-muted',
              )}
            >
              {day}
            </button>
          );
        })}
      </div>
    </div>
  );
}
