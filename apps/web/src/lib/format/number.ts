const compact = new Intl.NumberFormat('en-US', {
  notation: 'compact',
  maximumFractionDigits: 1,
});

export function formatCompact(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return compact.format(value);
}

export function formatMarketCap(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  if (value >= 1e12) return `$${(value / 1e12).toFixed(2)}T`;
  if (value >= 1e9) return `$${(value / 1e9).toFixed(2)}B`;
  if (value >= 1e6) return `$${(value / 1e6).toFixed(2)}M`;
  return `$${compact.format(value)}`;
}

const ratioFmt = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatRatio(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return ratioFmt.format(value);
}

const employeeFmt = new Intl.NumberFormat('en-US');

export function formatEmployees(value: number | null | undefined): string {
  if (value == null) return '—';
  return employeeFmt.format(value);
}
