package com.aistockadvisor.common.error;

import org.springframework.http.HttpStatus;

/**
 * 도메인 전반의 에러 코드.
 * 참조: docs/02-design/features/mvp.design.md §6.1.
 */
public enum ErrorCode {

    INVALID_TICKER(HttpStatus.BAD_REQUEST, "올바른 티커를 입력해주세요."),
    TICKER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 종목을 찾을 수 없습니다."),
    UPSTREAM_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "외부 데이터 제공자 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 데이터 제공자 응답이 지연되어 일부 정보를 표시하지 못했습니다."),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 데이터 제공이 중단되었습니다."),
    LLM_VALIDATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 응답 검증에 실패했습니다."),
    FORBIDDEN_CONTENT(HttpStatus.INTERNAL_SERVER_ERROR, "응답이 정책에 의해 차단되었습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
