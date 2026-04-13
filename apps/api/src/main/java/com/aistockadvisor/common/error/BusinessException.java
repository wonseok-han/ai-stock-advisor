package com.aistockadvisor.common.error;

import java.util.Map;

/**
 * 도메인 정의 에러를 발생시키는 런타임 예외. {@link GlobalExceptionHandler}에서 통합 처리.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage(), null, null);
    }

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    public BusinessException(ErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, null);
    }

    public BusinessException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(message != null ? message : code.defaultMessage(), cause);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
