package com.campuson.api.schedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /** 남의 일정은 존재하지 않는 것처럼 다룬다(404). */
    Optional<Event> findByIdAndMemberId(Long id, Long memberId);

    Optional<Event> findByMemberIdAndSourceAndExternalUid(Long memberId, EventSource source, String externalUid);

    @Query("select e from Event e left join fetch e.course where e.member.id = :memberId and e.dueAt is null order by e.id asc")
    List<Event> findUndated(@Param("memberId") Long memberId);

    @Query("select e from Event e left join fetch e.course where e.member.id = :memberId and e.dueAt >= :from and e.dueAt < :to order by e.dueAt asc, e.id asc")
    List<Event> findInRange(@Param("memberId") Long memberId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select e from Event e where e.member.id = :memberId and e.dueAt >= :from order by e.dueAt asc, e.id asc")
    List<Event> findFrom(@Param("memberId") Long memberId, @Param("from") Instant from);

    /** 탈퇴용 일괄 삭제. 엔티티를 하나씩 읽지 않고 DELETE 한 번으로 지운다. */
    @Modifying
    @Query("delete from Event e where e.member.id = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);
}
