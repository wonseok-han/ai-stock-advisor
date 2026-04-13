package com.aistockadvisor.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

/**
 * 모든 예외를 {@link ErrorResponse} 통합 형식으로 변환.
 * 참조: docs/02-design/features/mvp.design.md §6.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        String requestId = newRequestId();
        log.warn("business error: code={} requestId={} msg={}", ex.code(), requestId, ex.getMessage());
        return ResponseEntity
                .status(ex.code().httpStatus())
                .body(ErrorResponse.of(ex.code(), ex.getMessage(), ex.details(), requestId));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex) {
        String requestId = newRequestId();
        log.warn("validation error: requestId={} msg={}", requestId, ex.getMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_TICKER.httpStatus())
                .body(ErrorResponse.of(
                        ErrorCode.INVALID_TICKER,
                        ErrorCode.INVALID_TICKER.defaultMessage(),
                        Map.of("reason", ex.getClass().getSimpleName()),
                        requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String requestId = newRequestId();
        log.error("unexpected error: requestId={}", requestId, ex);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ErrorResponse.of(
                        ErrorCode.INTERNAL_ERROR,
                        ErrorCode.INTERNAL_ERROR.defaultMessage(),
                        null,
                        requestId));
    }

    private static String newRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
