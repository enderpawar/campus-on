package com.campuson.api.eclass;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.global.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Planary-style background sync with a synchronous manual endpoint and visible failure state.
 * DB lease로 회원당 동시에 하나만 실행한다. 수동 동기화는 마지막 시도 후 쿨다운이 지나야 한다.
 */
@Service
public class EclassSyncService {
    private static final Logger log = LoggerFactory.getLogger(EclassSyncService.class);
    private final EclassGateway gateway;
    private final EclassAccountService accounts;
    private final EclassConnectionRepository connections;
    private final Clock clock;
    private final boolean backgroundEnabled;
    private final Duration syncDelay;
    private final Duration failureBackoff;
    private final Duration manualCooldown;
    private final ConnectionCoordination coordination;
    public EclassSyncService(EclassGateway gateway, EclassAccountService accounts,
            EclassConnectionRepository connections, Clock clock, ConnectionCoordination coordination,
            @Value("${campus.eclass.sync-enabled:true}") boolean backgroundEnabled,
            @Value("${campus.eclass.sync-delay:PT5M}") Duration syncDelay,
            @Value("${campus.eclass.failure-backoff:PT30M}") Duration failureBackoff,
            @Value("${campus.eclass.manual-sync-cooldown:PT1M}") Duration manualCooldown) {
        this.coordination = coordination; this.gateway = gateway; this.accounts = accounts; this.connections = connections; this.clock = clock;
        this.backgroundEnabled = backgroundEnabled; this.syncDelay = syncDelay;
        this.failureBackoff = failureBackoff; this.manualCooldown = manualCooldown;
    }
    /** 수동 동기화. 실행 중이면 409, 쿨다운 중이면 429(Retry-After). */
    public EclassConnectionResponse sync(long memberId) {
        String owner = coordination.acquireSync(memberId);
        if (owner == null) {
            accounts.lastAttemptAt(memberId);
            throw new BusinessException(ErrorCode.ECLASS_SYNC_IN_PROGRESS);
        }
        try {
            Duration wait = manualCooldown.minus(Duration.between(accounts.lastAttemptAt(memberId), clock.instant()));
            if (manualCooldown.isPositive() && wait.isPositive()) throw new RateLimitedException(wait);
            attempt(memberId, owner);
            return accounts.status(memberId);
        } finally {
            coordination.releaseSync(memberId, owner);
        }
    }
    @Async("eclassExecutor")
    public void requestSync(long memberId) {
        if (backgroundEnabled) syncInBackground(memberId);
    }
    @Scheduled(fixedDelayString = "${campus.eclass.sync-delay:PT5M}", initialDelayString = "${campus.eclass.sync-delay:PT5M}")
    public void scheduledSync() {
        if (!backgroundEnabled) return;
        Instant now = clock.instant();
        for (Long memberId : connections.findDueMemberIds(now.minus(syncDelay), now.minus(failureBackoff))) {
            syncInBackground(memberId);
        }
    }
    /** 배경 실행: 같은 회원이 이미 동기화 중이면 건너뛴다. 결과는 연결 상태로만 남는다. */
    public void syncInBackground(long memberId) {
        String owner = coordination.acquireSync(memberId);
        if (owner == null) return;
        try { attempt(memberId, owner); }
        catch (BusinessException ignored) { /* status persisted by attempt(); no raw credentials logged */ }
        finally { coordination.releaseSync(memberId, owner); }
    }
    /**
     * 시작할 때 읽은 연결 세대로만 결과를 반영한다. 도중에 로그아웃·재로그인되면 저장 비밀번호로 다시 로그인하지
     * 않고, 끝난 결과(성공·실패 모두)도 버린 뒤 info 한 줄만 남긴다. 이미 학교로 보낸 요청은 회수할 수 없다.
     */
    private void attempt(long memberId, String owner) {
        String generation = null;
        try {
            EclassSyncContext context = accounts.context(memberId);
            generation = context.generation();
            String cookie = context.sessionCookie();
            EclassSnapshot snapshot;
            try { snapshot = gateway.fetch(cookie); }
            catch (BusinessException ex) {
                if (ex.code() != ErrorCode.ECLASS_REAUTH_REQUIRED || context.password() == null) throw ex;
                String claim = coordination.claim(memberId, generation, owner);
                if (claim == null) { logDiscarded(memberId); return; }
                try { cookie = reauthenticate(context); }
                finally { coordination.releaseClaim(memberId, claim); }
                snapshot = gateway.fetch(cookie);
            }
            if (!accounts.synchronizedSnapshot(memberId, generation, snapshot, cookie)) logDiscarded(memberId);
        } catch (UnreadableSecretException ex) {
            if (accounts.secretsUnreadable(memberId, ex.generation())) {
                log.warn("e-class 저장 자격 증명을 복호화할 수 없어 재로그인을 요구함 memberId={}", memberId);
            } else logDiscarded(memberId);
            throw new BusinessException(ErrorCode.ECLASS_REAUTH_REQUIRED);
        } catch (BusinessException ex) {
            // INVALID_CREDENTIALS here means the saved password was rejected: failed() forgets it (no lockout loop).
            if (generation != null && !accounts.failed(memberId, generation, ex.code())) logDiscarded(memberId);
            // Code only: upstream bodies and credentials never reach the log.
            else if (ex.code() == ErrorCode.ECLASS_REAUTH_REQUIRED || ex.code() == ErrorCode.UNAUTHORIZED) {
                log.info("e-class 동기화 중단 memberId={} code={}", memberId, ex.code());
            } else {
                log.warn("e-class 동기화 실패 memberId={} code={}", memberId, ex.code());
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (generation != null && !accounts.failed(memberId, generation, ErrorCode.ECLASS_UNAVAILABLE)) logDiscarded(memberId);
            // Exception class only: messages may carry upstream HTML or SQL detail.
            else log.warn("e-class 동기화 실패 memberId={} cause={}", memberId, ex.getClass().getName());
            throw new BusinessException(ErrorCode.ECLASS_UNAVAILABLE);
        }
    }
    /** 옛 세대의 결과: 새 연결에는 아무것도 쓰지 않았다. 운영자가 경고로 오인하지 않게 info. */
    private static void logDiscarded(long memberId) {
        log.info("e-class 동기화 결과를 버림: 도중에 연결이 바뀜 memberId={}", memberId);
    }
    private String reauthenticate(EclassSyncContext context) {
        EclassSession session = gateway.authenticate(com.campuson.api.auth.StudentIds.normalize(context.studentId()), context.password());
        if (session == null || session.profile() == null || !com.campuson.api.auth.StudentIds.normalize(context.studentId()).equals(com.campuson.api.auth.StudentIds.normalize(session.profile().studentId()))
                || session.sessionCookie() == null || session.sessionCookie().isBlank()) {
            throw new BusinessException(ErrorCode.ECLASS_UNAVAILABLE);
        }
        return session.sessionCookie();
    }
}
