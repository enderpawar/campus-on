package com.campuson.api.notice;

import com.campuson.api.school.School;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "boards")
public class Board {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;
    @Column(name = "board_type", nullable = false, length = 20)
    private String type = "K001";
    @Column(name = "board_name", nullable = false, length = 100)
    private String name;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Board() {}

    public static Board collected(School school, String source) {
        Board board = new Board();
        board.school = school;
        board.name = source;
        board.createdAt = Instant.now();
        return board;
    }

    public Long getId() { return id; }
    public School getSchool() { return school; }
    public String getName() { return name; }
}
