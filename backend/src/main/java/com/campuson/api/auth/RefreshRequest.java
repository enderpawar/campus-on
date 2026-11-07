package com.campuson.api.auth;

import jakarta.validation.constraints.NotBlank;

/** 토큰 갱신 요청. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
