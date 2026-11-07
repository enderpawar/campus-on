package com.campuson.api.eclass;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EclassConnectionRepository extends JpaRepository<EclassConnection, Long> {
    /**
     * 재로그인이 필요한 연결(REAUTH_REQUIRED)은 고르지 않는다: 만료 쿠키나 거절된 비밀번호로 학교를 반복 호출하지 않는다.
     * 일시 실패(FAILED)는 더 긴 간격으로만 다시 시도한다. 비활성 회원의 연결도 제외한다.
     */
    @Query("""
            select c.memberId from EclassConnection c
            where ((c.syncState in ('PENDING', 'READY') and c.updatedAt <= :cutoff)
                or (c.syncState = 'FAILED' and c.updatedAt <= :failedCutoff))
              and exists (select m.id from Member m where m.id = c.memberId and m.accountStatus = 'ACTIVE')
            order by c.memberId""")
    List<Long> findDueMemberIds(@Param("cutoff") Instant cutoff, @Param("failedCutoff") Instant failedCutoff);
    /** 상태 반영용 행 잠금. 락 순서는 학교 → 사용자 → 연결이다(연결만 잠그는 경로는 다른 락을 더 잡지 않는다). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EclassConnection c where c.memberId = :memberId")
    Optional<EclassConnection> lockById(@Param("memberId") Long memberId);
}
