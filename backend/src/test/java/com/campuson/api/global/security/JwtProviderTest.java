package com.campuson.api.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.security.WeakKeyException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Clock 주입으로 Thread.sleep 없이 만료를 검증한다 (Day 34 부채 상환). */
class JwtProviderTest {

    private static final String SECRET = "test-only-campus-on-secret-key-32bytes-min";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T03:00:00Z"));
    private final JwtProvider provider = new JwtProvider(
            new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(14)), clock);

    @Test
    void accessTokenValidBeforeExpiryAndRejectedAfterWithoutSleeping() {
        String access = provider.issue(7L, 3).accessToken();

        clock.advance(Duration.ofMinutes(14));
        TokenClaims claims = provider.verify(access, TokenType.ACCESS).orElseThrow();
        assertEquals(7L, claims.memberId());
        assertEquals(3, claims.tokenVersion());

        clock.advance(Duration.ofMinutes(2));
        assertTrue(provider.verify(access, TokenType.ACCESS).isEmpty());
    }

    @Test
    void refreshTokenIsRejectedWhereAccessTokenIsExpected() {
        String refresh = provider.issue(7L, 0).refreshToken();

        assertTrue(provider.verify(refresh, TokenType.ACCESS).isEmpty());
        assertTrue(provider.verify(refresh, TokenType.REFRESH).isPresent());
    }

    @Test
    void tamperedSignatureIsRejected() {
        String access = provider.issue(7L, 0).accessToken();
        char last = access.charAt(access.length() - 1);
        String tampered = access.substring(0, access.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertTrue(provider.verify(tampered, TokenType.ACCESS).isEmpty());
    }

    @Test
    void secretShorterThan32BytesFailsAtConstruction() {
        JwtProperties weak = new JwtProperties("too-short-secret", Duration.ofMinutes(15), Duration.ofDays(14));

        assertThrows(WeakKeyException.class, () -> new JwtProvider(weak, clock));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
