package com.campuson.api.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.campuson.api.global.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginAttemptThrottleTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-03-02T00:00:00Z"));
    private final org.springframework.jdbc.datasource.DriverManagerDataSource ds = database();
    private final org.springframework.jdbc.core.JdbcTemplate db = new org.springframework.jdbc.core.JdbcTemplate(ds);
    private final LoginAttemptThrottle throttle = instance();
    private LoginAttemptThrottle instance() {
        return new LoginAttemptThrottle(db, new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds), clock, 3, 5, Duration.ofMinutes(15), Duration.ofSeconds(15), Duration.ofSeconds(10));
    }
    private static org.springframework.jdbc.datasource.DriverManagerDataSource database() {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:throttle_"+java.util.UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        org.flywaydb.core.Flyway.configure().dataSource(ds).load().migrate(); return ds;
    }
    @Test void independentInstancesShareReservationsAndExpiredLeaseCanBeReclaimed() {
        LoginAttemptThrottle other = instance();
        throttle.begin("Ｓ１", "a"); other.begin(" s1 ", "b"); throttle.begin("s1", "c");
        assertThrows(RateLimitedException.class, () -> other.begin("s1", "d"));
        clock.advance(Duration.ofSeconds(15));
        other.begin("s1", "d").close();
    }

    @Test void studentLimitBlocksUntilTheWindowEndsWithExactRetryAfter() {
        for (int i = 0; i < 3; i++) fail("s1", "ip-" + i);
        clock.advance(Duration.ofMinutes(5));
        RateLimitedException limited = assertThrows(RateLimitedException.class, () -> throttle.begin("s1", "other-ip"));
        assertEquals(600, limited.retryAfterSeconds());
        throttle.begin("s2", "other-ip").close();

        clock.advance(Duration.ofMinutes(10));
        throttle.begin("s1", "other-ip").close();
    }

    /** 학교 응답 전의 동시 시도도 예산을 쓴다: 진행 중 3건이면 4번째는 학교에 가지 않는다. */
    @Test void concurrentAttemptsReserveTheBudgetBeforeTheSchoolAnswers() {
        List<LoginAttemptThrottle.Attempt> pending = new ArrayList<>();
        for (int i = 0; i < 3; i++) pending.add(throttle.begin("s1", "ip-" + i));
        RateLimitedException limited = assertThrows(RateLimitedException.class, () -> throttle.begin("s1", "ip-9"));
        assertEquals(1, limited.retryAfterSeconds());

        pending.getFirst().close(); // 학교 장애 등 결과 없이 끝나면 예약만 반납하고 실패로 세지 않는다.
        LoginAttemptThrottle.Attempt next = throttle.begin("s1", "ip-9");
        pending.get(1).failed(); pending.get(2).failed(); next.failed();
        assertEquals(900, assertThrows(RateLimitedException.class, () -> throttle.begin("s1", "ip-9")).retryAfterSeconds());
    }

    @Test void clientLimitCatchesGuessingAcrossManyStudents() {
        for (int i = 0; i < 5; i++) fail("student-" + i, "1.2.3.4");
        assertThrows(RateLimitedException.class, () -> throttle.begin("fresh-student", "1.2.3.4"));
        throttle.begin("fresh-student", "5.6.7.8").close();
    }

    @Test void successClearsOnlyThatStudentsFailures() {
        fail("s1", "1.2.3.4"); fail("s1", "1.2.3.4"); fail("s2", "1.2.3.4");
        throttle.begin("s1", "9.9.9.9").succeeded();
        fail("s1", "9.9.9.9"); fail("s1", "9.9.9.9");
        throttle.begin("s1", "9.9.9.9").close();
        fail("s1", "9.9.9.9");
        assertThrows(RateLimitedException.class, () -> throttle.begin("s1", "9.9.9.9"));
        // 1.2.3.4 still carries 3 failures: success of one student does not launder a guessing client.
        fail("s3", "1.2.3.4"); fail("s4", "1.2.3.4");
        assertThrows(RateLimitedException.class, () -> throttle.begin("s5", "1.2.3.4"));
    }

    private void fail(String studentId, String client) {
        try (LoginAttemptThrottle.Attempt attempt = throttle.begin(studentId, client)) { attempt.failed(); }
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
