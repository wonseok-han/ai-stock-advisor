import type { MetadataRoute } from 'next';

/**
 * PWA 웹 앱 매니페스트. Next.js가 자동으로 `<link rel="manifest">`를 주입한다.
 * 아이콘은 벡터(/icon.svg, sizes "any")로 모든 크기를 커버하고,
 * iOS 홈 화면 아이콘은 app/apple-icon.tsx(PNG)가 담당한다.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: '지금이니?!',
    short_name: '지금이니?!',
    description: '초보 투자자를 위한 미국 주식 참고/분석 도구 (투자 자문 아님).',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    background_color: '#09090b',
    theme_color: '#09090b',
    lang: 'ko',
    dir: 'ltr',
    icons: [
      {
        src: '/icon.svg',
        sizes: 'any',
        type: 'image/svg+xml',
        purpose: 'any',
      },
    ],
  };
}
