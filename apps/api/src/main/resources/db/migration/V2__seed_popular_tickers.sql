-- V2__seed_popular_tickers.sql
-- MVP 인기 종목 10개 시드 (SSR/ISR 랜딩 대상)
-- 참조: docs/02-design/features/mvp.do.md §2.1 Step 4
--
-- ON CONFLICT DO NOTHING: 마이그레이션 재실행/환경 간 안전성 확보.
-- 회사명 변경/추가는 별도 마이그레이션(V3+)으로 처리.

INSERT INTO popular_tickers (ticker, name, display_order) VALUES
    ('AAPL',  'Apple Inc.',                                            1),
    ('TSLA',  'Tesla, Inc.',                                           2),
    ('NVDA',  'NVIDIA Corporation',                                    3),
    ('MSFT',  'Microsoft Corporation',                                 4),
    ('GOOGL', 'Alphabet Inc. Class A',                                 5),
    ('META',  'Meta Platforms, Inc.',                                  6),
    ('AMZN',  'Amazon.com, Inc.',                                      7),
    ('AMD',   'Advanced Micro Devices, Inc.',                          8),
    ('PLTR',  'Palantir Technologies Inc.',                            9),
    ('TSM',   'Taiwan Semiconductor Manufacturing Company Limited',   10)
ON CONFLICT (ticker) DO NOTHING;
