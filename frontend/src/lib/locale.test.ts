import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { formatTimestamp, formatDate, formatCurrency } from './locale';

describe('formatTimestamp', () => {
  it('formats an ISO string as medium date + short time in en-SG', () => {
    // 2024-03-15T09:05:00.000Z is 2024-03-15 17:05 SGT (UTC+8)
    const result = formatTimestamp('2024-03-15T09:05:00.000Z');
    expect(result).toMatch(/15 Mar 2024/);
    expect(result).toMatch(/\d{1,2}:\d{2}/);
  });

  it('includes both date and time components', () => {
    const result = formatTimestamp('2024-06-01T00:00:00.000Z');
    expect(result).toMatch(/\d{1,2}:\d{2}/);
    expect(result).toMatch(/Jun 2024/);
  });
});

describe('formatDate', () => {
  it('formats an ISO string as medium date only in en-SG', () => {
    const result = formatDate('2024-03-15T00:00:00.000Z');
    expect(result).toMatch(/15 Mar 2024/);
  });

  it('does not include a time component', () => {
    const result = formatDate('2024-03-15T09:05:00.000Z');
    expect(result).not.toMatch(/\d{1,2}:\d{2}/);
  });

  it('formats the last day of the year correctly', () => {
    const result = formatDate('2024-12-31T00:00:00.000Z');
    expect(result).toMatch(/Dec 2024/);
  });
});

describe('formatCurrency', () => {
  it('formats SGD amounts with currency symbol', () => {
    const result = formatCurrency(1234.5, 'SGD');
    expect(result).toMatch(/1,234\.50/);
    expect(result).toMatch(/SGD/);
  });

  it('formats USD amounts with currency symbol', () => {
    const result = formatCurrency(999, 'USD');
    expect(result).toMatch(/999\.00/);
    expect(result).toMatch(/USD/);
  });

  it('always shows two decimal places', () => {
    expect(formatCurrency(10, 'SGD')).toMatch(/10\.00/);
    expect(formatCurrency(10.1, 'SGD')).toMatch(/10\.10/);
    expect(formatCurrency(10.123, 'SGD')).toMatch(/10\.12/);
  });

  it('formats zero correctly', () => {
    expect(formatCurrency(0, 'SGD')).toMatch(/0\.00/);
  });

  it('formats negative amounts correctly', () => {
    const result = formatCurrency(-50.5, 'SGD');
    expect(result).toMatch(/50\.50/);
    expect(result).toMatch(/SGD/);
  });
});

describe('VITE_APP_LOCALE env override', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('uses the locale from VITE_APP_LOCALE when set', async () => {
    vi.stubEnv('VITE_APP_LOCALE', 'en-US');
    const { formatDate: formatDateUs } = await import('./locale');
    // en-US medium date style: "Mar 15, 2024"
    const result = formatDateUs('2024-03-15T00:00:00.000Z');
    expect(result).toMatch(/Mar/);
    expect(result).toMatch(/2024/);
  });

  it('defaults to en-SG when VITE_APP_LOCALE is not set', async () => {
    const { formatDate: formatDateSg } = await import('./locale');
    // en-SG medium date style: "15 Mar 2024"
    const result = formatDateSg('2024-03-15T00:00:00.000Z');
    expect(result).toMatch(/15 Mar 2024/);
  });
});
