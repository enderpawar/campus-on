package com.campuson.api.eclass;

import java.time.*;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;
import com.campuson.api.global.error.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** HTTP 밖의 짧은 선점 트랜잭션. 소유자 토큰으로 만료 후 다른 실행의 lease 해제를 막는다. */
@Service
public class ConnectionCoordination {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration syncLease, claimLease;
    public ConnectionCoordination(JdbcTemplate db, PlatformTransactionManager manager, Clock clock,
            @Value("${campus.eclass.sync-lease:PT5M}") Duration syncLease,
            @Value("${campus.eclass.request-timeout:PT10S}") Duration timeout) {
        this.db=db; this.tx=new TransactionTemplate(manager); this.clock=clock;
        this.claimLease=timeout.plusSeconds(5);
        Duration minimum=Duration.ofMinutes(4).plus(claimLease);
        this.syncLease=syncLease.compareTo(minimum)<0 ? minimum : syncLease;
    }
    public String acquireSync(long id) {
        String owner=UUID.randomUUID().toString(); Instant now=clock.instant();
        return tx.execute(s -> db.update("UPDATE eclass_connections SET sync_lease_owner=?, sync_lease_until=? WHERE user_id=? AND (sync_lease_until IS NULL OR sync_lease_until<=?)",
                owner, sql(now.plus(syncLease)), id, sql(now)) == 1 ? owner : null);
    }
    public void releaseSync(long id, String owner) {
        tx.executeWithoutResult(s -> db.update("UPDATE eclass_connections SET sync_lease_owner=NULL, sync_lease_until=NULL WHERE user_id=? AND sync_lease_owner=?", id, owner));
    }
    public String claim(long id, String generation, String syncOwner) {
        String owner=UUID.randomUUID().toString(); Instant now=clock.instant();
        return tx.execute(s -> db.update("UPDATE eclass_connections SET reauth_claim_owner=?, reauth_claim_until=? WHERE user_id=? AND connection_generation=? AND sync_lease_owner=? AND sync_lease_until>? AND (reauth_claim_until IS NULL OR reauth_claim_until<=?)",
                owner, sql(now.plus(claimLease)), id, generation, syncOwner, sql(now), sql(now)) == 1 ? owner : null);
    }
    public void releaseClaim(long id, String owner) {
        tx.executeWithoutResult(s -> db.update("UPDATE eclass_connections SET reauth_claim_owner=NULL, reauth_claim_until=NULL WHERE user_id=? AND reauth_claim_owner=?", id, owner));
    }
    /** Caller already holds school/user locks; the connection lock and claim check are atomic with mutation. */
    public void assertMutable(long id) {
        var claims=db.query("SELECT reauth_claim_until FROM eclass_connections WHERE user_id=? FOR UPDATE",
                (rs,n) -> rs.getTimestamp(1), id);
        if (!claims.isEmpty() && claims.getFirst()!=null && claims.getFirst().toInstant().isAfter(clock.instant())) throw new Busy();
    }
    /** Each rejected attempt rolls back before parking: no connection or transaction is retained during waiting. */
    public <T> T mutate(Supplier<T> action) {
        long deadline=System.nanoTime()+claimLease.toNanos();
        while (true) {
            try { return tx.execute(s -> action.get()); }
            catch (Busy ex) {
                if (System.nanoTime()>=deadline || Thread.currentThread().isInterrupted()) throw new BusinessException(ErrorCode.ECLASS_SYNC_IN_PROGRESS);
                LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
            }
        }
    }
    private static OffsetDateTime sql(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private static final class Busy extends RuntimeException { private static final long serialVersionUID=1L; }
}
