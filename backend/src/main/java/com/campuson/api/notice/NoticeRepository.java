package com.campuson.api.notice;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findAllBySchoolId(Long schoolId, Pageable pageable);

    Page<Notice> findAllBySchoolIdAndCategory(Long schoolId, NoticeCategory category, Pageable pageable);

    Optional<Notice> findByIdAndSchoolId(Long id, Long schoolId);
}
