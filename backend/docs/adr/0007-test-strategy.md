# ADR-0007 테스트 전략: Unit(Clock) / Slice / Integration / Testcontainers

## 상태
채택

## 맥락
스터디에서 남은 테스트 약점은 셋이었다.
- `POST` 요청의 slice 테스트가 없었고, "`@WebMvcTest`가 무엇을 띄우지 않는가"에 바로 답하지 못했다(Day 26, 회고).
- JWT 만료 테스트가 `Thread.sleep`에 의존했다(Day 34, 회고).
- H2 호환 모드에서 통과한 SQL이 실제 DB에서 1064로 실패했다(Day 32). 회고의 다음 목표가 Testcontainers였다.

## 결정
테스트를 네 층으로 나누고, 층마다 확인하는 대상을 정한다.

|클래스|층|수|확인하는 것|
|---|---|---|---|
|`global/security/JwtProviderTest`|Unit (`MutableClock`)|4|만료·typ 혼용·서명 변조·짧은 키|
|`schedule/EventTest`|Unit|2|수집 일정 수정/삭제 제한, 시각 순서|
|`schedule/ics/IcsWriterTest`|Unit|4|이스케이프·folding·DTEND 생략·종일 날짜|
|`schedule/EventControllerWebMvcTest`|Slice|1|입력 오류 시 서비스 미호출|
|`global/error/GlobalExceptionHandlerWebMvcTest`|Slice|4|409/500/도메인 예외/404, 원인 경로 검증|
|`global/logging/SensitivePathsTest`|Unit|3|인코딩·대소문자·깨진 경로의 ICS 마스킹|
|`global/logging/RequestIdHttpTest`|Integration|3|안전한 request id 재사용·로그 삽입 차단|
|`auth/AuthFlowHttpTest`|Integration + 테스트 학교 경계|13|가입 없는 학교 로그인·동일 학번 재사용·실패 시 미등록·비밀번호 opt-in 암호화와 remember=false 재로그인 시 삭제·연결 해제·로그아웃 시 학교 연결 삭제·반복 실패 차단·동시 실패 요청의 예산 초과 차단·미커밋 로그아웃과 경합한 로그인의 최신 버전 발급·JWT/오류 계약|
|`auth/LoginAttemptThrottleTest`|Flyway H2 + JDBC (Clock)|5|학번 한도와 정확한 `Retry-After`·클라이언트 한도·성공 시 그 학번만 초기화·진행 중 시도의 예산 예약|
|`auth/LogoutConcurrencyTest`|Integration, 다중 스레드|1|동시 로그아웃 8건의 원자적 버전 증가|
|`auth/WithdrawalHttpTest`|Integration|4|재인증 실패 시 보존·전용 데이터 삭제·다른 회원/공유 데이터 보존·탈퇴 후 새 id 재진입과 옛 JWT 거부|
|`auth/WithdrawalRollbackTest`|Integration, 정확한 실패 주입|1|자식 삭제의 트랜잭션 내 부재와 일정·피드·연결 비밀·소속·사용자 롤백 복원|
|`member/MemberProfileHttpTest`|Integration|6|학과·학년 수정, 생략/null, 검증·소유권|
|`eclass/HttpEclassGatewayTest`|실제 Java HTTP + 로컬 서버|14|숨은 필드/쿠키/POST-200 검증·동일 origin·만료·실패·응답 상한·실제 수집 경로|
|`eclass/EclassHtmlParserTest`|원문 HTML 파싱|19|KST·D-day·원문 id·링크 공유 과제 보존·날짜 미정·종일·길이·명시적 시험 날짜·한글 KJ 키·KJ 40자 상한·연도 넘김·`24:00`·셀 경계 시험 행·todo/목록 중복 제거와 다른 마감·모호한 후보 보존|
|`eclass/EclassSyncHttpTest`|Integration + 프로그래밍 가능한 가짜 학교|10|수동 동기화 반영·저장 비밀번호 1회 재인증·비밀번호 없을 때 재로그인 요구·거절된 저장 비밀번호 폐기와 재시도 중단(warn 로그에 코드만, 비밀값 없음)·실패 백오프·복호화 불가 비밀값·재로그인 뒤 끝난 옛 동기화 폐기·교체된 연결의 비밀번호로 재인증하지 않음·로그아웃 없는 재로그인 중 옛 실패 폐기(info)·옛 세대 실패 보고 무시|
|`eclass/ManualSyncCooldownHttpTest`|Integration (쿨다운 활성)|1|로그인 직후 수동 동기화 429 + `Retry-After`|
|`eclass/EclassSecretsTest`|실제 암호 연산|1|nonce 분리·복호화·변조/다른 키 거부|
|`schedule/EclassImportHttpTest`|Integration|7|수집 id/완료/개인/미수집 항목 보존·과목 소속 격리·미정 날짜·종일 ICS·메타데이터·이름 없는 과목·길이 절단|
|`schedule/EventHttpTest`|Integration, 고정 Clock|9|KST D-day·기간·소유권·완료 영속성|
|`schedule/EventLifecycleHttpTest`|Integration|9|PUT/DELETE·남의 404·수집 일정 제한·제목 80자·검증|
|`schedule/IcsHttpTest`|Integration|8|공개 자격·회전·마스킹·수정/삭제 반영·회원 격리·현재 URL·비활성 회원 피드 404|
|`notice/NoticeHttpTest`|Integration|13|학교/분류/페이지/정렬·본문 문단/목록·복수 학과 태그|
|`SchemaUpgradeTest`|실제 Flyway + JDBC, H2|2|V2→최신 데이터 보존과 옛 이메일 계정 격리·V4 연결 행의 V5 세대 backfill과 NOT NULL·V6 조정 데이터 쓰기|
|`PostgresSchemaTest`|PostgreSQL 17 Testcontainers|2|실제 PostgreSQL Flyway/validate·CHECK, Docker 없으면 skipped|
|`auth/RemainingAuthHttpTest`|Integration + MutableClock|6|학교 장애 탈퇴·최근/오래된/없는 auth_time·refresh 보존·틀린 비밀번호·정규화 계정/버킷·400|
|`global/security/ClientAddressResolverTest`|Unit|5|기본 불신·오른쪽 체인·잘못된 주소·IPv6 CIDR|
|`eclass/DistributedSyncTest`|Integration + 독립 서비스 객체|4|서버 간 single-flight·만료 소유권·재인증 중 로그아웃/재로그인 대기·새 연결 보존|
|`PostgresSchemaUpgradeTest`|PostgreSQL 17 Testcontainers|2|H2와 공유하는 V2/V4 데이터 업그레이드·V5 backfill·NOT NULL·V6 조정 데이터, Docker 없으면 skipped|

로컬 결과(2026-11-08, Git Bash에서 `backend/` 작업 디렉터리로 `GRADLE_USER_HOME=C:/Users/jinwoo/.gradle sh gradlew cleanTest test --console=plain`): **163건 중 159건 통과, 실패 0, 오류 0, 4건 skipped**. XML 합계 tests=163/failures=0/errors=0/skipped=4. skipped는 `PostgresSchemaTest` 2건과 `PostgresSchemaUpgradeTest` 2건(Docker 없음)이다. PostgreSQL 테스트는 작성·컴파일됐으나 실제 실행은 미검증이다. 이전 2차 검증 후는 142건 중 140건 통과였다. 테스트 JVM의 `-Xshare:off`는 유지한다. `FakeEclass`는 학번별 장애·비밀번호·스냅샷·세션 만료·hold를 지원한다. 시간은 MutableClock으로 이동하며 sleep은 쓰지 않는다. 기본 test 프로필 쿨다운은 PT0S이며 `ManualSyncCooldownHttpTest`만 활성화한다.

별도 실제 JAR + loopback 학교 HTTP 서버 + H2 스모크에서 47개 HTTP 확인을 통과했다. 이 스모크는 e-class/ERD 전환 시점 실행이며, Claude 리뷰·Codex 검증 수정 후에는 다시 돌리지 않았다. 학교 폼 인증부터 과목·일정·ICS·공지·세션 재인증·소유권·연결 해제·로그아웃·탈퇴·새 id 재진입까지 연결했다. 과목 링크를 공유하는 두 과제 중 하나가 사라지는 문제를 이 실행에서 재현·수정하고 파서 회귀를 유지했다. 실제 학교 계정/외부 DB는 사용하지 않았다.

**slice에서 principal 주입:** `GlobalExceptionHandlerWebMvcTest`는 필터를 끄므로 `SecurityContextHolder`에 `AuthMember`를 직접 넣는다. 처음에는 넣지 않아 컨트롤러의 `me.memberId()` NPE가 500을 냈고, "예상 못 한 예외 → 500" 테스트가 엉뚱한 이유로 통과했다. 그래서 그 테스트는 `verify(eventService).create(...)`로 500이 서비스 예외에서 나왔는지까지 확인한다.

**회귀 증명:** `LogoutConcurrencyTest`는 통과만으로는 의미가 약하다. 그래서 옛 read-modify-write 로그아웃을 임시로 되살리고 50ms sleep으로 경합 구간을 넓혀 실행했고, 테스트가 실패(증가 유실)하는 것을 확인했다. 원자적 UPDATE로 되돌리면 통과한다. 임시 코드는 제거했다.

### 1. 시간은 `Clock`으로 주입한다
`ClockConfig`가 `Clock.systemUTC()`를 제공한다. JWT·일정·ICS·수집 파서는 Clock을 주입받아 시간 경계를 검증한다. JWT 만료는 MutableClock, D-day·HTML 날짜 추론은 고정 Clock을 사용한다.

### 2. `@WebMvcTest`가 띄우는 것과 띄우지 않는 것
`EventControllerWebMvcTest`(`@WebMvcTest(EventController.class)`, `@AutoConfigureMockMvc(addFilters = false)`)

|띄운다|띄우지 않는다|
|---|---|
|`EventController`, `@RestControllerAdvice`(`GlobalExceptionHandler`), Jackson, Bean Validation, MockMvc|`@Service`, 리포지토리, DataSource·JPA, 일반 `@Configuration`(`SecurityConfig`, `LoggingConfig`)|

그래서 `EventService`를 `@MockitoBean`으로 대체하고, 보안 필터는 `addFilters = false`로 끈다. `RequestIdFilter`에 `@Component`를 붙이지 않은 것도 slice가 이 필터를 끌어오지 않게 하려는 것이다([ADR-0008](0008-observability-request-id.md)). 테스트는 `{"title":"","dueAt":null}`을 보내 400 `VALIDATION_FAILED`, 오류 2건(`dueAt`, `title`), `verifyNoInteractions(eventService)`를 확인한다.

### 3. 통합 테스트는 H2 + 상대 단언
- HTTP 테스트에 `@Transactional`을 붙이지 않는다. MockMvc 요청은 실제 커밋을 거쳐야 dirty checking·제약 동작을 검증할 수 있다.
- 데이터는 `student-<UUID>`로 학교 인증 경계를 대체해 고유하게 만든다(`ApiTestClient`, `EclassTestConfiguration`). 이 경계 대체가 실제 학교 프로토콜을 증명하지는 않는다. 프로토콜은 `HttpEclassGatewayTest`의 실제 HTTP 클라이언트·로컬 서버로 별도 검증한다.
- **Phase 3에서 이 원칙을 어긴 버그를 찾았다.** `ApiTestClient`가 `GET /api/schools`의 첫 항목을 서울과기대로 가정했는데, `NoticeHttpTest`가 넣은 "다른학교-..."가 이름순으로 앞에 와서 3건이 실패했다. 이름으로 찾는 `ApiTestClient.seoultechId`로 고쳤다. 테스트끼리 DB를 공유하는 한 순서·개수 가정은 깨진다.
- `build.gradle.kts`의 Test 태스크는 `SPRING_*` 환경변수를 모두 지우고 `SPRING_PROFILES_ACTIVE=test`만 넣는다. 개발 PC의 외부 DB 설정이 테스트에 섞이지 않게 하려는 것이다([ADR-0008](0008-observability-request-id.md)).

### 4. 실제 DB는 Testcontainers
`PostgresSchemaTest`는 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Testcontainers(disabledWithoutDocker = true)`이고 `postgres:17-alpine`에 `@ServiceConnection`으로 붙는다.
- `flywayMigrationsMatchEntityMappingsOnRealPostgres`: 컨텍스트 기동(= Flyway 적용 + validate) 후 학교 시드 1건 확인.
- `blankMemberNameIsRejectedByCheckConstraint`: 빈 이름 INSERT가 CHECK에 막히는지 확인.
- Docker가 없으면 건너뛴다. CI(`.github/workflows/backend-ci.yml`의 `test` job, `ubuntu-latest`)에서는 실행된다.

## 스터디 근거
- [Day 26](https://velog.io/@snowmile1224/Spring-Study-Day-26-%ED%85%8C%EC%8A%A4%ED%8A%B8-%EB%B2%94%EC%9C%84%EC%99%80-Mock-%EC%A0%84%EB%9E%B5-Slice-Test%EC%9D%98-Security-%EC%9E%90%EB%8F%99-%EC%84%A4%EC%A0%95-%EC%B6%A9%EB%8F%8C-jnvlnq75): 테스트 범위와 Mock 전략, slice 테스트의 Security 자동 설정 충돌.
- [Day 32](https://velog.io/@snowmile1224/Spring-Study-Day-32-CI-%ED%8C%8C%EC%9D%B4%ED%94%84%EB%9D%BC%EC%9D%B8%EC%9D%98-%EC%8B%A4%EC%A0%9C-%EC%8B%A4%ED%96%89-%EA%B2%80%EC%A6%9D-GitHub-Actions-Job-%EC%9D%98%EC%A1%B4%EA%B3%BC-MySQL-1064-%EC%98%A4%EB%A5%98-81x13h5v): H2 호환 ≠ 실제 DB.
- [Day 34](https://velog.io/@snowmile1224/Spring-Study-Day-34-Day-35-5%EC%A3%BC%EC%B0%A8-%EB%A7%88%EB%AC%B4%EB%A6%AC-%EC%8B%9C%ED%97%98): slice 범위 오답, `Thread.sleep` 의존 만료 테스트.
- [회고](https://velog.io/@snowmile1224/Spring-Study%EB%A5%BC-%EB%A7%88%EC%B9%98%EB%A9%B0-5%EC%A3%BC-%EB%B0%B1%EC%97%94%EB%93%9C-%EA%B8%B0%EB%B3%B8%EA%B8%B0-%ED%8A%B8%EB%9E%99-%ED%9A%8C%EA%B3%A0%EC%99%80-%EB%8B%A4%EC%9D%8C-%EB%AA%A9%ED%91%9C-mx5am4xr): 다음 목표로 적은 POST slice 테스트와 Testcontainers.

## 결과와 트레이드오프
- 전체 테스트의 대부분이 `@SpringBootTest` 통합 테스트라 실행이 단위 테스트보다 느리다. 대신 보안·검증·트랜잭션이 함께 동작하는지를 실제 경로로 본다.
- **미검증:** `PostgresSchemaTest` 2건과 CI의 docker job은 로컬에서 한 번도 실행되지 않았다. CI 첫 실행에서 확인해야 한다.
- slice 테스트는 `EventControllerWebMvcTest`와 `GlobalExceptionHandlerWebMvcTest` 두 개다.

## 2026-11-08 회귀 검증

원본 전체 파일을 되돌린 실행과 구별하여, 새 동작을 제거한 회귀 변형을 실행했다. 최근 인증 탈퇴를 막기, refresh의 auth_time을 발급 시각으로 갱신하기, 원문 id 판정을 제거하기, DB alias 병합을 생략하기, 신뢰 프록시를 모두 무시하기에서 새 테스트 7건이 실패했다. 연결 변경의 claim 검사를 제거한 별도 실행은 로그아웃·재로그인 대기 테스트 2건 모두 실패했다. 모두 원복한 뒤 최종 전체 스위트를 통과했다. M10·DB throttle/sync 공유·학번 정규화에 대해 원본 전체 코드의 수정 전 실패 실행은 하지 않았다. 상세 명령과 오류는 `build/codex-remaining-fixes.md`에 기록한다.
