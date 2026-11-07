package com.campuson.api.schedule;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹 계층 slice 테스트 (Day 26에서 비어 있던 POST slice 테스트).
 *
 * <p>띄우는 것: {@link EventController}, {@code @RestControllerAdvice}(GlobalExceptionHandler), Jackson,
 * Bean Validation, Spring MVC 인프라.
 *
 * <p>띄우지 않는 것: {@code @Service}, Spring Data 리포지토리, DataSource·JPA·Flyway, 그리고 일반 {@code @Configuration}
 * (SecurityConfig·LoggingConfig·ClockConfig). 그래서 EventService를 {@code @MockitoBean}으로 대체한다.
 * Security 자동 설정의 기본 체인은 생기지만 {@code addFilters = false}로 필터를 끄므로 principal은 null이다.
 * {@code @AuthenticationPrincipal}이 null이어도 본문 검증이 먼저 실패하므로 서비스는 호출되지 않는다.
 */
@WebMvcTest(EventController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService eventService;

    @Test
    void invalidCreateRequestReturns400WithAllFieldErrorsAndNeverCallsService() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"dueAt\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("dueAt"))
                .andExpect(jsonPath("$.errors[1].field").value("title"));

        verifyNoInteractions(eventService);
    }
}
