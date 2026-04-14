package com.aistockadvisor.news.service;

import com.aistockadvisor.common.util.Hashing;
import com.aistockadvisor.legal.Disclaimers;
import com.aistockadvisor.news.domain.NewsItem;
import com.aistockadvisor.news.infra.FinnhubNewsClient;
import com.aistockadvisor.news.infra.FinnhubNewsClient.CompanyNews;
import com.aistockadvisor.news.infra.NewsRawEntity;
import com.aistockadvisor.news.infra.NewsRawRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
 * <p>캐시 전략: Postgres {@code news_raw} 24h (translated_at).<br>
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
            // 재조회 (translated_at 이 채워진 최신 상태 반영).
            return repository.findFreshTranslated(ticker, since).stream()
                    .limit(take)
                    .map(this::toDto)
                    .toList();
        } catch (Exception ex) {
            log.warn("news fetch failed ticker={} reason={} — returning stale {} items",
                    ticker, ex.getMessage(), fresh.size());
            return fresh.stream().limit(take).map(this::toDto).toList();
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
            NewsRawEntity entity = new NewsRawEntity(
                    UUID.randomUUID(),
                    ticker,
                    hash,
                    Optional.ofNullable(news.source()).orElse("Finnhub"),
                    news.url(),
                    news.headline(),
                    Instant.ofEpochSecond(news.datetime()),
                    Instant.now()
            );
            try {
                result.add(repository.saveAndFlush(entity));
            } catch (DataIntegrityViolationException race) {
                // URL hash unique race — 다른 요청이 먼저 저장. 재조회.
                repository.findByArticleUrlHash(hash).ifPresent(result::add);
            }
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
