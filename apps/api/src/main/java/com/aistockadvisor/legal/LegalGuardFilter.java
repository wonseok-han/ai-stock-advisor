package com.aistockadvisor.legal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 4-level 금지용어 가드의 최종 Servlet 방어막 (Level 3 우회 시 차단).
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §7.4, FR-14.
 *
 * <p>모든 {@code /api/v1/**} 응답 본문을 {@link ContentCachingResponseWrapper} 로 캡처하여
 * 금지용어가 검출되면 neutral JSON 으로 치환한다. 이 필터는 최종 응답 직전 단계에서 실행되며,
 * AiSignal/News 응답이 어느 경로로 생성되었든 동일하게 적용된다.
 *
 * <p>적용 대상: {@code application/json} 응답만. 정적 리소스/에러 응답은 pass-through.
 */
@Component
public class LegalGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LegalGuardFilter.class);

    private final ForbiddenTermsRegistry forbidden;
    private final ObjectMapper objectMapper;

    public LegalGuardFilter(ForbiddenTermsRegistry forbidden, ObjectMapper objectMapper) {
        this.forbidden = forbidden;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
        } finally {
            byte[] body = wrapper.getContentAsByteArray();
            String contentType = wrapper.getContentType();
            if (body.length > 0 && isJson(contentType)) {
                byte[] sanitized = sanitize(request.getRequestURI(), body);
                if (sanitized != body) {
                    wrapper.resetBuffer();
                    wrapper.setContentLength(sanitized.length);
                    wrapper.getOutputStream().write(sanitized);
                }
            }
            wrapper.copyBodyToResponse();
        }
    }

    private boolean isJson(String contentType) {
        return contentType != null && contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * 응답 body 에서 금지용어를 탐지. 검출 시 엔드포인트별 neutral JSON 으로 치환.
     * 치환하지 않으면 원본 배열을 그대로 반환 (reference equality 로 short-circuit).
     */
    private byte[] sanitize(String uri, byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        List<String> hits = forbidden.detect(text);
        if (hits.isEmpty()) {
            return body;
        }
        log.warn("legal-guard: forbidden terms detected in response uri={} hits={}", uri, hits);
        try {
            if (uri != null && uri.endsWith("/ai-signal")) {
                return neutralAiSignal(uri).getBytes(StandardCharsets.UTF_8);
            }
            if (uri != null && uri.endsWith("/news")) {
                return "[]".getBytes(StandardCharsets.UTF_8);
            }
            return neutralGeneric().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("legal-guard: rewrite failed uri={} reason={}", uri, ex.getMessage());
            return neutralGeneric().getBytes(StandardCharsets.UTF_8);
        }
    }

    private String neutralAiSignal(String uri) {
        String ticker = extractTicker(uri);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("ticker", ticker);
        root.put("signal", "NEUTRAL");
        root.put("confidence", 0.5);
        root.put("timeframe", "MID");
        ArrayNode rationale = root.putArray("rationale");
        rationale.add("일시적으로 AI 분석이 제한되어 중립 관점으로 제공됩니다.");
        rationale.add("기술 지표·뉴스·가격 흐름을 추가 확인하신 뒤 참고하세요.");
        ArrayNode risks = root.putArray("risks");
        risks.add("시장 변동성에 따라 단기 가격 방향이 크게 바뀔 수 있습니다.");
        risks.add("응답 검증 과정에서 일부 표현이 제한되었습니다.");
        root.put("summaryKo", "참고용 중립 분석입니다. 투자 판단과 책임은 사용자 본인에게 있습니다.");
        root.put("generatedAt", Instant.now().toString());
        root.put("modelName", "legal-guard-neutral");
        root.put("disclaimer", Disclaimers.AI_SIGNAL);
        root.put("fallback", true);
        return root.toString();
    }

    private String neutralGeneric() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("fallback", true);
        root.put("disclaimer", Disclaimers.DEFAULT);
        return root.toString();
    }

    /** {@code /api/v1/stocks/{ticker}/ai-signal} 형태에서 ticker 추출. 실패 시 "N/A". */
    private String extractTicker(String uri) {
        try {
            String[] parts = uri.split("\\?")[0].split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("stocks".equals(parts[i]) && i + 1 < parts.length) {
                    String candidate = parts[i + 1];
                    if (candidate.matches("^[A-Z]{1,5}(\\.[A-Z])?$")) {
                        return candidate;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "N/A";
    }

    @SuppressWarnings("unused")
    private boolean bodyLooksLikeJson(JsonNode node) {
        return node != null;
    }
}
