import { Geist, Geist_Mono } from 'next/font/google';

import { Providers } from '@/app/providers';

import type { Metadata } from 'next';

import './globals.css';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  title: 'AI Stock Advisor',
  description: '초보 투자자를 위한 미국 주식 참고/분석 도구 (투자 자문 아님).',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="ko"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col bg-zinc-50 dark:bg-black">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
