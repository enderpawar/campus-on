package com.campuson.api.member;

/** 인증 필터가 요청마다 읽는 최소 정보. DTO 프로젝션이라 엔티티·프록시 초기화가 없다. */
public record AuthSnapshot(Long memberId, Long schoolId, int tokenVersion) {
}
