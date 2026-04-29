package com.aistockadvisor.sec.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.sec.domain.SecFiling;
import com.aistockadvisor.sec.domain.SecFinancials;
import com.aistockadvisor.sec.infra.SecEdgarClient;
import com.aistockadvisor.sec.infra.SecFilingSummaryEntity;
import com.aistockadvisor.sec.infra.SecFilingSummaryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SEC EDGAR 공시 서비스.
 * DB 영구 저장 + Redis 핫 캐시 2계층 구조로 Gemini 호출을 공시 1건당 1회로 제한한다.
 */
@Service
public class SecFilingService {

    private static final Logger log = LoggerFactory.getLogger(SecFilingService.class);

    private static final Duration TTL_FILINGS = Duration.ofHours(1);
    private static final Duration TTL_FINANCIALS = Duration.ofHours(6);

    private static final TypeReference<List<SecFiling>> FILINGS_TYPE = new TypeReference<>() {};
    private static final TypeReference<SecFinancials> FINANCIALS_TYPE = new TypeReference<>() {};

    private final SecEdgarClient edgarClient;
    private final RedisCacheAdapter cache;
    private final SecFilingSummarizer summarizer;
    private final SecFilingSummaryRepository summaryRepo;

    public SecFilingService(SecEdgarClient edgarClient, RedisCacheAdapter cache,
                            SecFilingSummarizer summarizer,
                            SecFilingSummaryRepository summaryRepo) {
        this.edgarClient = edgarClient;
        this.cache = cache;
        this.summarizer = summarizer;
        this.summaryRepo = summaryRepo;
    }

    /** 최근 공시 최대 {@code limit}건. CIK 없거나 오류 시 빈 리스트. */
    public List<SecFiling> getRecentFilings(String ticker, int limit) {
        String key = "sec:filings:v2:" + ticker.toUpperCase();
        List<SecFiling> cached = cache.get(key, FILINGS_TYPE);
        if (cached != null) return cached;

        // 1. EDGAR 메타데이터 조회
        List<FilingMeta> metas = fetchFilingMetas(ticker, limit);
        if (metas.isEmpty()) return List.of();

        // 2. DB에서 기존 요약 조회
        List<String> accNums = metas.stream().map(FilingMeta::accession).toList();
        Map<String, SecFilingSummaryEntity> dbMap = summaryRepo
                .findByAccessionNumberIn(accNums).stream()
                .collect(Collectors.toMap(SecFilingSummaryEntity::getAccessionNumber, e -> e));

        // 3. DB에 없는 신규 건 필터
        LocalDate today = LocalDate.now();
        List<FilingMeta> newMetas = metas.stream()
                .filter(m -> !dbMap.containsKey(m.accession())).toList();

        // 4. 신규 건 Gemini URL Context 요약 (Gemini가 SEC.gov URL을 직접 읽음)
        Map<String, SecFilingSummarizer.SummaryResult> summaryMap = summarizeNewFilings(ticker, newMetas);

        // 5. 신규 건 DB 저장 (ON CONFLICT DO NOTHING — 동시 요청 race condition 방지)
        for (FilingMeta m : newMetas) {
            try {
                SecFilingSummarizer.SummaryResult sr = summaryMap.get(m.accession());
                summaryRepo.insertIgnoreDuplicate(new SecFilingSummaryEntity(
                        ticker, m.accession(), m.form(), m.category(), m.filedAt(),
                        m.docUrl(), null,
                        sr != null ? sr.summaryKo() : null,
                        sr != null ? sr.sentiment() : null));
            } catch (Exception ex) {
                log.debug("sec filing save failed acc={} reason={}", m.accession(), ex.getMessage());
            }
        }

        // 6. 최종 결과 조합 (EDGAR 순서 유지)
        List<SecFiling> result = new ArrayList<>();
        for (FilingMeta m : metas) {
            int daysAgo = (int) ChronoUnit.DAYS.between(m.filedAt(), today);
            SecFilingSummaryEntity db = dbMap.get(m.accession());
            if (db != null) {
                result.add(new SecFiling(ticker, db.getForm(), db.getEventCategory(),
                        db.getFiledAt(), db.getEventCategory(), daysAgo,
                        db.getDocumentUrl(), db.getContentSummary(), db.getSummaryKo(),
                        db.getSentiment()));
            } else {
                SecFilingSummarizer.SummaryResult sr = summaryMap.get(m.accession());
                result.add(new SecFiling(ticker, m.form(), m.category(),
                        m.filedAt(), m.category(), daysAgo, m.docUrl(),
                        null,
                        sr != null ? sr.summaryKo() : null,
                        sr != null ? sr.sentiment() : null));
            }
        }

        cache.set(key, result, TTL_FILINGS);
        return result;
    }

    /** XBRL 재무 팩트 (Revenue + NetIncome 최근 4분기). CIK 없거나 오류 시 null. */
    public SecFinancials getFinancials(String ticker) {
        String key = "sec:financials:" + ticker.toUpperCase();
        SecFinancials cached = cache.get(key, FINANCIALS_TYPE);
        if (cached != null) return cached;

        SecFinancials result = fetchFinancials(ticker);
        if (result != null) {
            cache.set(key, result, TTL_FINANCIALS);
        }
        return result;
    }

    record FilingMeta(String form, LocalDate filedAt, String category,
                      String docUrl, String accession) {}

    @SuppressWarnings("unchecked")
    private List<FilingMeta> fetchFilingMetas(String ticker, int limit) {
        return edgarClient.getCik(ticker).map(cik -> {
            try {
                Map<String, Object> subs = edgarClient.getSubmissions(cik);
                if (subs.isEmpty()) return List.<FilingMeta>of();

                Map<String, Object> filings = (Map<String, Object>) subs.get("filings");
                if (filings == null) return List.<FilingMeta>of();
                Map<String, Object> recent = (Map<String, Object>) filings.get("recent");
                if (recent == null) return List.<FilingMeta>of();

                List<String> forms = castList(recent.get("form"));
                List<String> dates = castList(recent.get("filingDate"));
                List<String> docs = castList(recent.get("primaryDocument"));
                List<Object> items = castList(recent.get("items"));
                List<String> accessions = castList(recent.get("accessionNumber"));

                List<FilingMeta> metas = new ArrayList<>();
                for (int i = 0; i < forms.size() && metas.size() < limit; i++) {
                    String form = forms.get(i);
                    if (!isRelevantForm(form)) continue;
                    LocalDate filedAt = parseDate(dates.size() > i ? dates.get(i) : null);
                    String itemCode = items.size() > i ? String.valueOf(items.get(i)) : "";
                    String acc = accessions.size() > i ? accessions.get(i) : null;
                    if (acc == null) continue;
                    String docUrl = buildDocumentUrl(cik, acc,
                            docs.size() > i ? docs.get(i) : null);
                    metas.add(new FilingMeta(form, filedAt,
                            formCategory(form, itemCode), docUrl, acc));
                }
                return metas;
            } catch (Exception ex) {
                log.debug("sec filings parse failed ticker={} reason={}", ticker, ex.getMessage());
                return List.<FilingMeta>of();
            }
        }).orElse(List.of());
    }

    private Map<String, SecFilingSummarizer.SummaryResult> summarizeNewFilings(
            String ticker, List<FilingMeta> metas) {
        if (metas.isEmpty()) return Map.of();
        LocalDate today = LocalDate.now();
        List<SecFiling> forSummary = metas.stream()
                .map(m -> new SecFiling(ticker, m.form(), m.category(), m.filedAt(),
                        m.category(), (int) ChronoUnit.DAYS.between(m.filedAt(), today),
                        m.docUrl(), null, null, null))
                .toList();
        List<SecFilingSummarizer.SummaryResult> summaries = summarizer.summarize(forSummary);
        Map<String, SecFilingSummarizer.SummaryResult> result = new HashMap<>();
        for (int i = 0; i < metas.size(); i++) {
            result.put(metas.get(i).accession(),
                    i < summaries.size() ? summaries.get(i) : null);
        }
        return result;
    }

    private String buildDocumentUrl(String cik, String accessionNumber, String primaryDoc) {
        if (accessionNumber == null || primaryDoc == null) return null;
        String cikTrimmed = cik.replaceFirst("^0+", "");
        String accNoDashes = accessionNumber.replace("-", "");
        return "https://www.sec.gov/Archives/edgar/data/"
                + cikTrimmed + "/" + accNoDashes + "/" + primaryDoc;
    }

    @SuppressWarnings("unchecked")
    private SecFinancials fetchFinancials(String ticker) {
        return edgarClient.getCik(ticker).map(cik -> {
            try {
                Map<String, Object> facts = edgarClient.getCompanyFacts(cik);
                if (facts.isEmpty()) return null;

                Map<String, Object> usGaap = (Map<String, Object>)
                        ((Map<String, Object>) facts.getOrDefault("facts", Map.of()))
                                .get("us-gaap");
                if (usGaap == null) return null;

                List<SecFinancials.QuarterValue> revenue = extractQuarterValues(usGaap, "Revenues", 4);
                if (revenue.isEmpty()) {
                    revenue = extractQuarterValues(usGaap, "RevenueFromContractWithCustomerExcludingAssessedTax", 4);
                }
                List<SecFinancials.QuarterValue> netIncome = extractQuarterValues(usGaap, "NetIncomeLoss", 4);

                if (revenue.isEmpty() && netIncome.isEmpty()) return null;

                return new SecFinancials(ticker, revenue, netIncome, "billion USD");
            } catch (Exception ex) {
                log.debug("sec financials parse failed ticker={} reason={}", ticker, ex.getMessage());
                return null;
            }
        }).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<SecFinancials.QuarterValue> extractQuarterValues(
            Map<String, Object> usGaap, String concept, int limit) {
        try {
            Map<String, Object> conceptMap = (Map<String, Object>) usGaap.get(concept);
            if (conceptMap == null) return List.of();
            Map<String, Object> units = (Map<String, Object>) conceptMap.get("units");
            if (units == null) return List.of();
            List<Map<String, Object>> usdList = (List<Map<String, Object>>) units.get("USD");
            if (usdList == null) return List.of();

            return usdList.stream()
                    .filter(e -> "10-Q".equals(e.get("form")) || "10-K".equals(e.get("form")))
                    .filter(e -> e.get("end") != null && e.get("val") != null)
                    .filter(e -> e.containsKey("frame"))
                    .sorted(Comparator.comparing(e -> String.valueOf(e.get("end")),
                            Comparator.reverseOrder()))
                    .distinct()
                    .limit(limit)
                    .map(e -> {
                        String end = String.valueOf(e.get("end"));
                        double val = ((Number) e.get("val")).doubleValue() / 1_000_000_000.0;
                        val = Math.round(val * 10.0) / 10.0;
                        return new SecFinancials.QuarterValue(quarterLabel(end), val);
                    })
                    .toList()
                    .reversed();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String quarterLabel(String endDate) {
        if (endDate == null || endDate.length() < 7) return endDate;
        String ym = endDate.substring(0, 7);
        int month = Integer.parseInt(ym.substring(5, 7));
        String year = ym.substring(0, 4);
        int quarter = (month - 1) / 3 + 1;
        return year + "-Q" + quarter;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object obj) {
        if (obj instanceof List<?> list) return (List<T>) list;
        return List.of();
    }

    private static boolean isRelevantForm(String form) {
        return switch (form) {
            case "8-K", "8-K/A", "S-3", "S-3ASR", "424B4", "424B5", "424B3" -> true;
            default -> false;
        };
    }

    private String formCategory(String form, String itemCode) {
        return switch (form) {
            case "S-3", "S-3ASR" -> "신주 발행 등록";
            case "424B4", "424B5" -> "공모 조건 확정";
            case "424B3" -> "공모 설명서";
            default -> classifyItem(itemCode);
        };
    }

    private String classifyItem(String items) {
        if (items == null || items.isBlank()) return "기타 공시";
        String first = items.split(",")[0].trim();
        return switch (first) {
            case "1.01" -> "중요 계약 체결";
            case "1.02" -> "계약 종료";
            case "1.03" -> "파산·청산";
            case "2.01" -> "중요 자산 취득·처분";
            case "2.02" -> "실적 발표";
            case "2.03" -> "부채·금융약정";
            case "2.05" -> "구조조정·비용";
            case "2.06" -> "자산가치 손상";
            case "3.01" -> "상장 폐지 위험";
            case "3.02" -> "신주 발행";
            case "4.01" -> "감사인 변경";
            case "5.01" -> "경영권 변경";
            case "5.02" -> "임원 교체";
            case "5.03" -> "정관 변경";
            case "5.07" -> "주주총회 결의";
            case "7.01" -> "FD 규정 공시";
            case "8.01" -> "기타 이벤트";
            case "9.01" -> "재무제표 첨부";
            default -> "기타 공시";
        };
    }
}
