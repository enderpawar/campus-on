# ADR-0002 Spring Data 인터페이스를 포트로 사용

## 상태
채택

## 맥락
스터디에서는 `XxxRepository` 인터페이스(포트)를 만들고 InMemory·JDBC·JPA 구현(어댑터)을 바꿔 끼우며 비교했다. 그 구조의 목적은 "구현을 교체해도 서비스가 바뀌지 않는다"를 직접 확인하는 것이었다. 이 프로젝트에는 저장소 구현을 교체할 계획이 없다. DB는 PostgreSQL(로컬·테스트는 H2 `MODE=PostgreSQL`)로 정해져 있다.

## 결정
- `JpaRepository`를 상속한 Spring Data 인터페이스를 서비스가 직접 의존하는 포트로 쓴다. 래퍼 어댑터(`JpaXxxRepository`)는 만들지 않는다.
- 쿼리는 인터페이스에 선언한다. 예:
  - `MemberRepository.findAuthSnapshot`: DTO 프로젝션(`AuthSnapshot`) 한 번으로 인증에 필요한 값만 읽는다.
  - `MemberRepository.findWithSchoolById`: `join fetch m.school`로 `/api/me` 응답에 필요한 학교를 함께 읽는다.
  - `EventRepository.findInRange`, `EventRepository.findFrom`, `CalendarFeedRepository.findMemberIdByToken`, `NoticeRepository.findAllBySchoolIdAndCategory`.

## 스터디 근거
- [Day 4](https://velog.io/@snowmile1224/Spring-Study-Day-4-ServiceRepository-Separation-of-Concerns-Identifier-%EB%8F%84%EC%9E%85%EA%B3%BC-Wrapper-%EA%B0%92-%EB%B9%84%EA%B5%90): 서비스와 저장소의 관심사 분리.
- [Day 5](https://velog.io/@snowmile1224/Spring-Study-Day-5-IoC%EC%99%80-DI-Constructor-Injection%EA%B3%BC-Singleton-Bean): 인터페이스 의존과 생성자 주입.
- [Day 10](https://velog.io/@snowmile1224/Spring-Study-Day-10-ORM-Entity-%EB%A7%A4%ED%95%91%EA%B3%BC-Spring-Data-JPA-%EA%B8%B0%EB%B3%B8-CRUD-l9uyj7th): Spring Data JPA로 어댑터를 교체해 본 경험.
- [Day 19](https://velog.io/@snowmile1224/Spring-Study-Day-19-%EC%97%B0%EA%B4%80-Entity-%EC%A1%B0%ED%9A%8C-%EC%A0%84%EB%9E%B5-N1-Problem%EA%B3%BC-Fetch-Join): fetch join(`findWithSchoolById`).

## 결과와 트레이드오프
- 같은 메서드를 두 번 선언하는 중복이 없다.
- 서비스 단위 테스트에서 저장소를 가짜 구현으로 바꾸기는 어렵다. 그래서 저장소를 거치는 동작은 H2 통합 테스트로 검증한다([ADR-0007](0007-test-strategy.md)).
- 저장소 교체가 실제로 필요해지면(예: 공지 검색을 별도 엔진으로) 그 기능에만 포트 인터페이스를 다시 도입한다.
