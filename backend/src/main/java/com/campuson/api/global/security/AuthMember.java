package com.campuson.api.global.security;

/** 인증된 요청의 principal. 컨트롤러에서 {@code @AuthenticationPrincipal AuthMember me}로 받는다. */
public record AuthMember(long memberId, long schoolId, Long authTime) {
}
