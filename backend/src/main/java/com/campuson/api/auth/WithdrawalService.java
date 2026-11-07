package com.campuson.api.auth;

import com.campuson.api.eclass.EclassAccountService;
import com.campuson.api.eclass.EclassGateway;
import com.campuson.api.eclass.EclassSession;
import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalService {
    private final EclassAccountService accounts;
    private final EclassGateway gateway;
    private final WithdrawalStore store;
    private final LoginAttemptThrottle throttle;
    private final java.time.Clock clock;
    private final java.time.Duration recentAuth;
    public WithdrawalService(EclassAccountService accounts, EclassGateway gateway, WithdrawalStore store,
                             LoginAttemptThrottle throttle, java.time.Clock clock,
            @org.springframework.beans.factory.annotation.Value("${campus.withdrawal.offline-recent-auth:PT10M}") java.time.Duration recentAuth) {
        this.accounts = accounts; this.gateway = gateway; this.store = store; this.throttle = throttle; this.clock = clock; this.recentAuth = recentAuth;
    }
    /** Reauthenticate at e-class before opening the deletion transaction. No local password is stored. */
    public void withdraw(long memberId, WithdrawRequest request, Long authTime, String client) {
        String studentId = StudentIds.normalize(accounts.studentId(memberId));
        // 로그인과 같은 학번 예산: 탈퇴 비밀번호 확인도 학교 로그인 시도라 학교 계정 잠금에 똑같이 기여한다.
        EclassSession session;
        try (LoginAttemptThrottle.Attempt attempt = throttle.begin(studentId, client)) {
            try { session = gateway.authenticate(studentId, request.password()); }
            catch (BusinessException ex) {
                if (ex.code() == ErrorCode.ECLASS_UNAVAILABLE) {
                    long now = clock.instant().getEpochSecond();
                    if (authTime != null && authTime <= now && now - authTime <= recentAuth.toSeconds()) {
                        store.delete(memberId);
                        return;
                    }
                    throw new BusinessException(ErrorCode.WITHDRAWAL_REAUTH_UNAVAILABLE);
                }
                if (ex.code() != ErrorCode.INVALID_CREDENTIALS) throw ex;
                attempt.failed();
                throw new BusinessException(ErrorCode.INVALID_PASSWORD);
            }
            attempt.succeeded();
        }
        if (session == null || session.profile() == null || !studentId.equals(StudentIds.normalize(session.profile().studentId()))
                || session.sessionCookie() == null || session.sessionCookie().isBlank()) {
            throw new BusinessException(ErrorCode.ECLASS_UNAVAILABLE);
        }
        store.delete(memberId);
    }
}
