package com.campuson.api.global.error;

/**
 * 도메인 규칙 위반을 나타내는 단일 예외. 도메인마다 예외 클래스를 만들지 않고 {@link ErrorCode}로 구분한다 (ADR-0006).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        this(code, code.message());
    }

    public BusinessException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
