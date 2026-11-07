package com.campuson.api.schedule;

import com.campuson.api.catalog.Course;
import com.campuson.api.catalog.CourseEnrollment;
import com.campuson.api.catalog.CourseEnrollmentRepository;
import com.campuson.api.catalog.CourseRepository;
import com.campuson.api.eclass.EclassCourse;
import com.campuson.api.eclass.EclassSnapshot;
import com.campuson.api.eclass.EclassTask;
import com.campuson.api.member.Member;
import com.campuson.api.school.School;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EclassImportService {
    private final CourseRepository courses;
    private final CourseEnrollmentRepository enrollments;
    private final EventRepository events;
    private final EntityManager entityManager;
    private final Clock clock;
    private static final int MAX_KJKEY = 40; // courses.legacy_kjkey VARCHAR(40)

    public EclassImportService(CourseRepository courses, CourseEnrollmentRepository enrollments,
                               EventRepository events, EntityManager entityManager, Clock clock) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.events = events;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Atomic with caller's sync state. Missing items never mean removed/completed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Member member, EclassSnapshot snapshot) {
        // A course is shared by users. Serialize the short DB-only upsert across a school;
        // a per-user sync lock alone cannot protect first imports of the same course.
        School school = entityManager.find(School.class, member.getSchool().getId(), LockModeType.PESSIMISTIC_WRITE);
        Instant now = Instant.now(clock);
        Map<String, Course> collected = new HashMap<>();
        for (EclassCourse incoming : snapshot.courses()) {
            Course course = upsertCourse(member, school, incoming.externalId(), incoming.name(), now);
            if (course != null) collected.put(incoming.externalId(), course);
        }
        for (EclassTask incoming : snapshot.tasks()) {
            Course course = collected.get(incoming.courseExternalId());
            if (course == null) {
                course = upsertCourse(member, school, incoming.courseExternalId(), incoming.courseName(), now);
                if (course != null) collected.put(incoming.courseExternalId(), course);
            }
            EventType type = EventType.valueOf(incoming.type().toUpperCase(Locale.ROOT));
            String externalUid = ImportLimits.externalUid(incoming.externalId());
            String title = ImportLimits.truncate(incoming.title(), ImportLimits.TITLE);
            String courseName = ImportLimits.truncate(incoming.courseName(), ImportLimits.COURSE_NAME);
            Event event = events.findByMemberIdAndSourceAndExternalUid(member.getId(), EventSource.ECLASS, externalUid)
                    .orElseGet(() -> events.save(Event.imported(member, type, title, incoming.dueAt(), externalUid, now)));
            for (String alias : incoming.mergedExternalIds()) {
                String uid = ImportLimits.externalUid(alias);
                if (externalUid.equals(uid)) continue;
                events.findByMemberIdAndSourceAndExternalUid(member.getId(), EventSource.ECLASS, uid).ifPresent(duplicate -> {
                    if (duplicate.isDone()) event.markDone(true);
                    events.delete(duplicate);
                });
            }
            event.refreshImported(course, courseName, type, title, incoming.startsAt(), incoming.dueAt(),
                    incoming.dateOnly(), incoming.durationSeconds(), ImportLimits.url(incoming.sourceUrl()));
        }
    }

    /** Returns null when the course is unknown and has no name: a course row is never created with a null name. */
    private Course upsertCourse(Member member, School school, String externalId, String name, Instant now) {
        if (externalId == null || externalId.length() > MAX_KJKEY) return null;
        String fitted = name == null || name.isBlank() ? null : ImportLimits.truncate(name, ImportLimits.COURSE_NAME);
        Course course = courses.findBySchoolIdAndExternalId(school.getId(), externalId).orElse(null);
        if (course == null) {
            if (fitted == null) return null;
            course = courses.save(Course.collected(school, externalId, fitted, now));
        }
        course.refresh(fitted, now);
        upsertEnrollment(member, course, now);
        return course;
    }

    private void upsertEnrollment(Member member, Course course, Instant now) {
        CourseEnrollment enrollment = enrollments.findByMemberIdAndCourseId(member.getId(), course.getId())
                .orElseGet(() -> enrollments.save(CourseEnrollment.collected(member, course, now)));
        enrollment.refresh(now);
    }
}
