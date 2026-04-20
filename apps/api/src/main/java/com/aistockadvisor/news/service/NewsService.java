package com.aistockadvisor.news.service;

import com.aistockadvisor.common.util.Hashing;
import com.aistockadvisor.legal.Disclaimers;
import com.aistockadvisor.news.domain.NewsItem;
import com.aistockadvisor.news.domain.NewsItem.Sentiment;
import com.aistockadvisor.news.infra.FinnhubNewsClient;
import com.aistockadvisor.news.infra.FinnhubNewsClient.CompanyNews;
import com.aistockadvisor.news.infra.NewsRawEntity;
import com.aistockadvisor.news.infra.NewsRawRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 뉴스 조회 서비스.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §4 (GET /news), §5
 *
 * <p>캐시 전략: Postgres {@code news_raw} 24h (translated_at) 는 Finnhub 호출 생략 여부 결정용.
 * 응답은 번역된 행 중 published_at 역순 최신 N 건 — 오래된 번역본도 발행일 기준 최신이면 노출.<br>
 * Miss → Finnhub 호출 → upsert → LLM 번역 → 응답. LLM 실패 시 영문 원문 유지 + 영문 disclaimer.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final int DEFAULT_LIMIT = 5;

    private final FinnhubNewsClient finnhub;
    private final NewsRawRepository repository;
    private final NewsTranslator translator;
    private final Duration cacheTtl;

    public NewsService(FinnhubNewsClient finnhub,
                       NewsRawRepository repository,
                       NewsTranslator translator,
                       @Value("${app.cache.news-ttl-hours:24}") long ttlHours) {
        this.finnhub = finnhub;
        this.repository = repository;
        this.translator = translator;
        this.cacheTtl = Duration.ofHours(ttlHours);
    }

    @Transactional
    public List<NewsItem> getNews(String ticker, int limit) {
        int take = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        Instant since = Instant.now().minus(cacheTtl);
        List<NewsRawEntity> fresh = repository.findFreshTranslated(ticker, since);
        if (fresh.size() >= take) {
            return fresh.stream().limit(take).map(this::toDto).toList();
        }

        try {
            List<CompanyNews> fetched = finnhub.fetchRecent(ticker);
            if (fetched.isEmpty() && fresh.isEmpty()) {
                return List.of();
            }
            List<NewsRawEntity> upserted = upsertAll(ticker, fetched);
            List<NewsRawEntity> toTranslate = upserted.stream()
                    .filter(e -> e.getTranslatedAt() == null)
                    .limit(take)
                    .toList();
            for (NewsRawEntity entity : toTranslate) {
                translateAndApply(entity, fetched);
            }
            // 재조회: published_at 역순 상위 N. 24h 필터는 캐시 결정용이라 응답 단계엔 적용 안 함.
            return repository.findLatestTranslatedByTicker(ticker, PageRequest.of(0, take))
                    .stream()
                    .map(this::toDto)
                    .toList();
        } catch (Exception ex) {
            log.warn("news fetch failed ticker={} reason={} — returning latest stale items",
                    ticker, ex.getMessage());
            return repository.findLatestTranslatedByTicker(ticker, PageRequest.of(0, take))
                    .stream()
                    .map(this::toDto)
                    .toList();
        }
    }

    private List<NewsRawEntity> upsertAll(String ticker, List<CompanyNews> fetched) {
        List<NewsRawEntity> result = new ArrayList<>();
        for (CompanyNews news : fetched) {
            String hash = Hashing.sha256Hex(news.url());
            Optional<NewsRawEntity> existing = repository.findByArticleUrlHash(hash);
            if (existing.isPresent()) {
                result.add(existing.get());
                continue;
            }
            // INSERT ... ON CONFLICT DO NOTHING — PostgreSQL 트랜잭션 abort 방지.
            UUID id = UUID.randomUUID();
            Instant publishedAt = Instant.ofEpochSecond(news.datetime());
            String source = Optional.ofNullable(news.source()).orElse("Finnhub");
            repository.insertIgnoreConflict(id, ticker, hash, source, news.url(),
                    news.headline(), publishedAt, Instant.now());
            // 삽입 성공이든 충돌 무시든 재조회로 엔티티 획득.
            repository.findByArticleUrlHash(hash).ifPresent(result::add);
        }
        return result;
    }

    private void translateAndApply(NewsRawEntity entity, List<CompanyNews> fetched) {
        CompanyNews source = fetched.stream()
                .filter(c -> Hashing.sha256Hex(c.url()).equals(entity.getArticleUrlHash()))
                .findFirst()
                .orElse(null);
        if (source == null) {
            return;
        }
        NewsTranslator.Translation tr = translator.translate(source);
        if (tr == null) {
            // 번역 실패(LLM 에러 · JSON 파싱 실패 · 금지어 차단) 시 영문 원문 fallback.
            // translated_at 을 now 로 확정해 동일 기사를 매 요청마다 재번역하지 않도록 하고,
            // findFreshTranslated 쿼리가 행을 포함하도록 한다. FE 는 title_ko == null 이면
            // title_en 으로 렌더(news-panel.tsx, market-news.tsx 참조).
            log.info("news-translator fallback-to-english id={} url={}", source.id(), source.url());
            entity.applyTranslation(null, null, Sentiment.NEUTRAL, Instant.now());
            repository.saveAndFlush(entity);
            return;
        }
        entity.applyTranslation(tr.titleKo(), tr.summaryKo(), tr.sentiment(), Instant.now());
        repository.saveAndFlush(entity);
    }

    private NewsItem toDto(NewsRawEntity e) {
        return new NewsItem(
                e.getTicker(),
                e.getArticleUrlHash(),
                e.getSource(),
                e.getSourceUrl(),
                e.getTitleEn(),
                e.getTitleKo(),
                e.getSummaryKo(),
                e.getSentiment(),
                e.getPublishedAt(),
                e.getTranslatedAt(),
                Disclaimers.NEWS
        );
    }
}
