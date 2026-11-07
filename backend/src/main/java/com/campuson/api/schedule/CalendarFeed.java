package com.campuson.api.schedule;

import com.campuson.api.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** 회원별 ICS 구독 주소. 토큰이 곧 인증 수단이므로 회원당 1개만 두고 재발급 시 교체한다. */
@Entity
@Table(name = "calendar_feed")
public class CalendarFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "token", nullable = false, length = 64)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarFeed() {
    }

    public CalendarFeed(Member member, String token, Instant createdAt) {
        this.member = member;
        this.token = token;
        this.createdAt = createdAt;
    }

    /** 토큰을 바꾼다. 이전 구독 주소는 즉시 404가 된다. */
    public void rotate(String newToken) {
        this.token = newToken;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
