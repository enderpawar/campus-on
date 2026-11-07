package com.campuson.api.auth;

import com.campuson.api.global.security.AuthMember;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final com.campuson.api.global.security.ClientAddressResolver addresses;

    public WithdrawalController(WithdrawalService withdrawalService, com.campuson.api.global.security.ClientAddressResolver addresses) {
        this.withdrawalService = withdrawalService; this.addresses = addresses;
    }

    /** 탈퇴. 본문 {@code {"password": "..."}}로 현재 비밀번호를 확인한 뒤 회원·일정·구독 피드를 삭제한다. */
    @DeleteMapping("/api/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthMember me,
                                         @Valid @RequestBody WithdrawRequest request, jakarta.servlet.http.HttpServletRequest http) {
        withdrawalService.withdraw(me.memberId(), request, me.authTime(), addresses.resolve(http));
        return ResponseEntity.noContent().build();
    }
}
