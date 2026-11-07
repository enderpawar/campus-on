package com.campuson.api.member;

import com.campuson.api.school.School;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/** ERD users identity, provisioned only after successful e-class authentication. */
@Entity
@Table(name = "users")
@DynamicUpdate
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false, updatable = false) private School school;
    @Column(name = "external_ref", nullable = false, length = 64, updatable = false) private String externalRef;
    @Column(name = "user_name", length = 100) private String name;
    @Column(name = "department_name", length = 200) private String department;
    @Column(name = "grade") private Integer grade;
    @Column(name = "user_role", nullable = false, length = 20) private String role;
    @Column(name = "account_status", nullable = false, length = 20) private String accountStatus;
    @Column(name = "token_version", nullable = false) private int tokenVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "last_synced_at") private Instant lastSyncedAt;
    protected Member() {}
    public Member(School school, String studentId, String name, String department, Integer grade, Instant now) {
        this.school = school; this.externalRef = studentId; this.name = name; this.department = department;
        this.grade = grade; this.role = "STUDENT"; this.accountStatus = "ACTIVE"; this.createdAt = now;
        this.lastSyncedAt = now;
    }
    public void refreshProfile(String name, String department, Integer grade, Instant now) {
        if (name != null && !name.isBlank()) this.name = name;
        if (department != null) this.department = department;
        if (grade != null && grade >= 1 && grade <= 6) this.grade = grade;
        this.lastSyncedAt = now;
    }
    public void changeDepartment(String department) { this.department = department; }
    public void changeGrade(Integer grade) { this.grade = grade; }
    public boolean isActive() { return "ACTIVE".equals(accountStatus); }
    public Long getId() { return id; }
    public School getSchool() { return school; }
    public String getExternalRef() { return externalRef; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public Integer getGrade() { return grade; }
    public int getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
