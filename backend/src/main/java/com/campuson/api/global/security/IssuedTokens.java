package com.campuson.api.global.security;

public record IssuedTokens(String accessToken, long accessExpiresInSeconds, String refreshToken, long refreshExpiresInSeconds) {
}
