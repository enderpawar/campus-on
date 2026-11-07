# ADR-0003 스키마 소유권: Flyway + validate, ERD 관련 구조 이관

## 상태
채택

## 맥락
명세 §6 초안의 V1 위에 사용자 제공 `STack DB.sql`의 관련 구조를 반영한다. 원본은 MySQL LMS 전체 모델이고 현재 앱은 PostgreSQL F1·F3·F6와 e-class 최소 수집이므로, 기존 기능 유지 + 관련 ERD 확장 합의에 따라 옮긴다. 전체 DDL을 그대로 실행하거나 미사용 LMS 기능을 생성하지 않는다.

## 결정
1. **스키마는 Flyway가 소유한다.** `application.yml`에 `spring.jpa.hibernate.ddl-auto: validate`를 두어 Hibernate는 엔티티와 테이블이 맞는지만 확인한다. 맞지 않으면 기동이 실패한다. 매핑이 어긋나면 SQL이 아니라 엔티티를 고친다.
2. **마이그레이션 파일**
   - `src/main/resources/db/migration/V1__provisional_schema.sql`: 임시 스키마. 첫 줄에 확정 ERD로 교체할 예정이라고 적었다.
   - `src/main/resources/db/migration/V2__seed_seoultech_school.sql`: 서울과학기술대학교 1건.
   - `V3__eclass_identity.sql`: `member`→`users`, 학교 학번 식별·연결 비밀. 기존 이메일 계정은 자동 매칭하지 않고 정지·토큰 버전 증가.
   - `V4__catalog_schedule_posts.sql`: 과목·수강 소속, 일정 메타데이터, `notice`→`posts`와 게시판·본문·대상 학과.
   - `V5__eclass_connection_generation.sql`: `eclass_connections.connection_generation VARCHAR(36) NOT NULL`(기존 행은 `'legacy-' || user_id`). 로그인마다 새 UUID로 바뀌어 재로그인 전의 옛 동기화 결과를 걸러 낸다(Codex 검증 F3).
   - `src/main/resources/db/seed-local/V1000__local_sample_notices.sql`: local 프로필에서만 읽는 예시 공지 4건.
3. **기존 V1·V2는 변경하지 않는다.** 새 이관은 V3·V4·V5로 적용한다. 기존 사용자·일정·피드·공지 id 보존은 `SchemaUpgradeTest`로 확인한다. 이후 적용된 버전은 수정하지 않고 새 버전을 추가한다. 차이와 배포 전제는 [ERD 인계 문서](../erd-handoff.md)에 기록한다.
4. **H2·PostgreSQL 공통 SQL을 쓴다.** MySQL `UNSIGNED`, backtick, `TINYINT(1)`을 그대로 쓰지 않는다. 본문은 두 DB가 지원하는 `TEXT`, enum은 `VARCHAR`+CHECK이다. 주석은 `-- ` 형식이다. 운영 PostgreSQL 확인은 H2 통과와 구분한다.
5. **enum 매핑:** `Event.type`·`Event.source`·`Notice.category` 같은 enum 필드에 `@Enumerated(EnumType.STRING)`과 함께 `@JdbcTypeCode(SqlTypes.VARCHAR)`를 붙인다. 이것이 없으면 H2에서 Hibernate가 네이티브 enum 컬럼을 기대해 `VARCHAR` 컬럼과 validate가 맞지 않는다.
6. **이중 방어:** 규칙은 도메인 코드와 DB 제약 양쪽에 둔다.

|규칙|도메인(먼저 막음, 의미 있는 오류)|DB(최종 방어선)|
|---|---|---|
|시작 < 마감|`Event`의 시각 검증 → `INVALID_EVENT_TIME`|`ck_event_time_order`|
|학교 식별 중복|학교 행을 잠근 짧은 등록 트랜잭션|`users(school_id, external_ref)` UNIQUE|
|enum 값|Java enum|`ck_event_type`, `ck_event_source`, `ck_notice_category`|
|제공된 이름·제목이 비어 있지 않음|수집 프로필 부재는 null, 개인 일정 제목은 strip 후 검증|`ck_member_name_not_blank`, `ck_event_title_not_blank`|
|학년 1~6|`@Min(1) @Max(6)`|`ck_member_grade`, `ck_notice_target_grade`|

## 스터디 근거
- [Day 8](https://velog.io/@snowmile1224/Spring-Study-Day-8-Schema-Migration-Migration-%ED%8C%8C%EC%9D%BC%EA%B3%BC-Checksum-%EA%B2%80%EC%A6%9D): 마이그레이션 파일과 checksum 검증. "적용된 파일은 고치지 않는다"의 근거.
- [Day 14](https://velog.io/@snowmile1224/Spring-Study-Day-14-2%EC%A3%BC%EC%B0%A8-%EB%B2%84%ED%8D%BC-ddl-auto-validate%EC%99%80-CHECK-Constraint-2qkpzdx0): `ddl-auto: validate`와 CHECK 제약.
- [Day 32](https://velog.io/@snowmile1224/Spring-Study-Day-32-CI-%ED%8C%8C%EC%9D%B4%ED%94%84%EB%9D%BC%EC%9D%B8%EC%9D%98-%EC%8B%A4%EC%A0%9C-%EC%8B%A4%ED%96%89-%EA%B2%80%EC%A6%9D-GitHub-Actions-Job-%EC%9D%98%EC%A1%B4%EA%B3%BC-MySQL-1064-%EC%98%A4%EB%A5%98-81x13h5v): H2 호환 모드에서 통과한 SQL이 실제 MySQL에서 1064로 실패했다. 호환 모드는 실제 DB와 같지 않다. 그래서 `PostgresSchemaTest`(Testcontainers)로 실제 PostgreSQL에서 같은 마이그레이션과 validate를 확인한다([ADR-0007](0007-test-strategy.md)).
- [Day 33](https://velog.io/@snowmile1224/Spring-Study-Day-33-%EC%98%88%EC%95%BD-%EC%8B%9C%EA%B0%84%EB%8C%80-%EC%A4%91%EB%B3%B5-%EB%B0%A9%EC%A7%80-%EA%B5%AC%EA%B0%84-%EA%B2%B9%EC%B9%A8-%ED%8C%90%EC%A0%95%EA%B3%BC-%ED%95%98%EC%9C%84-%ED%98%B8%ED%99%98-%ED%99%95%EC%9E%A5): 동시 요청 경합은 애플리케이션 검사만으로 막을 수 없다. 스터디에서는 "고치지 않을 것"으로 남겼고, 여기서는 DB UNIQUE를 최종 방어선으로 두었다.

## 결과와 트레이드오프
- H2에서 V1~V5 신규 적용·validate, V2 데이터의 V4 이관, local 예시 시드 및 실제 앱 HTTP 스모크(V5 추가 전)를 확인했다. 최신 집계는 [ADR-0007](0007-test-strategy.md)에 기록한다.
- PostgreSQL에서의 확인(`PostgresSchemaTest.flywayMigrationsMatchEntityMappingsOnRealPostgres`, `blankMemberNameIsRejectedByCheckConstraint`)은 로컬에 Docker가 없어 skipped다. **미검증**이며 CI 첫 실행에서 확인해야 한다.
- 규칙을 두 곳에 두므로 바꿀 때 두 곳을 함께 고쳐야 한다.
