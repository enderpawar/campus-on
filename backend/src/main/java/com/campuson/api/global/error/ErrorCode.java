package com.campuson.api.global.error;

import org.springframework.http.HttpStatus;

/** API 오류 코드 단일 목록. 응답 본문의 {@code code} 속성으로 노출된다 (ADR-0006). */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다."),
    INVALID_EVENT_TIME(HttpStatus.BAD_REQUEST, "시작 시각은 마감 시각보다 앞서야 합니다."),
    UNKNOWN_SCHOOL(HttpStatus.BAD_REQUEST, "등록되지 않은 학교입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "학번 또는 e-class 비밀번호가 올바르지 않습니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다. 다시 로그인해 주세요."),
    ECLASS_REAUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "e-class 세션이 만료되었습니다. 다시 로그인해 주세요."),
    ECLASS_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "e-class에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    WITHDRAWAL_REAUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "학교 서버에 연결할 수 없습니다. 최근 로그인 후 허용 시간 이내에 다시 시도해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."),
    CALENDAR_FEED_NOT_FOUND(HttpStatus.NOT_FOUND, "캘린더 구독 주소를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    EVENT_NOT_EDITABLE(HttpStatus.CONFLICT, "e-class에서 가져온 일정은 수정하거나 삭제할 수 없습니다."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "다른 요청과 충돌했습니다. 다시 시도해 주세요."),
    ECLASS_SYNC_IN_PROGRESS(HttpStatus.CONFLICT, "e-class 동기화가 이미 진행 중입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
