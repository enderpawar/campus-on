package com.campuson.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import com.campuson.api.catalog.CourseEnrollmentRepository;
import com.campuson.api.eclass.*;
import com.campuson.api.member.Member;
import com.campuson.api.member.MemberRepository;
import com.campuson.api.schedule.*;
import com.campuson.api.school.SchoolRepository;
import com.campuson.api.support.ApiTestClient;
import com.campuson.api.support.EclassTestConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(EclassTestConfiguration.class)
class WithdrawalRollbackTest {
    @Autowired AuthService auth;
    @Autowired WithdrawalService withdrawal;
    @Autowired EventService events;
    @Autowired CalendarFeedService feeds;
    @Autowired SchoolRepository schools;
    @Autowired EventRepository eventRepository;
    @Autowired CalendarFeedRepository feedRepository;
    @Autowired EclassAccountService accounts;
    @Autowired EclassConnectionRepository connections;
    @Autowired CourseEnrollmentRepository enrollments;
    @MockitoSpyBean MemberRepository members;
    @Test void exactFailureAfterPrivateDeletionRestoresEventsFeedCredentialsAndEnrollment() {
        String studentId = "student-" + UUID.randomUUID();
        auth.login(new LoginRequest(studentId, ApiTestClient.PASSWORD, true), null);
        long schoolId = schools.findByName(ApiTestClient.SEOULTECH).orElseThrow().getId();
        long memberId = members.findByExternalRefAndSchoolId(studentId, schoolId).orElseThrow().getId();
        long eventId = events.create(memberId, new CreateEventRequest("보존할 일정", null, null,
                OffsetDateTime.parse("2099-05-01T09:00:00+09:00"))).id();
        feeds.issue(memberId);
        accounts.synchronizedSnapshot(memberId, accounts.context(memberId).generation(), new EclassSnapshot(List.of(new EclassCourse("r-" + UUID.randomUUID(), "테스트 과목")), List.of()), "SESSION=" + studentId);
        String encryptedPassword = connections.findById(memberId).orElseThrow().getEncryptedPassword();
        long courseId = enrollments.findCoursesByMemberId(memberId).getFirst().getId();
        InjectedFailure injected = new InjectedFailure();
        AtomicBoolean privateDataGoneInsideTx = new AtomicBoolean();
        doAnswer(invocation -> {
            privateDataGoneInsideTx.set(!eventRepository.existsById(eventId)
                    && feedRepository.findByMemberId(memberId).isEmpty()
                    && !connections.existsById(memberId)
                    && enrollments.findCoursesByMemberId(memberId).isEmpty());
            throw injected;
        }).when(members).delete(any(Member.class));
        InjectedFailure thrown = assertThrows(InjectedFailure.class,
                () -> withdrawal.withdraw(memberId, new WithdrawRequest(ApiTestClient.PASSWORD), null, null));
        assertSame(injected, thrown, "다른 원인으로 통과할 수 없어야 한다");
        assertTrue(privateDataGoneInsideTx.get(), "실패 전 DB 조회에서 모든 전용 데이터가 삭제되어야 한다");
        assertTrue(eventRepository.existsById(eventId));
        assertTrue(feedRepository.findByMemberId(memberId).isPresent());
        assertEquals(encryptedPassword, connections.findById(memberId).orElseThrow().getEncryptedPassword());
        assertEquals(courseId, enrollments.findCoursesByMemberId(memberId).getFirst().getId());
        assertTrue(members.existsById(memberId));
    }
    private static final class InjectedFailure extends RuntimeException {}
}
