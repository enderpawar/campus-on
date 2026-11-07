package com.campuson.api.global.security;

import com.campuson.api.global.error.ApiProblems;
import com.campuson.api.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * FORBIDDEN 응답을 전역 핸들러와 같은 ProblemDetail로 직렬화한다.
 * 스터디 Day 27~28의 문자열 이어 붙이기 JSON(이스케이프 위험)을 ObjectMapper 직렬화로 바꿨다.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiProblems.of(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.message(), request, List.of()));
    }
}
