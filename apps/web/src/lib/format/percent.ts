const pct = new Intl.NumberFormat('en-US', {
  style: 'percent',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
});

const num2 = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
});

/** percentChange 가 이미 % 단위(예: 1.23 → +1.23%)로 오기 때문에 /100. */
export function formatPercentChange(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return pct.format(value / 100);
}

export function formatSignedNumber(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return num2.format(value);
}
