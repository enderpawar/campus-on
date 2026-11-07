package com.campuson.api.schedule;

import com.campuson.api.catalog.Course;
import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.type.SqlTypes;

/** Planary 일정. 개인 일정과 e-class에서 가져온 일정을 한 테이블에 둔다. */
@Entity
@Table(name = "event")
@DynamicUpdate
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", nullable = false, length = 20)
    private EventSource source;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "place", length = 100)
    private String place;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "date_only", nullable = false)
    private boolean dateOnly;

    @Column(name = "done", nullable = false)
    private boolean done;

    @Column(name = "external_uid", length = 200)
    private String externalUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "course_name", length = 300)
    private String courseName;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Event() {
    }

    private Event(Member member, EventType type, EventSource source, String title, String place,
                  Instant startsAt, Instant dueAt, String externalUid, Instant createdAt) {
        validateTime(startsAt, dueAt);
        this.member = member;
        this.type = type;
        this.source = source;
        this.title = title;
        this.place = place;
        this.startsAt = startsAt;
        this.dueAt = dueAt;
        this.externalUid = externalUid;
        this.createdAt = createdAt;
    }

    /** 사용자가 직접 만든 개인 일정. */
    public static Event personal(Member member, String title, String place, Instant startsAt, Instant dueAt, Instant createdAt) {
        return new Event(member, EventType.PERSONAL, EventSource.PERSONAL, title, place, startsAt, dueAt, null, createdAt);
    }

    /** e-class task identity is scoped to its owner by uq_event_external. */
    public static Event imported(Member member, EventType type, String title, Instant dueAt, String externalUid, Instant createdAt) {
        return new Event(member, type, EventSource.ECLASS, title, null, null, dueAt, externalUid, createdAt);
    }

    /** Refresh upstream fields only; user-managed done and the stable event ID are untouched. */
    public void refreshImported(Course course, String courseName, EventType type, String title, Instant startsAt,
                                Instant dueAt, boolean dateOnly, Integer durationSeconds, String sourceUrl) {
        if (source != EventSource.ECLASS) {
            throw new IllegalStateException("Only imported events can be refreshed");
        }
        if (!dateOnly) {
            validateTime(startsAt, dueAt);
        }
        this.course = course;
        this.courseName = courseName;
        this.type = type;
        this.title = title;
        this.startsAt = startsAt;
        this.dueAt = dueAt;
        this.dateOnly = dateOnly;
        this.durationSeconds = durationSeconds;
        this.sourceUrl = sourceUrl;
    }

    /** PUT 전체 교체. e-class 일정은 원본과 어긋나지 않도록 수정을 막는다. */
    public void updatePersonal(String title, String place, Instant startsAt, Instant dueAt) {
        ensureEditable();
        validateTime(startsAt, dueAt);
        this.title = title;
        this.place = place;
        this.startsAt = startsAt;
        this.dueAt = dueAt;
    }

    public void ensureDeletable() {
        ensureEditable();
    }

    public void markDone(boolean done) {
        this.done = done;
    }

    private void ensureEditable() {
        if (source != EventSource.PERSONAL) {
            throw new BusinessException(ErrorCode.EVENT_NOT_EDITABLE);
        }
    }

    /** DB {@code ck_event_time_order}와 함께 이중 방어한다 (Day 14·33). */
    private static void validateTime(Instant startsAt, Instant dueAt) {
        if (startsAt != null && dueAt != null && !startsAt.isBefore(dueAt)) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_TIME);
        }
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public EventType getType() {
        return type;
    }

    public EventSource getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public String getPlace() {
        return place;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public boolean isDone() {
        return done;
    }

    public String getExternalUid() {
        return externalUid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Course getCourse() { return course; }
    public String getCourseName() { return course == null ? courseName : course.getName(); }
    public Integer getDurationSeconds() { return durationSeconds; }
    public String getSourceUrl() { return sourceUrl; }
    public boolean isDateOnly() { return dateOnly; }
}
