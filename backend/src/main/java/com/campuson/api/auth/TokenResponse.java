package com.campuson.api.auth;

import com.campuson.api.global.security.IssuedTokens;

/** Access/Refresh 토큰 쌍 응답. tokenType은 항상 "Bearer"다. */
public record TokenResponse(String tokenType, String accessToken, long accessTokenExpiresIn,
                            String refreshToken, long refreshTokenExpiresIn) {

    static TokenResponse from(IssuedTokens t) {
        return new TokenResponse("Bearer", t.accessToken(), t.accessExpiresInSeconds(),
                t.refreshToken(), t.refreshExpiresInSeconds());
    }
}
