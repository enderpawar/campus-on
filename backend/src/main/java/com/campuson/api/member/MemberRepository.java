package com.campuson.api.member;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByExternalRefAndSchoolId(String externalRef, Long schoolId);
    @Query("select new com.campuson.api.member.AuthSnapshot(m.id, m.school.id, m.tokenVersion) from Member m where m.id = :id and m.accountStatus = 'ACTIVE'")
    Optional<AuthSnapshot> findAuthSnapshot(@Param("id") Long id);
    @Query("select m from Member m join fetch m.school where m.id = :id")
    Optional<Member> findWithSchoolById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> lockById(@Param("id") Long id);
    /**
     * 로그인용. 처음 읽을 때부터 행을 잠가 최신 token_version을 읽는다. 일반 조회 뒤에 잠그면 영속성 컨텍스트의
     * 엔티티가 갱신되지 않아, 그 사이 커밋된 로그아웃의 버전 증가를 놓친 토큰을 발급한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.externalRef = :externalRef and m.school.id = :schoolId")
    Optional<Member> lockByExternalRefAndSchoolId(@Param("externalRef") String externalRef, @Param("schoolId") Long schoolId);
    @Modifying
    @Query("update Member m set m.tokenVersion = m.tokenVersion + 1 where m.id = :id and m.accountStatus = 'ACTIVE'")
    int increaseTokenVersion(@Param("id") Long id);
}
