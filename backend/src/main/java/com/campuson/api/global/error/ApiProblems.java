package com.campuson.api.global.error;

import com.campuson.api.global.logging.RequestIdFilter;
import com.campuson.api.global.logging.SensitivePaths;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** 모든 오류 응답(전역 핸들러, 401/403 핸들러)이 같은 ProblemDetail 형식을 쓰도록 만드는 단일 진입점. */
public final class ApiProblems {

    private ApiProblems() {
    }

    public static ProblemDetail of(ErrorCode code, String detail, HttpServletRequest request, List<FieldErrorItem> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.status().getReasonPhrase());
        // ICS 토큰 경로는 응답 본문에도 되돌려 주지 않는다 (Codex 리뷰 #2)
        problem.setInstance(URI.create(SensitivePaths.mask(request.getRequestURI())));
        problem.setProperty("code", code.name());
        problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        problem.setProperty("timestamp", Instant.now());
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    public record FieldErrorItem(String field, String message) {
    }
}
