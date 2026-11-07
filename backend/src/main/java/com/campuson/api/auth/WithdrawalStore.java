package com.campuson.api.auth;

import com.campuson.api.catalog.CourseEnrollmentRepository;
import com.campuson.api.eclass.EclassConnectionRepository;
import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.member.Member;
import com.campuson.api.member.MemberRepository;
import com.campuson.api.schedule.CalendarFeedRepository;
import com.campuson.api.schedule.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All app-owned private records are removed together; shared courses, boards and posts remain. */
@Service
public class WithdrawalStore {
    private final MemberRepository members;
    private final com.campuson.api.eclass.ConnectionCoordination coordination;
    private final EventRepository events;
    private final CalendarFeedRepository feeds;
    private final EclassConnectionRepository connections;
    private final CourseEnrollmentRepository enrollments;
    public WithdrawalStore(MemberRepository members, EventRepository events, CalendarFeedRepository feeds,
            EclassConnectionRepository connections, CourseEnrollmentRepository enrollments, com.campuson.api.eclass.ConnectionCoordination coordination) {
        this.members = members; this.events = events; this.feeds = feeds; this.connections = connections;
        this.enrollments = enrollments; this.coordination = coordination;
    }
    public void delete(long memberId) {
        coordination.mutate(() -> {
        Member member = members.lockById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        coordination.assertMutable(memberId);
        events.deleteAllByMemberId(memberId);
        feeds.deleteAllByMemberId(memberId);
        enrollments.deleteAllByUserId(memberId);
        connections.deleteById(memberId);
        members.delete(member);
        return null; });
    }
}
