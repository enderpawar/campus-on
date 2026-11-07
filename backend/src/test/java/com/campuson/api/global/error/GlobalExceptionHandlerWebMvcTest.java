package com.campuson.api.global.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campuson.api.global.security.AuthMember;
import com.campuson.api.schedule.EventController;
import com.campuson.api.schedule.EventService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서비스 계층에서 올라온 예외가 어떤 HTTP 계약으로 바뀌는지 확인한다 (ADR-0006).
 * DB 경합(UNIQUE 위반)과 예상하지 못한 예외는 HTTP로 재현하기 어려워 서비스를 mock으로 두고 웹 계층만 띄운다.
 * 필터를 끄므로 인증 필터 대신 {@link #signIn()}이 SecurityContext에 principal을 직접 넣는다
 * (넣지 않으면 컨트롤러의 {@code me.memberId()}가 NPE로 500을 내서, 검증하려는 예외 매핑과 무관하게 500이 된다).
 */
@WebMvcTest(EventController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerWebMvcTest {

    private static final String VALID_BODY = "{\"title\":\"과제\",\"dueAt\":\"2099-01-01T09:00:00+09:00\"}";

    @Autowired MockMvc mockMvc;
    @MockitoBean EventService eventService;

    @BeforeEach
    void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthMember(1L, 1L, null), null, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dataIntegrityViolationBecomes409DataConflict() throws Exception {
        given(eventService.create(anyLong(), any()))
                .willThrow(new DataIntegrityViolationException("uq_event_external violated"));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(content().string(not(containsString("uq_event_external"))));
    }

    @Test
    void unexpectedExceptionBecomes500WithoutLeakingInternalMessage() throws Exception {
        given(eventService.create(anyLong(), any()))
                .willThrow(new IllegalStateException("jdbc password=secret-value"));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("요청 처리 중 오류가 발생했습니다."))
                .andExpect(content().string(not(containsString("secret-value"))));
        // 500이 다른 이유(예: principal 누락 NPE)가 아니라 서비스 예외에서 나왔는지 확인한다
        verify(eventService).create(eq(1L), any());
    }

    @Test
    void businessExceptionKeepsItsStatusAndCode() throws Exception {
        given(eventService.create(anyLong(), any())).willThrow(new BusinessException(ErrorCode.EVENT_NOT_EDITABLE));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_EDITABLE"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.EVENT_NOT_EDITABLE.message()))
                .andExpect(jsonPath("$.instance").value("/api/events"));
    }

    @Test
    void unknownPathReturns404ResourceNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
