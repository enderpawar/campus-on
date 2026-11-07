package com.campuson.api.schedule;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캘린더 앱이 Authorization 없이 가져가는 ICS 피드. 경로의 토큰이 인증 수단이다(SecurityConfig permitAll {@code /ics/*}).
 * 토큰은 요청 로그에서 {@code /ics/***.ics}로 가려진다(RequestIdFilter).
 */
@RestController
public class IcsController {

    private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final CalendarFeedService calendarFeedService;

    public IcsController(CalendarFeedService calendarFeedService) {
        this.calendarFeedService = calendarFeedService;
    }

    @GetMapping("/ics/{token:[A-Za-z0-9_-]{20,64}}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        String ics = calendarFeedService.render(token);
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ics);
    }
}
