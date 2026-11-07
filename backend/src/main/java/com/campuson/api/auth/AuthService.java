package com.campuson.api.auth;

import com.campuson.api.eclass.*;
import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.global.security.JwtProvider;
import com.campuson.api.global.security.TokenClaims;
import com.campuson.api.global.security.TokenType;
import com.campuson.api.member.AuthSnapshot;
import com.campuson.api.member.MemberRepository;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final EclassGateway gateway;
    private final EclassAccountService accounts;
    private final EclassSyncService sync;
    private final MemberRepository members;
    private final JwtProvider jwt;
    private final LoginAttemptThrottle throttle;
    private final ConnectionCoordination coordination;
    private final java.time.Clock clock;
    public AuthService(EclassGateway gateway, EclassAccountService accounts, EclassSyncService sync,
                       MemberRepository members, JwtProvider jwt, LoginAttemptThrottle throttle, ConnectionCoordination coordination, java.time.Clock clock) {
        this.gateway = gateway; this.accounts = accounts; this.sync = sync; this.members = members; this.jwt = jwt;
        this.throttle = throttle; this.coordination = coordination; this.clock = clock;
    }
    /** Upstream HTTP before the database transaction; registration is not exposed. */
    public TokenResponse login(LoginRequest request, String client) {
        String studentId = StudentIds.normalize(request.studentId());
        if (studentId.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        EclassSession session;
        long authTime;
        try (LoginAttemptThrottle.Attempt attempt = throttle.begin(studentId, client)) {
            try { session = gateway.authenticate(studentId, request.password()); }
            catch (BusinessException ex) {
                if (ex.code() == ErrorCode.INVALID_CREDENTIALS) attempt.failed();
                throw ex;
            }
            authTime = clock.instant().getEpochSecond();
            attempt.succeeded();
        }
        if (session.profile() == null || !studentId.equals(StudentIds.normalize(session.profile().studentId()))
                || session.sessionCookie() == null || session.sessionCookie().isBlank()) {
            throw new BusinessException(ErrorCode.ECLASS_UNAVAILABLE);
        }
        EclassAccountService.AuthenticatedAccount account = accounts.authenticated(session, request.password(), request.rememberCredentials());
        try { sync.requestSync(account.memberId()); }
        catch (TaskRejectedException ignored) { /* PENDING connection is retried by the scheduled worker. */ }
        return TokenResponse.from(jwt.issue(account.memberId(), account.tokenVersion(), authTime));
    }
    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        TokenClaims claims = jwt.verify(request.refreshToken(), TokenType.REFRESH)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        AuthSnapshot snapshot = members.findAuthSnapshot(claims.memberId())
                .filter(member -> member.tokenVersion() == claims.tokenVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return TokenResponse.from(jwt.issue(snapshot.memberId(), snapshot.tokenVersion(), claims.authTime()));
    }
    /**
     * 모든 기기의 토큰을 무효화하고 학교 연결(세션 쿠키·저장 비밀번호)도 지운다.
     * 남겨 두면 로그아웃한 사용자의 학교 계정을 배경 동기화가 계속 긁는다. 다시 로그인하면 연결이 새로 생긴다.
     */
    public void logout(long memberId) {
        coordination.mutate(() -> {
        if (members.increaseTokenVersion(memberId) == 0) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        accounts.forgetConnection(memberId);
        return null; });
    }
}
