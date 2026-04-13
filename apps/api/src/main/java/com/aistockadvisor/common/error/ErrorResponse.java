package com.aistockadvisor.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 통합 에러 응답 본문. 모든 4xx/5xx 응답은 이 형식.
 * 참조: docs/02-design/features/mvp.design.md §6.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Body error) {

    public static ErrorResponse of(ErrorCode code, String message, Map<String, Object> details, String requestId) {
        return new ErrorResponse(new Body(
                code.name(),
                message != null ? message : code.defaultMessage(),
                details,
                requestId,
                OffsetDateTime.now()
        ));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(
            String code,
            String message,
            Map<String, Object> details,
            String requestId,
            OffsetDateTime timestamp
    ) {
    }
}
