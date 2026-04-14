# 04. 외부 API 및 데이터 소스

## 4.1 주요 데이터 소스 매트릭스

| 용도 | 1순위 (주) | 2순위 (fallback) | 비용 |
|---|---|---|---|
| 실시간 시세(15분 지연) | **Finnhub** `/quote` | Yahoo (yfinance) | 무료 |
| 종목 검색 | **Finnhub** `/search` | — | 무료 |
| 기업 프로필 | **Finnhub** `/stock/profile2` | — | 무료 |
| OHLCV 캔들 | **Twelve Data** `/time_series`*1 | Alpha Vantage `TIME_SERIES_DAILY` | 무료 |
| 종목 뉴스 | **Finnhub** `/company-news` | Marketaux | 무료 |
| 시장 뉴스 | **Finnhub** `/news?category=general` | Marketaux | 무료 |
| 실적 캘린더 | Finnhub `/calendar/earnings` | — | 무료 |
| 경제 지표 캘린더 | Finnhub `/calendar/economic` | — | 무료 |
| VIX | Yahoo `^VIX` | Finnhub | 무료 |
| 주요 지수 (S&P, Nasdaq, Dow) | Yahoo `^GSPC`, `^IXIC`, `^DJI` | Finnhub | 무료 |
| 미국 10Y 금리 | Yahoo `^TNX` | FRED API | 무료 |
| USD/KRW 환율 | exchangerate.host | Yahoo `KRW=X` | 무료 |
| 섹터 ETF (시장 스냅샷) | Yahoo `XLK`, `XLF`, `XLE` 등 | — | 무료 |

> *1 **확정 (2026-04-14)**: Finnhub `/stock/candle`은 무료 플랜에서 **403 Forbidden** (유료 $49.99/mo 이상만 허용). 확인 로그:
> `{"error":"You don't have access to this resource."}`. 이에 따라 OHLCV 캔들 1순위를 **Twelve Data `/time_series`** 로 교체. 무료 800 req/day, 8 req/min — Redis 캐시로 한도 내 운영 가능.
> 검색/프로필/시세/뉴스는 Finnhub 무료 그대로 유지 (hybrid provider 전략).

## 4.2 Rate Limit 및 대응

| API | 무료 한도 | 대응 |
|---|---|---|
| Finnhub | 60 req/min | Redis 캐시 + 토큰 버킷 큐 |
| Twelve Data | 8 req/min, 800 req/day | Redis 캐시(5m/1h TTL) 로 한도 내 유지 |
| Alpha Vantage | 25 req/day, 5 req/min | 캔들 legacy 백업 (현재 미사용) |
| Yahoo (비공식) | 명시 없음 (너무 많이 쓰면 차단) | 분당 30회 이하로 제한, User-Agent 필수 |
| Gemini 1.5 Flash | 분당 15 RPM, 일 1M 토큰 (무료) | 캐시 공격적 적용 |
| exchangerate.host | 1000 req/month (무료) | 5분 TTL 캐시 |

**공통 원칙:** Spring Boot의 `ExternalApiClient`에 **토큰 버킷 rate limiter(Bucket4j)** 를 달고, Redis에 전역 카운터 공유.

## 4.3 Finnhub API 주요 엔드포인트 (MVP 사용분)

```
GET /api/v1/search?q={query}&token={key}
GET /api/v1/stock/profile2?symbol=AAPL&token={key}
GET /api/v1/quote?symbol=AAPL&token={key}
GET /api/v1/stock/candle?symbol=AAPL&resolution=D&from={ts}&to={ts}&token={key}
GET /api/v1/company-news?symbol=AAPL&from=2024-01-01&to=2024-01-31&token={key}
GET /api/v1/news?category=general&token={key}
```

## 4.4 Yahoo Finance (yfinance) 사용 시 주의

Yahoo의 공식 API는 사실상 없음. 커뮤니티 클라이언트를 사용.

- Java: `yahoofinance-api` (비공식, 한동안 업데이트 없음) → **대안: 직접 `query1.finance.yahoo.com/v8/finance/chart/{symbol}` HTTP 호출**
- 주의: TOS 이슈가 있을 수 있으니 **fallback 용도로만 사용**, 상업 배포 시 공식 API로 전환 검토

## 4.5 API 키 관리

- 모든 키는 **Spring Boot 서버에만 보관**, Next.js로 노출 금지
- `application.yml` → `application-local.yml` / 환경변수로 분리
- Fly.io 배포 시 `fly secrets set FINNHUB_API_KEY=...`
- 로컬 개발: `.env` + `.gitignore`

## 4.6 기술적 지표 계산: ta4j

외부 지표 API 대신 OHLCV만 받아서 **서버에서 직접 계산**. 장점:
- 비용 $0 (계산은 무료)
- 커스터마이징 자유 (파라미터 조정 쉬움)
- API 호출 수 절감

```java
BarSeries series = new BaseBarSeriesBuilder().withName("AAPL").build();
// OHLCV 주입
ClosePriceIndicator close = new ClosePriceIndicator(series);
MACDIndicator macd = new MACDIndicator(close, 12, 26);
RSIIndicator rsi = new RSIIndicator(close, 14);
BollingerBandsMiddleIndicator mid = new BollingerBandsMiddleIndicator(new SMAIndicator(close, 20));
BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(mid, new StandardDeviationIndicator(close, 20));
```

## 4.7 데이터 신선도 요구사항 정리

| 화면 | 요구 신선도 | 캐시 전략 |
|---|---|---|
| 종목 현재가 | 1분 | Redis 60초 TTL |
| 종목 차트 (일봉) | 10분 | Redis 10분 TTL |
| 종목 뉴스 | 15분 | Redis 15분 TTL |
| AI 분석 | 1시간 | Postgres + Redis hybrid |
| 시장 대시보드 | 5분 | Redis 5분 TTL |
| 북마크 알림 체크 | 15분 | 스케줄러 주기 |
