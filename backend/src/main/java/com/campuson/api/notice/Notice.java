package com.campuson.api.notice;

import com.campuson.api.school.School;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.OrderColumn;
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
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 학교 공지. 쓰기는 F6 수집기만 하고 API는 조회만 제공한다. */
@Entity
@Table(name = "posts")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(name = "post_kind", nullable = false, length = 20)
    private String postKind = "NOTICE";

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", nullable = false, length = 20)
    private NoticeCategory category;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "excerpt", length = 1000)
    private String summary;

    @Column(name = "body_text", columnDefinition = "text")
    private String body;

    @ElementCollection
    @CollectionTable(name = "post_target_departments", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "department_name", nullable = false, length = 200)
    @OrderColumn(name = "sort_order")
    private List<String> targetDepartments = new ArrayList<>();

    @Column(name = "target_grade")
    private Integer targetGrade;

    @Column(name = "published_at", nullable = false)
    private Instant postedAt;

    protected Notice() {
    }

    /**
     * The collector supplies plain text, not unsanitized HTML. Newlines, paragraphs
     * and list markers are stored verbatim; unavailable body stays null.
     */
    public static Notice collected(Board board, NoticeCategory category, String title, String url,
                                   String body, String excerpt, Integer targetGrade,
                                   List<String> targetDepartments, Instant postedAt) {
        Notice notice = new Notice();
        notice.board = board;
        notice.school = board.getSchool();
        notice.source = board.getName();
        notice.category = category;
        notice.title = title;
        notice.url = url;
        notice.body = body;
        notice.summary = excerpt;
        notice.targetGrade = targetGrade;
        notice.targetDepartments = new ArrayList<>(targetDepartments.stream().distinct().toList());
        notice.postedAt = postedAt;
        return notice;
    }

    public Long getId() { return id; }
    public School getSchool() { return school; }
    public String getSource() { return source; }
    public NoticeCategory getCategory() { return category; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getSummary() { return summary; }
    public Integer getTargetGrade() { return targetGrade; }
    public Instant getPostedAt() { return postedAt; }
    public String getBody() { return body; }
    public List<String> getTargetDepartments() { return List.copyOf(targetDepartments); }
}
