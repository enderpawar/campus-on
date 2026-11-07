package com.campuson.api.global.error;

import com.campuson.api.global.error.ApiProblems.FieldErrorItem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 오류를 ProblemDetail(application/problem+json) 하나의 형식으로 돌려준다.
 * 스터디 Day 3·7의 오류 본문 3종 혼재와 필드 위반 덮어쓰기를 여기서 정리한다 (ADR-0006).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Comparator<FieldErrorItem> FIELD_ORDER =
            Comparator.comparing(FieldErrorItem::field).thenComparing(FieldErrorItem::message);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return respond(ex.code(), ex.getMessage(), request, List.of());
    }

    /** 로그인 실패 누적·수동 동기화 쿨다운. 클라이언트가 재시도 시점을 알 수 있게 {@code Retry-After}를 보낸다. */
    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()));
        return respond(ex.code(), ex.getMessage(), request, List.of(), headers);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorItem(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(new FieldErrorItem("_global", ge.getDefaultMessage())));
        errors.sort(FIELD_ORDER);
        return respond(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(), request, errors);
    }

    /**
     * 경로 변수에 {@code @Positive} 같은 제약이 있으면 Spring은 본문까지 한 번에 메서드 검증하고 이 예외를 던진다.
     * 본문(@Valid @RequestBody)의 위반은 파라미터 이름("request")이 아니라 본문 필드 이름("title")으로 알려야
     * POST(MethodArgumentNotValidException)와 PUT의 오류 형식이 같아진다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors.getFieldErrors()
                        .forEach(fe -> errors.add(new FieldErrorItem(fe.getField(), fe.getDefaultMessage())));
                bodyErrors.getGlobalErrors()
                        .forEach(ge -> errors.add(new FieldErrorItem("_global", ge.getDefaultMessage())));
                return;
            }
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new FieldErrorItem(name, error.getDefaultMessage()));
            }
        });
        errors.sort(FIELD_ORDER);
        return respond(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(), request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "요청 본문을 읽을 수 없습니다.", request, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "필수 파라미터 '" + ex.getParameterName() + "'가 없습니다.", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "'" + ex.getName() + "' 파라미터 형식이 올바르지 않습니다.", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.message(), request, List.of());
    }

    /** {@code Allow} 헤더(지원 메서드 목록)를 함께 보낸다 (RFC 9110 §15.5.6). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.message(), request, List.of(), ex.getHeaders());
    }

    /** 잘못된 Content-Type은 클라이언트 오류다: 500으로 떨어지지 않게 415와 {@code Accept} 헤더로 답한다. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.message(), request, List.of(), ex.getHeaders());
    }

    /**
     * 동시 요청 경합의 최종 방어선(DB UNIQUE/CHECK). 서비스에서 catch하지 않는다:
     * 리포지토리 프록시를 통과하며 트랜잭션이 이미 rollback-only로 표시되기 때문이다 (Day 16·17, ADR-0004).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("데이터 무결성 위반: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DATA_CONFLICT, ErrorCode.DATA_CONFLICT.message(), request, List.of());
    }

    /** 내부 메시지는 응답에 노출하지 않는다 (Day 7). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("처리하지 못한 예외", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message(), request, List.of());
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, HttpServletRequest request, List<FieldErrorItem> errors) {
        return respond(code, detail, request, errors, HttpHeaders.EMPTY);
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, HttpServletRequest request,
                                                  List<FieldErrorItem> errors, HttpHeaders headers) {
        return ResponseEntity.status(code.status())
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblems.of(code, detail, request, errors));
    }
}
