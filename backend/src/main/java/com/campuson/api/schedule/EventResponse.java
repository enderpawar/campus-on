package com.campuson.api.schedule;

import com.campuson.api.global.time.CampusTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 일정 응답. {@code dDay}는 KST 기준 오늘과 마감일의 날짜 차이다.
 * 0은 오늘 마감, 양수는 남은 날 수, 음수는 지난 날 수.
 */
public record EventResponse(
        Long id,
        EventType type,
        EventSource source,
        String title,
        String place,
        OffsetDateTime startsAt,
        OffsetDateTime dueAt,
        boolean dateOnly,
        boolean done,
        Long dDay,
        Long courseId,
        String courseName,
        Integer durationSeconds,
        String sourceUrl) {

    /** Called inside a transaction; course is fetched with calendar queries. */
    public static EventResponse of(Event event, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(CampusTime.ZONE));
        LocalDate dueDate = event.getDueAt() == null ? null : event.getDueAt().atZone(CampusTime.ZONE).toLocalDate();
        return new EventResponse(
                event.getId(),
                event.getType(),
                event.getSource(),
                event.getTitle(),
                event.getPlace(),
                CampusTime.toOffset(event.getStartsAt()),
                CampusTime.toOffset(event.getDueAt()),
                event.isDateOnly(),
                event.isDone(),
                dueDate == null ? null : ChronoUnit.DAYS.between(today, dueDate),
                event.getCourse() == null ? null : event.getCourse().getId(),
                event.getCourseName(),
                event.getDurationSeconds(),
                event.getSourceUrl());
    }
}
