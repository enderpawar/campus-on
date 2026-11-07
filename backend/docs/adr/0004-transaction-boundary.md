# ADR-0004 트랜잭션 경계, OSIV off, 서비스 안 DTO 변환

## 상태
채택

## 맥락
트랜잭션 경계가 흐리면 LAZY 프록시가 뷰 렌더링 시점에 초기화되거나, 실패한 트랜잭션을 커밋하려다 `UnexpectedRollbackException`이 나는 문제가 생긴다. 스터디에서 이 문제들을 하나씩 직접 재현했다.

## 결정
1. **DB 경계는 서비스 메서드다.** 쓰기는 `@Transactional`, 조회는 `@Transactional(readOnly = true)`. HTTP 인증·수집을 조정하는 서비스 자체에는 트랜잭션을 걸지 않는다. 컨트롤러는 경계를 소유하지 않는다.
2. **OSIV off:** `application.yml`의 `spring.jpa.open-in-view: false`. 영속성 컨텍스트는 서비스 트랜잭션이 끝나면 닫힌다.
3. **DTO 변환은 조회 트랜잭션 안에서 한다.** 예: `MemberService.me` → `MemberResponse.from(...)`, `EventService.markDone` → `EventResponse.of(event, clock)`. 컨트롤러는 엔티티를 보지 않는다.
4. **save() 없이 dirty checking:** 이미 영속 상태인 엔티티를 한 요청만 바꾸는 경우에는 상태만 바꾼다.
   - `EventService.markDone`: `event.markDone(done)`만 호출한다.
   - `CalendarFeedService.issue`: 피드가 있으면 `feed.rotate(newToken)`만 호출한다.
   - 코드 주석에 근거(Day 12·15)를 남겼다. `EventHttpTest.markDonePersistsWithoutExplicitSave`가 PATCH 후 다시 조회해 반영을 확인한다.
   - **공유 카운터에는 쓰지 않는다.** 처음에는 `AuthService.logout`도 `member.increaseTokenVersion()`(읽고 ++ 후 커밋)으로 dirty checking에 맡겼다. Codex 리뷰(#1)가 지적했듯 이것은 read-modify-write라서, 두 기기의 로그아웃이 같은 버전을 읽으면 둘 다 `v+1`을 써서 증가 하나가 사라진다. 그 사이 발급된 토큰이 살아남을 수 있다. 지금은 `MemberRepository.increaseTokenVersion(id)`(`@Modifying` `update Member m set m.tokenVersion = m.tokenVersion + 1 where m.id = :id`)가 DB에서 원자적으로 증가시키고, 갱신 행이 0이면 `UNAUTHORIZED`다. `Member.increaseTokenVersion()`은 삭제했다.
   - 교훈: Day 12·15의 지식(스냅샷 비교 후 flush 시 UPDATE)은 맞았지만 불완전했다. dirty checking은 "한 쓰기 주체의 상태 변경"에는 맞고, 동시 쓰기가 있는 값에는 원자적 UPDATE나 락이 필요하다. `auth/LogoutConcurrencyTest.concurrentLogoutsNeverLoseAnIncrement`(8스레드 → 버전 8)가 이를 고정한다. 옛 read-modify-write 코드를 임시로 되살리고 50ms sleep으로 경합 구간을 넓혀 돌렸을 때 이 테스트는 실패(증가 유실)했고, 원자적 UPDATE에서는 통과했다. 임시 코드는 제거했다.
5. **SELECT 없는 연관:** `EventService.create`는 `memberRepository.getReferenceById(memberId)`로 회원 프록시만 얻는다. 인증 필터가 이미 회원 존재를 확인했으므로 FK만 있으면 된다.
6. **DB 서비스 안에서 무결성 예외를 삼키지 않는다.** 실패한 저장 트랜잭션은 롤백한 뒤 경계 밖에서 처리한다. 일반 요청의 `DataIntegrityViolationException`은 전역 핸들러가 409 `DATA_CONFLICT`로 변환한다. 수집 조정 서비스는 저장 실패까지 연결 실패 상태로 기록하고 502를 반환한다.
7. **탈퇴는 외부 인증과 원자적 삭제를 분리한다.** `WithdrawalService.withdraw`가 트랜잭션 밖에서 현재 학교 비밀번호를 확인한다. 성공 후 별도 빈 `WithdrawalStore.delete`의 한 `@Transactional`에서 사용자 락 → 일정 → ICS → 수강 소속 → 연결 비밀 → 사용자를 삭제한다. 공유 학교·과목·공지는 보존한다. `WithdrawalRollbackTest`는 자식 삭제 뒤 사용자 삭제에 정확히 식별되는 예외를 주입하고, 트랜잭션 안의 부재와 롤백 후 일정·피드·연결 비밀번호·수강 소속·사용자 복원을 검증한다.
8. **수집의 네트워크와 저장도 분리한다.** `EclassSyncService`가 HTTP를 끝낸 뒤 `EclassAccountService.synchronizedSnapshot`이 짧은 쓰기 트랜잭션을 연다. `EclassImportService.apply`는 `MANDATORY`로 이 경계에 참여한다. 자동 등록·수집은 학교→사용자 순으로 잠가 역순 락을 피한다. `Member`·`Event`의 `@DynamicUpdate`는 프로필/원문 메타데이터 변경이 별도 원자적 토큰 버전이나 완료 표시를 덮어쓰지 않게 한다.

## 스터디 근거
- [Day 12](https://velog.io/@snowmile1224/Spring-Study-Day-12-Dirty-Checking-Snapshot%EA%B3%BC-flush-%EC%8B%9C%EC%A0%90): snapshot 비교와 flush 시점. save() 없이 UPDATE가 나가는 이유.
- [Day 15](https://velog.io/@snowmile1224/Spring-Study-Day-15-Transaction-Boundary-flushcommitrollback): flush/commit/rollback과 트랜잭션 경계.
- [Day 16](https://velog.io/@snowmile1224/Spring-Study-Day-16-Spring-AOP-Proxy-Transactional%EA%B3%BC-self-invocation-qa5mm4uz): `@Transactional`은 프록시로 동작한다. self-invocation에서는 적용되지 않는다.
- [Day 17](https://velog.io/@snowmile1224/Spring-Study-Day-17-Transaction-Propagation-REQUIRED%EC%99%80-REQUIRESNEW-5467dva8): REQUIRED 전파와 rollback-only. 내부에서 잡은 예외가 바깥 커밋을 깨뜨린다.
- [Day 18](https://velog.io/@snowmile1224/Spring-Study-Day-18-Association-Mapping%EA%B3%BC-Lazy-Loading-Hibernate-Proxy%EC%9D%98-%EC%B4%88%EA%B8%B0%ED%99%94-%EC%8B%9C%EC%A0%90): LAZY 프록시와 초기화 시점. `getReferenceById`가 프록시만 만드는 이유.
- [Day 20](https://velog.io/@snowmile1224/Spring-Study-Day-20-Day-21-3%EC%A3%BC%EC%B0%A8-%EB%A7%88%EB%AC%B4%EB%A6%AC-%EC%8B%9C%ED%97%98): OSIV를 끈 상태에서는 readOnly 트랜잭션 안에서 DTO로 바꿔야 한다.

## 결과와 트레이드오프
- 컨트롤러·직렬화 단계에서 `LazyInitializationException`이 생길 경로가 없다.
- 응답에 필요한 연관은 서비스에서 미리 읽어야 한다(예: `MemberService.me`의 `findWithSchoolById` fetch join).
- 같은 학교 학번은 학교 단위 짧은 락 안에서 조회·등록한다. DB UNIQUE는 최종 방어선이다. 네트워크 지연 중에는 DB 락을 잡지 않는 대신 외부 인증과 앱 저장을 하나의 분산 트랜잭션으로 보장하지는 않는다.
