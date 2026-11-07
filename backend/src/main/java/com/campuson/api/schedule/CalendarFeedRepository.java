package com.campuson.api.schedule;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarFeedRepository extends JpaRepository<CalendarFeed, Long> {

    Optional<CalendarFeed> findByMemberId(Long memberId);

    /** 회원 엔티티를 읽지 않고 id만 가져온다. 비활성(레거시 SUSPENDED 등) 회원의 피드는 존재하지 않는 것처럼 404다. */
    @Query("select f.member.id from CalendarFeed f where f.token = :token and f.member.accountStatus = 'ACTIVE'")
    Optional<Long> findMemberIdByToken(@Param("token") String token);

    /** 탈퇴용. 삭제되면 그 회원의 ICS 주소는 바로 404가 된다. */
    @Modifying
    @Query("delete from CalendarFeed f where f.member.id = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);
}
