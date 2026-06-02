import { Analytics } from '@vercel/analytics/next';
import { Geist, Geist_Mono } from 'next/font/google';

import { Providers } from '@/app/providers';
import { FloatingToolbox } from '@/components/layout/floating-toolbox';
import { SiteHeader } from '@/components/layout/site-header';
import { Snackbar } from '@/components/ui/snackbar';
import { SwRegister } from '@/components/sw-register';
import { DisclaimerBanner } from '@/components/legal/disclaimer-banner';
import { DisclaimerFooter } from '@/components/legal/disclaimer-footer';

import type { Metadata, Viewport } from 'next';

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
  title: '지금이니?!',
  description: '초보 투자자를 위한 미국 주식 참고/분석 도구 (투자 자문 아님).',
  appleWebApp: {
    capable: true,
    title: '지금이니?!',
    statusBarStyle: 'black-translucent',
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: dark)', color: '#09090b' },
    { media: '(prefers-color-scheme: light)', color: '#f4f8f6' },
  ],
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
      suppressHydrationWarning
    >
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var t=localStorage.getItem('theme');if(t!=='light')t='dark';document.documentElement.setAttribute('data-theme',t);if(t==='dark')document.documentElement.classList.add('dark')}catch(e){}})()`,
          }}
        />
      </head>
      <body className="min-h-full flex flex-col bg-bg">
        <Providers>
          <SiteHeader />
          <div className="flex flex-1 flex-col">{children}</div>
          <FloatingToolbox />
          <Snackbar />
          <DisclaimerBanner />
          <DisclaimerFooter />
        </Providers>
        <SwRegister />
        <Analytics />
      </body>
    </html>
  );
}
