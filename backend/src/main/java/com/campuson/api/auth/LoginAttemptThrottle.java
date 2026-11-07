package com.campuson.api.auth;

import com.campuson.api.global.error.RateLimitedException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 예약과 실패 창. 하나의 짧은 DB 잠금으로 두 예산을 원자적으로 판정한다. */
@Component
public class LoginAttemptThrottle {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int maxStudent, maxClient;
    private final Duration window, lease;
    public LoginAttemptThrottle(JdbcTemplate db, PlatformTransactionManager manager, Clock clock,
            @Value("${campus.auth.throttle.max-failures-per-student:5}") int maxStudent,
            @Value("${campus.auth.throttle.max-failures-per-client:20}") int maxClient,
            @Value("${campus.auth.throttle.window:PT15M}") Duration window,
            @Value("${campus.auth.throttle.reservation-lease:PT15S}") Duration lease,
            @Value("${campus.eclass.request-timeout:PT10S}") Duration timeout) {
        this.db = db; this.tx = new TransactionTemplate(manager); this.clock = clock;
        this.maxStudent = maxStudent; this.maxClient = maxClient; this.window = window;
        Duration minimum = timeout.plusSeconds(5);
        this.lease = lease.compareTo(minimum) < 0 ? minimum : lease;
    }
    public Attempt begin(String studentId, String client) {
        String student = "s:" + StudentIds.bucket(studentId), source = client == null ? null : "c:" + client;
        return tx.execute(status -> {
            lock(); Instant now = clock.instant(); prune(now);
            reject(student, maxStudent, now); if (source != null) reject(source, maxClient, now);
            String id = UUID.randomUUID().toString();
            reserve(id, student, now); if (source != null) reserve(id, source, now);
            return new Attempt(id, student, source);
        });
    }
    private void lock() { db.queryForObject("SELECT id FROM auth_throttle_mutex WHERE id=1 FOR UPDATE", Integer.class); }
    private void prune(Instant now) {
        db.update("DELETE FROM auth_throttle_failure WHERE ends_at <= ?", sql(now));
        db.update("DELETE FROM auth_throttle_reservation WHERE expires_at <= ?", sql(now));
    }
    private void reserve(String id, String key, Instant now) {
        db.update("INSERT INTO auth_throttle_reservation(attempt_id,bucket_key,expires_at) VALUES(?,?,?)", id, key, sql(now.plus(lease)));
    }
    private void reject(String key, int max, Instant now) {
        var rows = db.query("SELECT failures, ends_at FROM auth_throttle_failure WHERE bucket_key=?",
                (rs,n) -> new Failure(rs.getInt(1), rs.getTimestamp(2).toInstant()), key);
        Failure f = rows.isEmpty() ? new Failure(0, now) : rows.getFirst();
        int pending = db.queryForObject("SELECT COUNT(*) FROM auth_throttle_reservation WHERE bucket_key=?", Integer.class, key);
        if (f.count + pending >= max) throw new RateLimitedException(f.count >= max ? Duration.between(now, f.until) : Duration.ofSeconds(1));
    }
    private static OffsetDateTime sql(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private record Failure(int count, Instant until) {}
    private void finish(Attempt a, int outcome) {
        tx.executeWithoutResult(status -> {
            lock(); Instant now = clock.instant(); prune(now);
            int removed = db.update("DELETE FROM auth_throttle_reservation WHERE attempt_id=?", a.id);
            if (removed == 0) return; // expired or already completed; stale owners cannot spend a new reservation
            if (outcome == 1) { fail(a.student, now); if (a.client != null) fail(a.client, now); }
            if (outcome == 2) db.update("DELETE FROM auth_throttle_failure WHERE bucket_key=?", a.student);
        });
    }
    private void fail(String key, Instant now) {
        if (db.update("UPDATE auth_throttle_failure SET failures=failures+1 WHERE bucket_key=?", key) == 0)
            db.update("INSERT INTO auth_throttle_failure(bucket_key,failures,ends_at) VALUES(?,1,?)", key, sql(now.plus(window)));
    }
    public final class Attempt implements AutoCloseable {
        private final String id, student, client;
        private boolean finished;
        private Attempt(String id, String student, String client) { this.id=id; this.student=student; this.client=client; }
        public void failed() { complete(1); }
        public void succeeded() { complete(2); }
        @Override public void close() { complete(0); }
        private synchronized void complete(int outcome) { if (!finished) { finish(this, outcome); finished = true; } }
    }
}
