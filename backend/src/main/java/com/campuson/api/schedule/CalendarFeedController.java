package com.campuson.api.schedule;

import com.campuson.api.global.security.AuthMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ICS 구독 주소 발급(재발급 시 교체)과 현재 주소 조회. */
@RestController
@RequestMapping("/api/calendar-feed")
public class CalendarFeedController {

    private final CalendarFeedService calendarFeedService;

    public CalendarFeedController(CalendarFeedService calendarFeedService) {
        this.calendarFeedService = calendarFeedService;
    }

    @PostMapping
    public CalendarFeedResponse issue(@AuthenticationPrincipal AuthMember me) {
        return calendarFeedService.issue(me.memberId());
    }

    @GetMapping
    public CalendarFeedResponse current(@AuthenticationPrincipal AuthMember me) {
        return calendarFeedService.current(me.memberId());
    }
}
