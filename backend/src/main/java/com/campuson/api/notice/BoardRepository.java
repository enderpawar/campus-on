package com.campuson.api.notice;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, Long> {
    Optional<Board> findBySchoolIdAndName(Long schoolId, String name);
}
