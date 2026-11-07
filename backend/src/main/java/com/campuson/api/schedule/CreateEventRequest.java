package com.campuson.api.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 개인 일정 추가. 제목 80자는 화면07 일정 추가 규칙이다. e-class 일정과 DB 컬럼은 200자를 유지한다(과제 제목이 길 수 있음). */
public record CreateEventRequest(
        @NotBlank @Size(max = 80) String title,
        @Size(max = 100) String place,
        OffsetDateTime startsAt,
        @NotNull OffsetDateTime dueAt) {
}
