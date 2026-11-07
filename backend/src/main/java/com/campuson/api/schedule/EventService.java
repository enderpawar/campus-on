package com.campuson.api.schedule;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.global.time.CampusTime;
import com.campuson.api.member.Member;
import com.campuson.api.member.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private static final long MAX_RANGE_DAYS = 62;

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public EventService(EventRepository eventRepository, MemberRepository memberRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /** 마감일 기준 [from 00:00 KST, to+1일 00:00 KST) 구간의 일정을 돌려준다. */
    @Transactional(readOnly = true)
    public List<EventResponse> list(long memberId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        // to 날짜도 포함하므로 포함 일수(between + 1)로 상한을 센다
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "조회 기간은 최대 62일입니다.");
        }
        Instant start = from.atStartOfDay(CampusTime.ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(CampusTime.ZONE).toInstant();
        return eventRepository.findInRange(memberId, start, end).stream()
                .map(e -> EventResponse.of(e, clock))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> undated(long memberId) {
        return eventRepository.findUndated(memberId).stream()
                .map(event -> EventResponse.of(event, clock)).toList();
    }

    @Transactional
    public EventResponse create(long memberId, CreateEventRequest request) {
        // 프록시만 만들고 SELECT는 하지 않는다 (Day 18). FK는 INSERT 시점에 DB가 검증한다.
        Member member = memberRepository.getReferenceById(memberId);
        Event event = Event.personal(member, requireTitle(request.title()), request.place(),
                toInstant(request.startsAt()), request.dueAt().toInstant(), Instant.now(clock));
        return EventResponse.of(eventRepository.save(event), clock);
    }

    @Transactional
    public EventResponse update(long memberId, long eventId, UpdateEventRequest request) {
        Event event = findOwned(memberId, eventId);
        event.updatePersonal(requireTitle(request.title()), request.place(),
                toInstant(request.startsAt()), request.dueAt().toInstant());
        return EventResponse.of(event, clock);
    }

    @Transactional
    public EventResponse markDone(long memberId, long eventId, boolean done) {
        Event event = findOwned(memberId, eventId);
        // save()를 부르지 않는다: 커밋 시 dirty checking이 UPDATE를 만든다 (Day 12·15).
        event.markDone(done);
        return EventResponse.of(event, clock);
    }

    @Transactional
    public void delete(long memberId, long eventId) {
        Event event = findOwned(memberId, eventId);
        event.ensureDeletable();
        eventRepository.delete(event);
    }

    /** 없는 일정과 남의 일정을 구분하지 않고 404로 숨긴다. */
    private Event findOwned(long memberId, long eventId) {
        return eventRepository.findByIdAndMemberId(eventId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    }

    /** {@code @NotBlank}는 trim() 기준이라 U+3000 같은 유니코드 공백만 있는 제목을 통과시킨다: strip() 결과로 다시 검사한다. */
    private static String requireTitle(String title) {
        String stripped = title.strip();
        if (stripped.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "제목을 입력해 주세요.");
        }
        return stripped;
    }

    private static Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
