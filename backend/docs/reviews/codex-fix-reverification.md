# F1~F5 수정 2차 독립 검증

검증일: 2026-11-07(KST). 대상: 현재 `backend/` 코드·테스트·문서. 판정: **REQUEST_CHANGES**. 기존 F1~F5의 직접 수정은 확인했으나, 사용자 락을 추가한 로그인 경로에 오래된 token_version으로 토큰을 발급하는 경합이 남아 있다(N1). 이 결함이 이번 수정에서 처음 도입됐다고 단정하지는 않는다.

아래 경로는 `backend/` 기준이다. `main/`은 `src/main/java/com/campuson/api/`, `test/`는 `src/test/java/com/campuson/api/`를 뜻한다. 저장소 파일은 이 보고서 하나만 작성했다. 지정 테스트의 Gradle 산출물 갱신은 별도이며, git 명령·실제 학교 계정·외부 DB는 사용하지 않았다. 추가 실험도 독립적인 프로세스 내부 H2 메모리 DB만 사용했고 소스·테스트 파일은 만들지 않았다.

## 새로 실행한 테스트와 XML 집계

- Git Bash(`C:/Program Files/Git/bin/bash.exe`), 작업 디렉터리 `backend/`에서 정확히 다음 명령을 실행했다.

  ```sh
  GRADLE_USER_HOME=C:/Users/jinwoo/.gradle sh gradlew cleanTest test --console=plain
  ```

- 종료 코드 0, `BUILD SUCCESSFUL in 23s`. `:cleanTest`, `:test`가 실행됐고 `:test`는 UP-TO-DATE가 아니었다. 총 6개 actionable task 중 2개 실행, 4개 UP-TO-DATE(컴파일·리소스 관련)였다.
- 실행 명령이나 셸에서 `SPRING_*` 환경변수를 추가·삭제·변경하지 않았다. 기존 `build.gradle.kts:43~47`의 Test 태스크가 테스트 JVM 환경을 격리하는 설정은 그대로 사용했다.
- 새 `build/test-results/test/TEST-*.xml` **25개**의 tests/failures/errors/skipped 속성을 합산했다: **tests=138, failures=0, errors=0, skipped=2, 통과=136**. XML timestamp 범위는 `2026-11-07T13:55:57.114Z`~`2026-11-07T13:56:10.458Z`다. 지난 실행 XML을 재사용한 집계가 아니다.

| 확인 대상 XML 클래스 | tests | 통과 | failures/errors | skipped |
|---|---:|---:|---:|---:|
| AuthFlowHttpTest | 12 | 12 | 0/0 | 0 |
| LoginAttemptThrottleTest | 4 | 4 | 0/0 | 0 |
| LogoutConcurrencyTest | 1 | 1 | 0/0 | 0 |
| WithdrawalHttpTest / WithdrawalRollbackTest | 5 | 5 | 0/0 | 0 |
| EclassHtmlParserTest | 17 | 17 | 0/0 | 0 |
| EclassSyncHttpTest | 8 | 8 | 0/0 | 0 |
| HttpEclassGatewayTest | 14 | 14 | 0/0 | 0 |
| ManualSyncCooldownHttpTest | 1 | 1 | 0/0 | 0 |
| EclassImportHttpTest | 6 | 6 | 0/0 | 0 |
| IcsHttpTest / IcsWriterTest | 12 | 12 | 0/0 | 0 |
| SchemaUpgradeTest | 1 | 1 | 0/0 | 0 |
| PostgresSchemaTest | 2 | 0 | 0/0 | 2 |
| 나머지 11개 클래스 | 55 | 55 | 0/0 | 0 |
| **합계** | **138** | **136** | **0/0** | **2** |

PostgreSQL skip 대상은 `blankMemberNameIsRejectedByCheckConstraint()`와 `flywayMigrationsMatchEntityMappingsOnRealPostgres()`다(`TEST-com.campuson.api.PostgresSchemaTest.xml:2~9`). XML에는 `<skipped/>`만 있고 구체적인 Docker 진단은 없다. `test/PostgresSchemaTest.java:24`의 `disabledWithoutDocker=true`와 부합하지만 이 결과를 PostgreSQL 통과로 해석하지 않는다. 아래 추가 실험은 JUnit 138건에 포함하지 않았다.

## F1~F5별 판정

### F1 — 해결

- `main/auth/LoginAttemptThrottle.java:42~51`: 한 모니터 안에서 학번·IP의 완료 실패와 inFlight를 검사하고, 둘 다 허용된 뒤에만 두 예약 수를 증가시킨다. 학번 검사 성공 후 IP 검사에서 거절돼도 아직 증가 전이므로 예약 누수가 없다.
- 같은 파일 `:54~70`, `:95~102`, `:122~125`: 실패·성공·단순 반환 모두 inFlight를 감소시키며 중복 완료를 무시한다. 진행 중인 Budget은 idle 정리 대상이 아니어서 창 만료·prune 중에도 예약이 유실되지 않는다. 성공은 학번의 완료 실패만 지우고 다른 진행 중 시도는 남긴다.
- `main/auth/AuthService.java:33~40`, `main/auth/WithdrawalService.java:25~33`: 학교 인증이 try-with-resources 안에 있다. INVALID_CREDENTIALS는 failed, 정상 반환은 succeeded, 그 밖의 BusinessException/RuntimeException 및 예외 탈출은 close로 예약을 반환한다. DB 저장·JWT 발급은 예약 해제 뒤다. 실제 호출자는 Attempt를 다른 스레드에 공유하지 않는다.
- `test/auth/LoginAttemptThrottleTest.java:31~40`는 스레드를 만들지는 않지만 여러 예약이 동시에 살아 있는 상태와 close 후 슬롯 재사용을 확인한다. 실제 병렬 검증은 `test/auth/AuthFlowHttpTest.java:51~75`이며, 학교에 5건이 들어간 latch를 기다리고 응답을 풀기 **전에** 3개의 429를 회수한다. 응답을 푼 뒤에는 5개의 401 및 authCalls=5를 확인한다. 완료 실패가 뒤늦게 쌓여 우연히 통과하는 구조가 아니다.
- 학번 UUID·서로 다른 IP로 기존 카운터 간섭을 피한다. 10초 관측/30초 학교 hold 제한은 극단적으로 느린 환경에서 실패할 여지는 있지만, sleep 타이밍으로 정답을 가정하는 테스트는 아니다. 예외별 실제 서비스 예약 재사용을 모두 별도 테스트한 것은 아니며, 그 부분은 코드 경로로 확인했다.

### F2 — 해결(명시된 보수적 휴리스틱 범위)

- `main/eclass/HttpEclassGateway.java:176~211`: 과목·종류·정규화 제목별 후보를 묶고 todo 1개/목록 1개일 때만 비교한다. 양쪽 마감이 있으면 KST LocalDate가 같은 경우만 합친다. 한쪽 미상은 허용한다. 다른 날짜 또는 다중 후보는 원본 객체를 전부 보존한다. 날짜 보충은 유일한 todo의 마감이 없을 때만 한다.
- `test/eclass/EclassHtmlParserTest.java:172~201`의 기존 정상 병합 및 `sameTitleWithDifferentDeadlineIsNotMerged`, `ambiguousSameTitleCandidatesAreAllKept`가 새 실행에서 통과했다. Claude가 제시한 메서드 이름은 정확하며, 소속 클래스는 **EclassHtmlParserTest**다.
- 원문 ID 동일성을 증명하는 알고리즘으로 바뀐 것은 아니다. 동일 제목·같은 날의 별도 항목이나 한쪽 마감 미상은 여전히 구별을 보장하지 않는다. 이는 이번에 문서화한 일대일/날짜 휴리스틱의 한계다. 기존 F2의 다른 주 마감 및 모호한 다중 후보 누락은 막았다. 이전 DB 중복 정리는 구현하지 않았고 문서에도 미구현으로 명시했다.

### F3 — 해결(이전 세대의 DB 반영 차단), 관련 추가 결함 N1

- `src/main/resources/db/migration/V5__eclass_connection_generation.sql:3~5`: nullable 컬럼 추가 → 기존 user_id 기반 채움 → NOT NULL 순서다. `main/eclass/EclassConnection.java:20~29`에서 길이 36 및 로그인마다 UUID 교체가 일치한다. 삭제 후 재생성도 다른 세대가 된다.
- `main/eclass/EclassAccountService.java:58~68`는 쿠키·비밀번호와 함께 세대를 읽고 복호화 예외에도 세대를 전달한다. `:80~88`의 성공 반영은 연결 행 PESSIMISTIC_WRITE 아래에서 세대를 확인하고, 불일치 시 importer 이전에 false다. `:92~99`의 일반 실패·복호화 실패도 연결 행을 잠근 뒤 동일 세대에서만 쓴다. 실패 반영은 연결을 재생성하지 않는다.
- `main/eclass/EclassSyncService.java:88~94`는 저장 비밀번호 재인증 직전에 현재 세대를 확인하고 성공 결과의 폐기를 처리한다. 다만 isCurrent와 실제 gateway 호출은 원자적이지 않다. 확인 직후 교체가 일어나는 매우 좁은 경합까지 학교 요청 발송을 금지하는 보장은 없다. 이미 진행 중인 HTTP를 취소하는 구현도 아니다. 세대 확인 이후의 DB 반영은 다시 검사하므로 보호된다.
- `test/eclass/EclassSyncHttpTest.java:128~173`: 옛 fetch가 학교 경계에 진입한 것을 기다리고 로그아웃 및 재로그인을 완료한 뒤 풀어 준다. 첫 테스트는 새 연결 PENDING/lastSyncedAt=null 및 옛 일정 미생성을, 두 번째는 authCalls=2와 새 저장 비밀번호 보존을 확인한다. 학교 만료 판정도 hold 해제 후 수행된다(`test/support/EclassTestConfiguration.java:49~58`). 두 테스트 모두 이번 실행에서 통과했다.
- 두 테스트의 실제 시나리오는 **로그아웃→재로그인**이다. 로그아웃 없는 재로그인, 옛 작업의 일반 실패·복호화 실패가 새 연결을 만지는 경합은 독립 테스트하지 않았다. 해당 경로의 세대 검사 자체는 코드로 확인했다. 테스트가 모든 락 경합을 증명한다고 보지는 않는다.

V5 추가 확인: H2 2.3.232의 별도 `jdbc:h2:mem:review_v5;MODE=PostgreSQL`에서 기존 행 42와 BIGINT 최댓값 9223372036854775807을 넣고 V5의 실행 SQL 세 문장을 그대로 적용했다. `legacy-42`, `legacy-9223372036854775807`로 채워졌고 NOT NULL 적용까지 성공했다. VARCHAR(20)은 부호 포함 BIGINT 문자열을 담고, 결과는 VARCHAR(36)에 들어간다. ADD COLUMN/CAST/문자열 `||`/ALTER COLUMN SET NOT NULL은 PostgreSQL에서도 사용하는 문법으로 정적 문제는 찾지 못했다. **실제 PostgreSQL 실행은 하지 않았다.** 기존 SchemaUpgradeTest는 V5까지 적용하지만 V4의 연결 행을 미리 넣지는 않아 populated V5 backfill 검증을 대신하지 않는다.

### F4 — 해결

- `main/eclass/EclassSyncService.java:100~109`: BusinessException에 대해 memberId+ErrorCode만 남긴다. ECLASS_REAUTH_REQUIRED/UNAUTHORIZED는 info, 나머지는 warn이다. `main/eclass/HttpEclassGateway.java:307~318`의 HTTP 비정상 상태·IOException이 ECLASS_UNAVAILABLE로 변환되는 경로도 이 catch에 도달한다.
- 복호화 실패는 memberId만 warn(`:96~99`), 그 밖의 RuntimeException은 memberId+예외 클래스 이름만 warn(`:110~114`)이다. 예외 객체·메시지·응답 원문·자격 증명을 인자로 넘기지 않는다.
- 이번 변경의 로그 레벨/내용을 캡처하는 전용 회귀 테스트는 없다. 이 판정은 실제 분기와 로거 인자를 읽은 결과다. 아래 N2의 문서 표현은 별개다.

### F5 — 해결(기존 세 지적)

- `src/main/resources/application-test.yml:14~15`: 쿨다운 전용 클래스가 ManualSyncCooldownHttpTest임을 정확히 명시한다.
- `docs/erd-handoff.md:50`: 기존 과목이 있으면 연결하고, 기존 과목도 이름도 없을 때만 과목 없이 수집한다고 수정했다. `main/schedule/EclassImportService.java:71~80`과 일치한다.
- `docs/adr/0007-test-strategy.md:20~21,84`: slice 두 클래스와 각각 1/4개가 실제 테스트와 일치한다. `:24~41`의 클래스별 개수 및 `:43`의 138/136/0/2도 새 XML 집계와 일치한다. `docs/tech-debt-ledger.md:171`의 변경 클래스 12/4/8/17과 README:173, erd-handoff:73의 합계도 정확하다. 과거 132/130 및 110/108, 47건 스모크는 과거 실행으로 구분하므로 현재 수치와 모순이 아니다.
- README, 원장, ADR-0005/0007/0008, ERD 인계의 F1~F4 기술도 대조했다. 추가한 로그 설명 중 과장된 한 문장은 N2로 분리했다.

## 실제 락 획득 순서와 영향 범위

| 경로 | 실제 획득 순서와 근거 |
|---|---|
| 로그인 | 학교 WRITE → 사용자 일반 조회 → 기존 사용자 WRITE → 연결 WRITE/저장 (`main/eclass/EclassAccountService.java:35~49`). 신규 사용자는 INSERT로 사용자 행을 생성한다. 일반 조회 후 락의 상태 갱신 문제는 N1. |
| 동기화 성공 | 학교 WRITE → 사용자 WRITE → 연결 WRITE → import의 학교 재획득 → 과목/소속/일정 쓰기 (`EclassAccountService.java:81~87`, `main/schedule/EclassImportService.java:43~66`). import는 이미 같은 트랜잭션이 보유한 학교 락을 다시 요청한다. |
| 동기화 실패/복호화 실패 | 연결 WRITE만 (`EclassAccountService.java:92~99`). 이후 학교/사용자 락을 요청하지 않는다. |
| 로그아웃 | users의 원자 UPDATE가 사용자 쓰기 락 획득 → 연결 DELETE, 같은 트랜잭션 (`main/auth/AuthService.java:63~66`, `main/member/MemberRepository.java:17~19`, `EclassAccountService.java:120~121`). |
| 연결 해제 | 사용자 WRITE → 연결 DELETE (`EclassAccountService.java:114~116`). |
| 탈퇴 | 외부 학교 인증 완료 후 사용자 WRITE → 일정/피드/소속 DELETE → 연결 DELETE → 사용자 DELETE (`main/auth/WithdrawalStore.java:27~35`). 학교 행을 뒤늦게 잠그는 경로는 없다. |

주요 경로에서 학교↔사용자 또는 사용자↔연결의 순환 대기를 만드는 역전은 발견하지 못했다. import의 학교 락 재요청은 동일 트랜잭션의 기존 락이므로 역전이 아니다. 연결만 잠그는 실패 경로도 상위 락을 추가로 기다리지 않는다. 이는 코드 추적 결과이며 PostgreSQL에서 교착 부재를 부하 테스트로 증명한 것은 아니다. 향후 importer.apply를 사용자 락만 보유한 다른 운영 경로에서 호출하면 순서가 달라지므로 호출 경계를 유지해야 한다.

새 시그니처의 전체 호출부를 검색했고 운영 호출은 EclassSyncService에, 테스트 직접 호출은 WithdrawalHttpTest/WithdrawalRollbackTest 등에 반영돼 있다. 신규 테스트 실행으로 테스트 컴파일 및 탈퇴/롤백·ICS·수집 회귀 통과를 확인했다. `spring.jpa.open-in-view=false`(`src/main/resources/application.yml:7`)여서 context 조회의 엔티티가 HTTP 전체에 걸쳐 같은 영속성 컨텍스트에 남는 구성도 아니다.

## 새로 확인한 결함

### N1 — Medium: 사용자 락을 뒤늦게 잡아도 로그인에서 읽은 token_version은 갱신되지 않는다

- 위치: **`main/eclass/EclassAccountService.java:36~41,50`**. 관련: `main/member/MemberRepository.java:14~19`, `main/auth/AuthService.java:48,65`, `main/global/security/JwtAuthenticationFilter.java:39~41`.
- 근거: authenticated는 일반 조회로 Member를 영속성 컨텍스트에 올린 다음 lockById를 호출한다. 같은 ID를 PESSIMISTIC_WRITE로 다시 조회해도 기존 엔티티 필드를 refresh하지 않는다. 반환값을 member에 다시 대입하는 것만으로도 해결되지 않는다.
- 가능한 순서: 로그인 A가 학교 락을 잡고 Member(tokenVersion=0)를 읽음 → 로그아웃 B가 사용자 버전을 1로 올리고 연결을 지운 뒤 커밋(학교 락은 필요 없음) → A가 사용자 락을 획득해 새 연결을 저장 → A가 오래된 0으로 JWT를 발급. 로그인 응답은 성공하지만 새 access/refresh는 현재 DB 버전 1과 달라 즉시 거부된다. B가 UPDATE를 먼저 수행하고 미커밋인 동안 A의 일반 조회가 이전 버전을 읽고 락에서 대기하는 순서도 가능하다. @DynamicUpdate는 DB 버전 덮어쓰기를 막을 뿐 이 발급 버전을 갱신하지 않는다.
- 추가 실행 근거: 저장소의 컴파일된 실제 Member/School 엔티티와 캐시된 Hibernate **6.6.53.Final**, H2 **2.3.232**를 JShell 표준입력으로 실행했다. Spring 앱이나 외부 DB를 띄우지 않고 별도 메모리 DB에서 두 Session을 사용했다. A의 Member 조회 → B의 동일 tokenVersion 원자 UPDATE/커밋 → A의 동일 ID PESSIMISTIC_WRITE 조회를 순서대로 수행한 출력은 **`REVIEW: loaded=0 locked=0 db=1 sameEntity=true`**였다. 이 실험은 영속성 컨텍스트의 stale 상태를 직접 확인한 것이며 전체 HTTP 경합 테스트는 아니다.
- 수정안: 학교 락 이후 최초 사용자 조회 자체를 `(externalRef, schoolId)` 조건의 PESSIMISTIC_WRITE 조회로 바꿔, 일반 조회를 없앤다. 또는 락을 잡은 뒤 명시적 refresh를 하고 사용자 상태/발급 버전을 그 최신 상태로 판단한다. 사용자 행이 없어진 경우도 반드시 처리한다. 회귀 테스트는 사용자 읽기와 락 사이에 로그아웃 커밋을 강제해 로그인 응답의 새 access/refresh가 실제로 유효한지 확인해야 한다. 단순 동시 logout 8건 테스트는 이 경합을 검증하지 않는다.
- 신규 도입 여부: git을 사용하지 않았으므로 비교 판정하지 않았다. 현재 수정된 락 코드에도 남아 있는 **새로 발견한 결함**이다.

### N2 — Low: 모든 옛 결과 폐기가 info만 남는다는 문서가 구현과 다르다

- 위치: **`docs/erd-handoff.md:47`**, 관련 `docs/adr/0008-observability-request-id.md:23`, `docs/tech-debt-ledger.md:178`.
- 근거: ERD 인계는 성공·실패·복호화 실패를 설명한 바로 뒤에 세대가 달라지면 “옛 결과는 버리고 info 로그만 남긴다”고 일반화한다. 하지만 이전 세대의 INVALID_CREDENTIALS/ECLASS_UNAVAILABLE는 `main/eclass/EclassSyncService.java:100~108`에서 먼저 warn을 남기고 accounts.failed가 세대 불일치로 쓰기를 생략한다. 이전 세대의 복호화 실패도 `:96~98`에서 warn이다. 폐기를 알리는 info(`:93~94`)는 성공 스냅샷 반영에서 false를 반환한 경우에만 있다. DB 보호는 정상이나 로그 계약 설명이 부정확하다.
- 수정안: 문서를 “성공 스냅샷이 세대 불일치로 폐기되면 info. 실패·복호화 실패는 기존 레벨로 기록하되 새 연결에는 반영하지 않음”으로 좁힌다. 또는 실패 반영 API도 적용 여부를 반환하도록 하여 실제로 stale 실패를 별도 info 처리한다.

## 검증 결과와 분리한 실행 오류·경고 기록

- **외부 서비스의 429/레이트 리밋 오류는 관측하지 않았다.** AuthFlowHttpTest 및 ManualSyncCooldownHttpTest에서 검증한 429는 애플리케이션의 의도된 응답이며 검증 도구/외부 API의 레이트 리밋과 다르다.
- 지정 Gradle 명령은 첫 시도에 성공했다. 다운로드·네트워크 권한 실패나 재시도는 없었다. OpenJDK의 class sharing 경고는 있었으나 테스트 실패는 없었다.
- 추가 JShell 실험의 첫 두 시도는 classpath에서 저장소 엔티티를 찾지 못해 `package com.campuson.api.member does not exist`/`school does not exist` 및 후속 심볼 오류가 났다. 두 프로세스의 종료 코드는 0이었으나 이를 성공 증거로 사용하지 않았다. URLClassLoader로 실제 컴파일 디렉터리를 읽은 세 번째 실행에서 Hibernate/H2 실험과 위 REVIEW 출력을 얻었다.
- JShell은 Windows Java Preferences에 대해 `Windows RegCreateKeyEx(...) returned error code 5`, `Windows RegOpenKey(...) returned error code 2` 경고를 출력했다. 레지스트리 환경 경고이며 429가 아니다. Hibernate의 built-in connection pool 경고도 추가 메모리 실험에만 해당한다.
- 추가 H2 Shell 첫 시도는 PowerShell의 빈 `-password ''` 인자 전달 때문에 옵션이 밀려 `JdbcSQLFeatureNotSupportedException: Feature not supported`(50100-232), exit 1로 끝났다. V5 SQL의 실행 실패로 판정하지 않았다. 별도 메모리 DB의 비어 있지 않은 임시 비밀번호 인자로 다시 실행했고 V5 backfill은 exit 0으로 성공했다.
- 읽기 검색 중 PowerShell에서 그대로 전달된 wildcard/중괄호 경로가 rg에 의해 경로 또는 옵션으로 해석되는 오류, ADR-0005 파일명 추정 오류가 있었다. 파일 목록/정확한 디렉터리 경로로 다시 읽어 검증했다. 소스 결함이나 테스트 실패로 집계하지 않았다.

REQUEST_CHANGES
VERIFY-DONE
