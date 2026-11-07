package com.campuson.api.global.security;

import com.campuson.api.global.error.ApiProblems;
import com.campuson.api.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * UNAUTHORIZED 응답을 전역 핸들러와 같은 ProblemDetail로 직렬화한다.
 * 스터디 Day 27~28의 문자열 이어 붙이기 JSON(이스케이프 위험)을 ObjectMapper 직렬화로 바꿨다.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 401은 적용할 인증 방식을 challenge로 알려야 한다 (RFC 9110 §15.5.2, RFC 6750 §3)
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiProblems.of(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.message(), request, List.of()));
    }
}
