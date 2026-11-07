package com.campuson.api.member;

/** Nullable profile fields mean e-class did not supply them; no invented academic data. */
public record MemberResponse(Long id, String studentId, String name, String department, Integer grade,
                             Long schoolId, String schoolName) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getExternalRef(), member.getName(),
                member.getDepartment(), member.getGrade(), member.getSchool().getId(), member.getSchool().getName());
    }
}
