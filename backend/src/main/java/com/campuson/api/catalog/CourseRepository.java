package com.campuson.api.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findBySchoolIdAndExternalId(Long schoolId, String externalId);
}
