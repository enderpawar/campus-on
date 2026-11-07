package com.campuson.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {
    Optional<CourseEnrollment> findByMemberIdAndCourseId(Long memberId, Long courseId);

    @Query("select e.course from CourseEnrollment e where e.member.id = :memberId order by e.course.name, e.course.id")
    List<Course> findCoursesByMemberId(@Param("memberId") Long memberId);

    @Modifying
    @Query("delete from CourseEnrollment e where e.member.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
