package com.nowini.market.service;

import com.nowini.common.util.Hashing;
import com.nowini.legal.Disclaimers;
import com.nowini.market.domain.MarketNewsItem;
import com.nowini.market.infra.FinnhubMarketNewsClient;
import com.nowini.market.infra.MarketNewsEntity;
import com.nowini.market.infra.MarketNewsRepository;
import com.nowini.news.infra.FinnhubNewsClient.CompanyNews;
import com.nowini.news.service.NewsTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 시장 일반 뉴스. DB(market_news) 영속화 기반.
 * <p>
 * 쓰기: 스케줄러 배치가 30분마다 Finnhub general news 수집(중복 무시) → 신규 항목 번역 후 저장.
 * 읽기: DB 커서 페이지네이션(published_at 역순) — 사용자는 외부 API 안 거치고 과거까지 열람 가능.
 * 참조: docs/02-design/features/market-dashboard.design.md §5.2
 */
@Service
public class MarketNewsService {

    private static final Logger log = LoggerFactory.getLogger(MarketNewsService.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    /** 배치당 번역 상한(폭주 방지). dedup 으로 실제 신규는 소수라 넉넉하다. */
    private static final int TRANSLATE_BATCH = 50;

    private final FinnhubMarketNewsClient newsClient;
    private final NewsTranslator translator;
    private final MarketNewsRepository repository;

    public MarketNewsService(FinnhubMarketNewsClient newsClient,
                             NewsTranslator translator,
                             MarketNewsRepository repository) {
        this.newsClient = newsClient;
        this.translator = translator;
        this.repository = repository;
    }

    /**
     * 화면 조회 — DB 커서 페이지네이션. {@code before}(epoch seconds) 보다 과거인 뉴스를 최신순으로.
     * 미지정(첫 페이지)이면 최신부터. 테이블이 비어 있는 첫 페이지에선 1회 수집(콜드스타트 안전망).
     */
    public List<MarketNewsItem> getNews(int limit, Long before) {
        int take = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        boolean firstPage = before == null || before <= 0;
        Instant cursor = firstPage ? Instant.now() : Instant.ofEpochSecond(before);

        List<MarketNewsEntity> rows = repository.findPageBefore(cursor, PageRequest.of(0, take));
        if (rows.isEmpty() && firstPage) {
            // 콜드스타트(배치 전 빈 테이블): 동기 수집만 수행(번역 제외 → 대기 최소화), 영문으로라도 노출
            ingest();
            rows = repository.findPageBefore(cursor, PageRequest.of(0, take));
        }
        return rows.stream().map(this::toItem).toList();
    }

    /** 배치 — 수집 + 신규 항목 번역. 스케줄러가 호출. */
    public void refresh() {
        ingest();
        translatePending();
    }

    /** Finnhub general news 수집 후 중복 무시 upsert. (insert 는 repo 레벨 트랜잭션) */
    public void ingest() {
        List<CompanyNews> raw = newsClient.fetchGeneralNews();
        if (raw.isEmpty()) return;

        Instant now = Instant.now();
        for (CompanyNews n : raw) {
            if (n.url() == null || n.url().isBlank()
                    || n.headline() == null || n.headline().isBlank()) {
                continue;
            }
            repository.insertIgnoreConflict(
                    UUID.randomUUID(),
                    Hashing.sha256Hex(n.url()),
                    n.source() != null && !n.source().isBlank() ? n.source() : "Unknown",
                    n.url(),
                    n.headline(),
                    n.summary(),
                    Instant.ofEpochSecond(n.datetime()),
                    now);
        }
    }

    /** 번역 대기 항목을 번역해 저장. 실패 시 영문 유지하되 translated_at 채워 재시도 방지. */
    public void translatePending() {
        List<MarketNewsEntity> pending = repository.findUntranslated(PageRequest.of(0, TRANSLATE_BATCH));
        for (MarketNewsEntity e : pending) {
            Instant now = Instant.now();
            try {
                CompanyNews cn = new CompanyNews(0L, e.getPublishedAt().getEpochSecond(),
                        e.getTitleEn(), e.getSource(), e.getSummaryEn(), e.getSourceUrl(), "general", "");
                NewsTranslator.Translation tr = translator.translate(cn);
                e.applyTranslation(tr != null ? tr.titleKo() : null,
                        tr != null ? tr.summaryKo() : null, now);
            } catch (Exception ex) {
                log.debug("market news translation failed hash={}: {}", e.getArticleUrlHash(), ex.getMessage());
                e.applyTranslation(null, null, now);
            }
            repository.save(e);
        }
    }

    private MarketNewsItem toItem(MarketNewsEntity e) {
        return new MarketNewsItem(
                e.getArticleUrlHash(),
                e.getSource(),
                e.getSourceUrl(),
                e.getTitleEn(),
                e.getTitleKo(),
                e.getSummaryKo(),
                e.getPublishedAt().getEpochSecond(),
                Disclaimers.MARKET_NEWS);
    }
}
