import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Progress } from './progress';

describe('Prizm Progress Component', () => {
  it('renders determinate progress bar with accessibility attributes', () => {
    render(<Progress value={45} max={100} aria-label="Loading document" />);

    const progressbar = screen.getByRole('progressbar', { name: /Loading document/i });
    expect(progressbar).toBeInTheDocument();
    expect(progressbar).toHaveAttribute('aria-valuenow', '45');
    expect(progressbar).toHaveAttribute('aria-valuemax', '100');
    expect(progressbar).toHaveAttribute('aria-valuemin', '0');
  });

  it('renders indeterminate progress bar when value is null', () => {
    render(<Progress value={null} aria-label="Loading engine" />);

    const progressbar = screen.getByRole('progressbar', { name: /Loading engine/i });
    expect(progressbar).toBeInTheDocument();
    expect(progressbar).not.toHaveAttribute('aria-valuenow');
    expect(progressbar).toHaveAttribute('data-indeterminate');
  });

  it('applies custom className to progress root', () => {
    const { container } = render(<Progress value={80} className="custom-progress w-64" />);

    const root = container.firstChild as HTMLElement;
    expect(root).toHaveClass('custom-progress', 'w-64');
  });
});
