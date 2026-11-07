package com.campuson.api.member;

import com.campuson.api.global.security.AuthMember;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/api/me")
    public MemberResponse me(@AuthenticationPrincipal AuthMember me) {
        return memberService.me(me.memberId());
    }

    /** 학과·학년 수정. 생략한 필드는 유지, null은 비우기. */
    @PatchMapping("/api/me")
    public MemberResponse updateProfile(@AuthenticationPrincipal AuthMember me,
                                        @Valid @RequestBody UpdateProfileRequest request) {
        return memberService.updateProfile(me.memberId(), request);
    }
}
