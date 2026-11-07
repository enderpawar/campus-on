package com.campuson.api.global.security;

import com.campuson.api.member.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer access 토큰을 검증하고, DB의 token_version과 일치할 때만 인증을 설정한다 (로그아웃 즉시 무효화, ADR-0005).
 * 실패해도 직접 401을 쓰지 않는다: 인증 없이 체인을 계속 타면 ExceptionTranslationFilter가 EntryPoint를 부른다 (Day 24·27).
 * {@code @Component}가 아니다: 서블릿 필터로 이중 등록되지 않도록 SecurityConfig에서 직접 생성한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, MemberRepository memberRepository) {
        this.jwtProvider = jwtProvider;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        // 인증 스킴 이름은 대소문자를 구분하지 않는다 (RFC 9110 §11.1). 토큰 값은 원문 그대로 검증한다.
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            jwtProvider.verify(header.substring(BEARER_PREFIX.length()), TokenType.ACCESS)
                    .flatMap(claims -> memberRepository.findAuthSnapshot(claims.memberId())
                            .filter(snapshot -> snapshot.tokenVersion() == claims.tokenVersion())
                            .map(snapshot -> new AuthMember(snapshot.memberId(), snapshot.schoolId(), claims.authTime())))
                    .ifPresent(snapshot -> {
                        var principal = snapshot;
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }
}
