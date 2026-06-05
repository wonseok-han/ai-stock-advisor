import { ImageResponse } from 'next/og';

// iOS 홈 화면 아이콘(apple-touch-icon)은 PNG만 인식하므로 로고 SVG를 래스터화해 생성한다.
// Next.js가 자동으로 <link rel="apple-touch-icon">를 주입한다.
export const runtime = 'nodejs';
export const size = { width: 180, height: 180 };
export const contentType = 'image/png';

// app/icon.svg 와 동일한 마크 (둥근 사각 #18181b 배경 + 에메랄드 워드마크).
const ICON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180" viewBox="0 0 48 48">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#10b981"/>
      <stop offset="100%" stop-color="#2dd4bf"/>
    </linearGradient>
  </defs>
  <rect width="48" height="48" fill="#18181b"/>
  <g transform="translate(3,-3.3) scale(0.53)">
    <path fill="url(#g)" d="M17.86 19.20L17.86 20.59Q17.86 26.30 18.82 30.41Q19.78 34.51 21.46 36.70Q23.14 38.88 25.30 39.12L25.30 39.12L23.38 46.56Q20.78 46.37 18.19 44.69Q15.60 43.01 13.63 39.74L13.63 39.74Q11.62 43.01 9.02 44.69Q6.43 46.37 3.84 46.56L3.84 46.56L1.92 39.12Q5.76 38.78 7.56 34.18Q9.36 29.57 9.36 20.59L9.36 20.59L9.36 19.20L2.88 19.20L2.88 12L24.29 12L24.29 19.20L17.86 19.20Z"/>
    <path fill="#ffffff" d="M27.46 48L27.46 11.42L36.48 11.42L36.48 48L27.46 48Z"/>
    <path fill="url(#g)" d="M43.20 18.77L66.86 18.77L66.86 21.98L75.36 21.98L75.84 12.05L43.20 12.05L43.20 18.77Z"/>
    <path fill="#ffffff" d="M76.80 24.05L42.24 24.05L42.24 30.72L76.80 30.72L76.80 24.05Z"/>
    <path fill="#ffffff" d="M75.84 48L75.84 32.98L43.20 32.98L43.20 48L75.84 48Z"/>
    <path fill="url(#g)" d="M49.50 38.46L69.50 38.46L69.50 42.52L49.50 42.52L49.50 38.46Z"/>
    <g transform="translate(-80.6,43)">
      <g transform="translate(9.33,2.92) scale(0.9)">
        <path fill="url(#g)" d="M93.31 11.90L93.31 11.90Q88.03 11.90 85.30 14.69Q82.56 17.47 82.56 22.85L82.56 22.85L82.56 35.62Q82.56 40.99 85.30 43.78Q88.03 46.56 93.31 46.56L93.31 46.56Q98.59 46.56 101.33 43.78Q104.06 40.99 104.06 35.62L104.06 35.62L104.06 22.85Q104.06 17.47 101.33 14.69Q98.59 11.90 93.31 11.90ZM93.31 19.10L93.31 19.10Q95.09 19.10 95.09 20.93L95.09 20.93L95.09 37.54Q95.09 39.36 93.31 39.36L93.31 39.36Q91.54 39.36 91.54 37.54L91.54 37.54L91.54 20.93Q91.54 19.10 93.31 19.10Z"/>
      </g>
      <path fill="#ffffff" d="M104.5 11.5L116.5 11.5L116.5 36L109.5 36L109.5 18L109.5 18L104.5 20.5ZM109.5 40L116.5 40L116.5 48L109.5 48Z"/>
      <g transform="translate(13.22,2.92) scale(0.9)">
        <path fill="url(#g)" d="M122.02 11.90L122.02 46.56L142.46 46.56L142.46 39.36L131.04 39.36L131.04 11.90L122.02 11.90Z"/>
      </g>
      <g transform="translate(-35,-0.53)">
        <path fill="url(#g)" d="M182.40 37.06L181.92 28.80L181.68 12L190.70 12L190.46 28.80L189.98 37.06L182.40 37.06ZM186.19 39.46L186.19 39.46Q188.11 39.46 189.38 40.70Q190.66 41.95 190.66 43.97L190.66 43.97Q190.66 45.98 189.38 47.23Q188.11 48.48 186.19 48.48L186.19 48.48Q184.27 48.48 182.98 47.18Q181.68 45.89 181.68 43.97L181.68 43.97Q181.68 41.95 182.98 40.70Q184.27 39.46 186.19 39.46Z"/>
      </g>
    </g>
  </g>
</svg>`;

export default function AppleIcon() {
  const dataUri = `data:image/svg+xml;base64,${Buffer.from(ICON_SVG).toString('base64')}`;
  return new ImageResponse(
    (
      <div
        style={{
          display: 'flex',
          width: '100%',
          height: '100%',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#18181b',
        }}
      >
        <img width={180} height={180} src={dataUri} alt="" />
      </div>
    ),
    { ...size },
  );
}
