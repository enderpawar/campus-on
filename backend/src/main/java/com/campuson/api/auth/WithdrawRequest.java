package com.campuson.api.auth;

import jakarta.validation.constraints.NotBlank;

/** 탈퇴 요청. 되돌릴 수 없는 작업이라 현재 비밀번호를 다시 받는다. */
public record WithdrawRequest(@NotBlank String password) {
}
