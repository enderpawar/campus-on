package com.campuson.api.member;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** fetch join으로 학교까지 한 번에 읽는다 (Day 19). */
    @Transactional(readOnly = true)
    public MemberResponse me(long memberId) {
        return memberRepository.findWithSchoolById(memberId)
                .map(MemberResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /** 보낸 필드만 바꾼다. save() 없이 dirty checking으로 반영한다(본인 프로필이라 동시 수정 경합은 마지막 요청이 이긴다). */
    @Transactional
    public MemberResponse updateProfile(long memberId, UpdateProfileRequest request) {
        Member member = memberRepository.findWithSchoolById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (request.hasDepartment()) {
            member.changeDepartment(request.department());
        }
        if (request.hasGrade()) {
            member.changeGrade(request.grade());
        }
        return MemberResponse.from(member);
    }
}
