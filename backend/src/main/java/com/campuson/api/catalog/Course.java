package com.campuson.api.catalog;

import com.campuson.api.school.School;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "courses")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;
    @Column(name = "course_type", nullable = false, length = 10)
    private String courseType;
    @Column(name = "course_name", nullable = false, length = 300)
    private String name;
    @Column(name = "legacy_kjkey", nullable = false, length = 40)
    private String externalId;
    @Column(name = "course_status", nullable = false, length = 12)
    private String status = "OPEN";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Course() {}

    public static Course collected(School school, String externalId, String name, Instant now) {
        Course course = new Course();
        course.school = school;
        course.externalId = externalId;
        course.name = name;
        // ILOS nonregular KJ keys use the explicit 비 prefix; no year/term is inferred.
        course.courseType = externalId.startsWith("비") ? "비정규" : "정규";
        course.createdAt = now;
        return course;
    }

    public void refresh(String name, Instant now) {
        if (name != null && !name.isBlank()) this.name = name; // A missing name never erases a known one.
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getExternalId() { return externalId; }
    public String getCourseType() { return courseType; }
    public String getStatus() { return status; }
}
