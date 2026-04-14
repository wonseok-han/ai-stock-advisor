'use client';

import { useQuery } from '@tanstack/react-query';
import {
  ColorType,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import { useEffect, useRef } from 'react';

import { getCandles } from '@/lib/api/stocks';

import type { Candle, TimeFrame } from '@/types/stock';

/**
 * TradingView Lightweight Charts 기반 캔들 차트 (design §4.2).
 * BE /api/v1/stocks/{ticker}/candles?tf=... 로드.
 * 실패 시 메시지 표시. 리사이즈는 ResizeObserver.
 */
export function ChartPanel({ ticker, tf }: { ticker: string; tf: TimeFrame }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);

  const { data, isLoading, error } = useQuery<Candle[]>({
    queryKey: ['candles', ticker, tf],
    queryFn: () => getCandles(ticker, tf),
    staleTime: tf === '1D' ? 60_000 : 5 * 60_000,
  });

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const chart = createChart(el, {
      height: 360,
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: '#71717a',
      },
      grid: {
        vertLines: { color: 'rgba(228,228,231,0.4)' },
        horzLines: { color: 'rgba(228,228,231,0.4)' },
      },
      rightPriceScale: { borderVisible: false },
      timeScale: { borderVisible: false, timeVisible: false },
    });
    const series = chart.addCandlestickSeries({
      upColor: '#16a34a',
      downColor: '#dc2626',
      borderVisible: false,
      wickUpColor: '#16a34a',
      wickDownColor: '#dc2626',
    });
    chartRef.current = chart;
    seriesRef.current = series;

    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        chart.applyOptions({ width: entry.contentRect.width });
      }
    });
    ro.observe(el);

    return () => {
      ro.disconnect();
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, []);

  useEffect(() => {
    const series = seriesRef.current;
    if (!series || !data) return;
    series.setData(
      data.map((c) => ({
        time: c.time as UTCTimestamp,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      })),
    );
    chartRef.current?.timeScale().fitContent();
  }, [data]);

  return (
    <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
      <div className="mb-2 flex items-baseline justify-between">
        <h2 className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">
          차트 ({tf})
        </h2>
        {isLoading && <span className="text-xs text-zinc-500">불러오는 중…</span>}
      </div>
      {error ? (
        <div className="py-8 text-center text-sm text-red-600">
          차트 데이터를 불러오지 못했습니다.
        </div>
      ) : (
        <div ref={containerRef} className="w-full" />
      )}
    </div>
  );
}
