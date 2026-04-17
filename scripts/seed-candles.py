#!/usr/bin/env python3
"""
인기 종목 5년 일봉 OHLCV 벌크 다운로드 → CSV 출력.
Usage: pip install yfinance && python scripts/seed-candles.py > scripts/candles-seed.csv

CSV 형식: ticker,trade_date,open,high,low,close,adj_close,volume
PostgreSQL COPY 또는 Spring CommandLineRunner로 적재.

참조: docs/02-design/features/phase4.5-improvements.design.md §5.4
"""

import sys
from datetime import datetime, timedelta

try:
    import yfinance as yf
except ImportError:
    print("pip install yfinance 필요", file=sys.stderr)
    sys.exit(1)

# popular_tickers 테이블과 동일 (V2__seed_popular_tickers.sql 기준)
TICKERS = [
    "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "META", "TSLA", "BRK-B",
    "JPM", "V", "UNH", "JNJ", "WMT", "PG", "MA", "HD", "XOM", "DIS",
    "BAC", "NFLX", "CRM", "ADBE", "AMD", "INTC", "CSCO", "PEP", "KO",
    "ABT", "MRK", "TMO",
]

end = datetime.now()
start = end - timedelta(days=365 * 5 + 30)  # 5년 + 여유

for ticker in TICKERS:
    try:
        df = yf.download(ticker, start=start, end=end, auto_adjust=False, progress=False)
        if df.empty:
            print(f"WARN: {ticker} 데이터 없음", file=sys.stderr)
            continue
        for idx, row in df.iterrows():
            date_str = idx.strftime("%Y-%m-%d") if hasattr(idx, "strftime") else str(idx)[:10]
            print(
                f"{ticker},{date_str},"
                f"{row['Open']:.4f},{row['High']:.4f},{row['Low']:.4f},"
                f"{row['Close']:.4f},{row['Adj Close']:.4f},{int(row['Volume'])}"
            )
        print(f"OK: {ticker} ({len(df)} rows)", file=sys.stderr)
    except Exception as e:
        print(f"ERROR: {ticker} - {e}", file=sys.stderr)
