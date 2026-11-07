package com.campuson.api.global.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * HS256 Access/Refresh 토큰 발급·검증 (Day 23, ADR-0005).
 * 시각은 주입된 {@link Clock}에서 읽어 만료 테스트가 Thread.sleep 없이 돈다 (Day 34 부채 상환).
 */
@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_VERSION = "ver";

    private final JwtProperties props;
    private final Clock clock;
    private final SecretKey key;

    public JwtProvider(JwtProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        // 32바이트 미만이면 WeakKeyException으로 기동 실패 (Day 23 fail-fast 유지)
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(UTF_8));
    }

    public IssuedTokens issue(long memberId, int tokenVersion) {
        return issue(memberId, tokenVersion, clock.instant().getEpochSecond());
    }

    public IssuedTokens issue(long memberId, int tokenVersion, Long authTime) {
        Date issuedAt = Date.from(clock.instant());
        String access = build(memberId, tokenVersion, TokenType.ACCESS, issuedAt, authTime, props.accessTtl());
        String refresh = build(memberId, tokenVersion, TokenType.REFRESH, issuedAt, authTime, props.refreshTtl());
        return new IssuedTokens(access, props.accessTtl().toSeconds(), refresh, props.refreshTtl().toSeconds());
    }

    public Optional<TokenClaims> verify(String token, TokenType expected) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expected.name().equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            Integer version = claims.get(CLAIM_VERSION, Integer.class);
            if (version == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(Long.parseLong(claims.getSubject()), version, claims.get("auth_time", Long.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String build(long memberId, int tokenVersion, TokenType type, Date issuedAt, Long authTime, Duration ttl) {
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_TYPE, type.name())
                .claim(CLAIM_VERSION, tokenVersion)
                .claim("auth_time", authTime)
                .issuedAt(issuedAt)
                .expiration(new Date(issuedAt.getTime() + ttl.toMillis()))
                // 알고리즘을 명시한다: signWith(key)만 쓰면 키 길이에 따라 HS384/HS512로 바뀌어 문서(HS256)와 어긋난다
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
