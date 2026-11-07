package com.campuson.api.schedule;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.member.MemberRepository;
import com.campuson.api.schedule.ics.IcsEvent;
import com.campuson.api.schedule.ics.IcsWriter;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자별 ICS 구독 피드. 토큰이 곧 인증 수단이라 추측할 수 없는 난수로 만들고, 재발급하면 이전 주소는 즉시 무효가 된다. */
@Service
public class CalendarFeedService {

    static final String CALENDAR_NAME = "캠퍼스 온 일정";
    private static final int TOKEN_BYTES = 32;
    private static final Duration PAST_WINDOW = Duration.ofDays(30);

    private final SecureRandom secureRandom = new SecureRandom();
    private final CalendarFeedRepository calendarFeedRepository;
    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final String publicBaseUrl;

    public CalendarFeedService(CalendarFeedRepository calendarFeedRepository,
                               EventRepository eventRepository,
                               MemberRepository memberRepository,
                               Clock clock,
                               @Value("${campus.public-base-url}") String publicBaseUrl) {
        this.calendarFeedRepository = calendarFeedRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 피드가 있으면 토큰을 교체하고(dirty checking), 없으면 새로 만든다. */
    @Transactional
    public CalendarFeedResponse issue(long memberId) {
        String token = newToken();
        calendarFeedRepository.findByMemberId(memberId).ifPresentOrElse(
                feed -> feed.rotate(token),
                () -> calendarFeedRepository.save(
                        new CalendarFeed(memberRepository.getReferenceById(memberId), token, Instant.now(clock))));
        return toResponse(token);
    }

    @Transactional(readOnly = true)
    public CalendarFeedResponse current(long memberId) {
        return calendarFeedRepository.findByMemberId(memberId)
                .map(feed -> toResponse(feed.getToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_FEED_NOT_FOUND));
    }

    /** 30일 전 이후 마감 일정 전체(완료 포함)를 ICS로 만든다. */
    @Transactional(readOnly = true)
    public String render(String token) {
        Long memberId = calendarFeedRepository.findMemberIdByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_FEED_NOT_FOUND));
        Instant now = Instant.now(clock);
        List<IcsEvent> events = eventRepository.findFrom(memberId, now.minus(PAST_WINDOW)).stream()
                .map(CalendarFeedService::toIcsEvent)
                .toList();
        return IcsWriter.write(CALENDAR_NAME, events, now);
    }

    private static IcsEvent toIcsEvent(Event event) {
        boolean hasStart = !event.isDateOnly() && event.getStartsAt() != null;
        return new IcsEvent(
                "event-" + event.getId() + "@campus-on",
                event.getTitle(),
                event.getPlace(),
                hasStart ? event.getStartsAt() : event.getDueAt(),
                hasStart ? event.getDueAt() : null,
                event.isDateOnly());
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private CalendarFeedResponse toResponse(String token) {
        return new CalendarFeedResponse(publicBaseUrl + "/ics/" + token + ".ics");
    }
}
