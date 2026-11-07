package com.campuson.api.notice;

import com.campuson.api.global.security.AuthMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 학교는 요청 파라미터가 아니라 토큰의 회원에서 정한다. 다른 학교 공지는 보이지 않는다. */
    @GetMapping
    public NoticePageResponse list(@AuthenticationPrincipal AuthMember me,
                                   @RequestParam(required = false) NoticeCategory category,
                                   // 상한: page*size가 JPA offset(int) 범위를 넘으면 500이 나므로 요청 단계에서 400으로 막는다
                                   @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return noticeService.list(me.schoolId(), category, page, size);
    }

    @GetMapping("/{id}")
    public NoticeResponse get(@AuthenticationPrincipal AuthMember me, @PathVariable @Positive Long id) {
        return noticeService.get(me.schoolId(), id);
    }
}
