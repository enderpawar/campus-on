package com.campuson.api.schedule;

import com.campuson.api.global.security.AuthMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Planary 일정 API. {@code @Positive} 경로 변수는 Spring 6.1 메서드 검증이 처리하므로
 * 클래스에 {@code @Validated}를 붙이지 않는다(위반 시 HandlerMethodValidationException → 400).
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventResponse> list(@AuthenticationPrincipal AuthMember me,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return eventService.list(me.memberId(), from, to);
    }

    @GetMapping("/undated")
    public List<EventResponse> undated(@AuthenticationPrincipal AuthMember me) {
        return eventService.undated(me.memberId());
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@AuthenticationPrincipal AuthMember me,
                                                @Valid @RequestBody CreateEventRequest request) {
        EventResponse created = eventService.create(me.memberId(), request);
        return ResponseEntity.created(URI.create("/api/events/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public EventResponse update(@AuthenticationPrincipal AuthMember me,
                                @PathVariable @Positive Long id,
                                @Valid @RequestBody UpdateEventRequest request) {
        return eventService.update(me.memberId(), id, request);
    }

    @PatchMapping("/{id}/done")
    public EventResponse markDone(@AuthenticationPrincipal AuthMember me,
                                  @PathVariable @Positive Long id,
                                  @Valid @RequestBody UpdateEventDoneRequest request) {
        return eventService.markDone(me.memberId(), id, request.done());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me,
                                       @PathVariable @Positive Long id) {
        eventService.delete(me.memberId(), id);
        return ResponseEntity.noContent().build();
    }
}
