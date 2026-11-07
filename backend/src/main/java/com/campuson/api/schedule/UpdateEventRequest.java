package com.campuson.api.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** PUT 전체 교체 요청. 빠진 선택 필드는 null로 덮어쓴다. 제목 한도는 추가와 같은 80자(개인 일정만 수정 가능). */
public record UpdateEventRequest(
        @NotBlank @Size(max = 80) String title,
        @Size(max = 100) String place,
        OffsetDateTime startsAt,
        @NotNull OffsetDateTime dueAt) {
}
