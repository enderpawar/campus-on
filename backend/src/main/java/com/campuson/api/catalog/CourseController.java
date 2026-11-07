package com.campuson.api.catalog;

import com.campuson.api.global.security.AuthMember;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseController {
    private final CourseEnrollmentRepository enrollments;

    public CourseController(CourseEnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @GetMapping("/api/courses")
    @Transactional(readOnly = true)
    public List<CourseResponse> list(@AuthenticationPrincipal AuthMember me) {
        return enrollments.findCoursesByMemberId(me.memberId()).stream()
                .map(c -> new CourseResponse(c.getId(), c.getExternalId(), c.getName(), c.getCourseType(), c.getStatus()))
                .toList();
    }

    public record CourseResponse(Long id, String externalId, String name, String courseType, String status) {}
}
