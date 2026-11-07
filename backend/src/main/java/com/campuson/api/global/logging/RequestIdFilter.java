package com.campuson.api.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 requestId를 MDC와 응답 헤더에 싣고 접근 로그 한 줄을 남긴다 (Day 29, ADR-0008).
 * Authorization 헤더와 쿼리 스트링은 기록하지 않고, ICS 토큰 경로는 가린다.
 * {@code @Component}를 붙이지 않는다: {@code @WebMvcTest}가 Filter 빈을 자동으로 끌어오지 않게 LoggingConfig에서 등록한다.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        long started = System.nanoTime();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            log.info("{} {} -> {} ({}ms)", request.getMethod(), SensitivePaths.mask(request.getRequestURI()),
                    response.getStatus(), elapsedMs);
            MDC.remove(MDC_KEY);
        }
    }
}
