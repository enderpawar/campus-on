package com.campuson.api.global.security;

public record TokenClaims(long memberId, int tokenVersion, Long authTime) {
}
