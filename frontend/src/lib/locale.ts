const locale = import.meta.env.VITE_APP_LOCALE ?? 'en-SG';

const timestampFormatter = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' });
const dateFormatter = new Intl.DateTimeFormat(locale, { dateStyle: 'medium' });

export function formatTimestamp(isoString: string): string {
  return timestampFormatter.format(new Date(isoString));
}

export function formatDate(isoString: string): string {
  return dateFormatter.format(new Date(isoString));
}

export function formatCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    currencyDisplay: 'code',
    minimumFractionDigits: 2,
  }).format(amount);
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(value)} ${units[unitIndex]}`;
}
