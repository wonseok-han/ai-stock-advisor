'use client';

import { type Theme, useTheme } from './theme-provider';

const themes: { value: Theme; label: string; desc: string }[] = [
  { value: 'light', label: 'Light', desc: '밝은 배경' },
  { value: 'dark', label: 'Dark', desc: '어두운 배경' },
  { value: 'brand', label: 'Brand', desc: '브랜드 테마' },
];

const previewColors: Record<Theme, { bg: string; surface: string; accent: string }> = {
  light: { bg: 'bg-white', surface: 'bg-zinc-200', accent: 'bg-emerald-500' },
  dark: { bg: 'bg-zinc-900', surface: 'bg-zinc-700', accent: 'bg-emerald-400' },
  brand: { bg: 'bg-[#0a0a0c]', surface: 'bg-[#151518]', accent: 'bg-emerald-500' },
};

export function ThemeSwitcher() {
  const { theme, setTheme } = useTheme();

  return (
    <div className="grid grid-cols-3 gap-3">
      {themes.map((t) => {
        const active = theme === t.value;
        const colors = previewColors[t.value];
        return (
          <button
            key={t.value}
            onClick={() => setTheme(t.value)}
            className={`cursor-pointer rounded-lg border p-3 transition-colors ${
              active
                ? 'border-primary ring-1 ring-primary'
                : 'border-border hover:border-fg-muted'
            }`}
          >
            <div className={`mx-auto mb-3 h-14 w-full rounded-md ${colors.bg} flex flex-col gap-1.5 p-2 ring-1 ring-inset ring-border`}>
              <div className={`h-1.5 w-3/4 rounded ${colors.surface}`} />
              <div className={`h-1.5 w-1/2 rounded ${colors.surface}`} />
              <div className={`mt-auto h-2 w-2/5 rounded ${colors.accent}`} />
            </div>
            <p className={`text-sm font-medium ${active ? 'text-primary' : 'text-fg'}`}>
              {t.label}
            </p>
            <p className="text-xs text-fg-muted">{t.desc}</p>
          </button>
        );
      })}
    </div>
  );
}
