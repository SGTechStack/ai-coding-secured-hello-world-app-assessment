import type { Meta, StoryObj } from '@storybook/react-vite';
import type { ColumnDef } from '@tanstack/react-table';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { Button } from './button';
import { DataTable } from './data-table';

interface Invoice {
  id: string;
  status: string;
  method: string;
  amount: string;
}

const invoices: Invoice[] = [
  { id: 'INV001', status: 'Paid', method: 'Credit Card', amount: '$250.00' },
  { id: 'INV002', status: 'Pending', method: 'PayPal', amount: '$150.00' },
  { id: 'INV003', status: 'Unpaid', method: 'Bank Transfer', amount: '$350.00' },
  { id: 'INV004', status: 'Paid', method: 'Credit Card', amount: '$450.00' },
];

const columns: ColumnDef<Invoice>[] = [
  { accessorKey: 'id', header: 'Invoice' },
  { accessorKey: 'status', header: 'Status' },
  { accessorKey: 'method', header: 'Method' },
  { accessorKey: 'amount', header: 'Amount' },
];

const meta: Meta = {
  title: 'UI/DataTable',
};

export default meta;
type Story = StoryObj;

// Basic table with a "Columns" dropdown to toggle visibility
export const Default: Story = {
  render: () => <DataTable columns={columns} data={invoices} />,
};

export const Empty: Story = {
  render: () => <DataTable columns={columns} data={[]} />,
};

// Expandable rows — clicking the chevron reveals a sub-component per row
const expandableColumns: ColumnDef<Invoice>[] = [
  {
    id: 'expander',
    header: () => null,
    cell: ({ row }) => (
      <Button variant="ghost" size="icon" className="size-6" onClick={row.getToggleExpandedHandler()}>
        {row.getIsExpanded() ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
      </Button>
    ),
  },
  ...columns,
];

export const ExpandableRows: Story = {
  render: () => (
    <DataTable
      columns={expandableColumns}
      data={invoices}
      renderSubComponent={({ row }) => (
        <div className="text-fg-muted text-sm">
          Full details for invoice <span className="text-fg font-medium">{row.original.id}</span> — billed via{' '}
          {row.original.method}.
        </div>
      )}
    />
  ),
};
