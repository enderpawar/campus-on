package com.campuson.api.catalog;

import com.campuson.api.member.Member;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "course_enrollments")
public class CourseEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enrollment_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Member member;
    @Column(name = "enrollment_kind", nullable = false, length = 12)
    private String kind;
    @Column(name = "enrollment_role", nullable = false, length = 12)
    private String role = "STUDENT";
    @Column(name = "enrollment_status", nullable = false, length = 12)
    private String status = "ENROLLED";
    @Column(name = "source", nullable = false, length = 12)
    private String source = "SYSTEM";
    @Column(name = "external_sync_at", nullable = false)
    private Instant externalSyncAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CourseEnrollment() {}

    public static CourseEnrollment collected(Member member, Course course, Instant now) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.member = member;
        enrollment.course = course;
        enrollment.kind = "비정규".equals(course.getCourseType()) ? "NONREGULAR" : "REGISTRAR";
        enrollment.externalSyncAt = now;
        enrollment.createdAt = now;
        return enrollment;
    }

    public void refresh(Instant now) { this.externalSyncAt = now; }
    public Course getCourse() { return course; }
}
