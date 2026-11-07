# 남은 한계 6건 구현 결과

2026-11-08, Codex 단독 작업. git 명령·다른 에이전트·실제 학교 계정·실제 학교 서버·외부 DB·bootRun을 사용하지 않았다. 사용자 범위 SPRING_* 환경변수는 변경하지 않았다. 지정된 README/부채 원장 (B)(F)(G)·2차 검증/ADR-0005·0007·0008/ERD 인계/이전 재검증 보고서를 검토했다.

## 1. M9 학교 장애 시 탈퇴

- `src/main/java/com/campuson/api/global/security/JwtProvider.java:41`, `src/main/java/com/campuson/api/global/security/TokenClaims.java:3`, `src/main/java/com/campuson/api/global/security/AuthMember.java:4`, `src/main/java/com/campuson/api/global/security/JwtAuthenticationFilter.java:41`: access/refresh에 실제 학교 비밀번호 인증 시각 auth_time을 싣고 principal까지 전달한다. 클레임 없는 기존 JWT는 검증되지만 대체 경로는 불가하다.
- `src/main/java/com/campuson/api/auth/AuthService.java:35`: 학교 인증 완료 시각을 Clock에서 읽어 발급하고 refresh는 기존 auth_time을 복사한다. 새 iat를 최근 인증으로 해석하지 않는다.
- `src/main/java/com/campuson/api/auth/WithdrawalController.java:25`, `src/main/java/com/campuson/api/auth/WithdrawalService.java:31`, `src/main/java/com/campuson/api/global/error/ErrorCode.java:18`: 학교 통신 장애에만 기본 10분 대체를 허용한다. 오래됨/클레임 없음/미래 시각은 503, 틀린 비밀번호는 최근 인증과 무관하게 INVALID_PASSWORD 401이다. 탈퇴도 학번·클라이언트 예약 예산을 사용한다. 새 해시/비밀값을 저장하지 않는다. 삭제와 JWT/ICS 무효화는 기존 경계를 유지한다.
- `src/test/java/com/campuson/api/auth/RemainingAuthHttpTest.java:55`: 최근 인증 장애 탈퇴 204 → 사용자·일정·연결 삭제, access/refresh 401·ICS 404. 오래된 인증은 일정/피드/연결/회원 보존, refresh 후 601초는 503, 기존 클레임 없음은 503, 틀린 비밀번호 401을 검증한다. Clock을 이동하며 sleep은 없다. 최근 인증 대체 제거와 refresh 시각 갱신 변형에서 테스트 실패를 확인했다(원본 전체 코드 재실행은 아님).

## 2. M10 데이터 있는 PostgreSQL 업그레이드

- `src/test/java/com/campuson/api/SchemaUpgradeScenarios.java:9`, `src/test/java/com/campuson/api/SchemaUpgradeTest.java:2`, `src/test/java/com/campuson/api/PostgresSchemaUpgradeTest.java:7`: 두 시나리오를 공유한다. V2 회원/일정/피드/공지 id 보존·옛 계정 격리, V4 연결 행의 V5 legacy 세대·NOT NULL과 V6 nullable 초기값 및 lease/claim·throttle 데이터 쓰기를 확인한다. 시나리오마다 고유 H2 DB/PG schema를 사용한다.
- PostgreSQL 17 Testcontainers(disabledWithoutDocker=true) 테스트 2건을 작성·컴파일했다. Docker가 없어 로컬 실행은 skipped이며 H2 경로만 실행했다. PostgreSQL 실제 성공이나 기존 빈 DB validate 테스트 실행 성공으로 보고하지 않는다. 기존 PostgresSchemaTest 2건도 skipped다. 수정 전에는 테스트 자체가 없었고 수정 전 실패 실행은 하지 않았다.

## 3. 원문 id 우선 중복 제거와 DB 중복 정리

- `src/main/java/com/campuson/api/eclass/EclassHtmlParser.java:273`, `src/main/java/com/campuson/api/eclass/EclassTask.java:3`: todo goLecture의 세 번째 인자/hidden todo 값은 원문 item id다. 목록은 명시된 item/seq/id/report_id/quiz_id 쿼리 필드의 단일 값만 인정한다. 서로 충돌하는 여러 값은 미상으로 둔다.
- 근거: `src/test/resources/eclass/planary-todo.html:2`에는 goLecture('DESIGN101','report','TASK42')가 있다. 원래 목록 `src/test/resources/eclass/planary-report-list.html:4`는 KJKEY=DESIGN101만 있으며 **실학교 목록의 항목 id 확보를 증명하지 못한다**. 새 테스트의 reportView?seq=B, reportView?id=T9, quizView?item=Q1은 합성 HTML이다. 실제 학교에는 접속하지 않았다. 따라서 기존 관찰 픽스처는 휴리스틱을 유지한다.
- `src/main/java/com/campuson/api/eclass/HttpEclassGateway.java:170`: 양쪽 id가 있으면 과목·종류·item id가 같은 경우만 병합하고 다르면 제목/날짜가 같아도 보존한다. 한쪽 id 미상에서만 일대일·같은 KST 날짜/한쪽 미상 휴리스틱을 사용한다. 모호한 쌍은 보존한다.
- `src/main/java/com/campuson/api/schedule/EclassImportService.java:65`: 병합한 목록 external_uid를 alias로 전달하여 이번 수집에서 확정된 같은 회원 ECLASS 중복만 삭제한다. 정본 id와 양쪽 done의 OR를 보존한다. 개인·다른 회원 일정은 유지한다. 모호한 과거 중복을 제목만으로 삭제하지 않는다.
- `EclassHtmlParserTest.differentOriginalIdsWithSameTitleAndDayAreNeverMerged`, `quizListExplicitItemIdMatchesTodoDespiteChangedTitle`, `sameAssignmentOnTodoAndReportListKeepsOnlyTodoIdentity`, `EclassImportHttpTest.mergedAliasesRemoveOnlyOwnersDuplicateAndPreserveCanonicalIdAndDone` 검증. id 판정/alias 병합 제거 변형에서 실패했다. 처음 전체 실행에서 기존 T9 vs id=5 병합 기대 테스트가 새 정책상 실패하여 같은 id의 테스트로 고쳤고 다른 id 보존 테스트를 분리했다.

## 4. DB 로그인 제한과 동기화 lease

- `src/main/java/com/campuson/api/auth/LoginAttemptThrottle.java:31`: 메모리 Map/모니터 경로를 삭제했다. JDBC 시간 인자는 PostgreSQL JDBC가 지원하는 UTC OffsetDateTime으로 바인딩한다. DB mutex의 짧은 FOR UPDATE 트랜잭션에서 학번·IP의 실패 + 유효 예약을 함께 판정/예약한다. 성공은 학번 실패만 지우며 진행 중 예약과 IP 실패는 유지한다. 예약만으로 막히면 Retry-After 1초, 완료 실패는 창 종료까지다. 예약 UUID의 완료는 한 번만 반영한다. begin/finish에 만료 행 정리가 있다. 기본 예약 15초, 실효 하한은 학교 인증 전체 제한 + 5초다.
- `src/main/java/com/campuson/api/eclass/ConnectionCoordination.java:29`, `src/main/java/com/campuson/api/eclass/EclassSyncService.java:44`: 메모리 inFlight 삭제. 연결의 조건부 UPDATE와 소유자 UUID로 서버 간 선점한다. 수동 409/배경 생략, finally 해제·만료 회수. 옛 소유자가 새 lease를 해제하지 못한다. 기본 5분, 실효 하한은 전체 수집 2회 + 인증 기한 + 5초다.
- `LoginAttemptThrottleTest.independentInstancesShareReservationsAndExpiredLeaseCanBeReclaimed`은 독립 두 객체로 같은 DB의 정규화 버킷/예약·회수를 검증한다. 기존 한도/창/성공 초기화/Retry-After 및 AuthFlowHttpTest 동시 비밀번호 한도와 ManualSyncCooldownHttpTest를 유지했다. `DistributedSyncTest.twoIndependentServicesShareSingleFlightAndBackgroundSkips`, `expiredOwnersCannotReleaseNewSyncLeaseOrClaimAndExpiredClaimAllowsMutation`은 독립 서비스·조정 객체로 공유·만료·소유권을 확인한다. 이 항목의 원본 전체 코드 수정 전 실패 실행은 하지 않았다.

## 5. 프록시 IP와 학번 정규화

- `src/main/java/com/campuson/api/global/security/ClientAddressResolver.java:15`, `src/main/java/com/campuson/api/auth/AuthController.java:19`, `src/main/java/com/campuson/api/auth/WithdrawalController.java:25`: 기본 소켓 peer, 신뢰 CIDR peer만 XFF를 오른쪽부터 읽는다. 첫 비신뢰 주소를 택하며 하나라도 깨졌으면 peer로 돌아간다. IPv4/IPv6 수치 리터럴만 해석하여 DNS 조회를 하지 않는다. server.forward-headers-strategy=NONE이며 이 리졸버만 헤더를 해석한다.
- `src/main/java/com/campuson/api/auth/StudentIds.java:9`, `src/main/java/com/campuson/api/auth/LoginRequest.java:10`, `src/main/java/com/campuson/api/eclass/EclassAccountService.java:35`, `src/main/java/com/campuson/api/eclass/EclassSyncService.java:126`: NFKC→strip, 내부 공백/제어/format 문자·64자 초과 400, profile도 정규화하여 입력과 비교·계정 식별에 사용한다. throttle 키만 Locale.ROOT 소문자로 묶는다. 선행 0과 계정 식별의 대소문자는 바꾸지 않는다.
- 기존 ASCII 값은 변화가 없고 V3의 legacy 격리값은 이관하지 않는다. 과거 비정규 Unicode 학번의 자동 재작성·계정 병합은 하지 않는다. 실제 운영 DB는 읽지 않았으므로 이런 값이 존재하는지는 미검증이다.
- `ClientAddressResolverTest` 5건(불신 peer, 오른쪽 위조 체인, 잘못된 체인, IPv6, 반복된 XFF 헤더), `RemainingAuthHttpTest.normalizedStudentUsesSameAccountAndThrottleBudgetAndInvalidInnerCharactersAre400`(전각/앞뒤 공백 같은 회원·5회 예산, 내부 공백/탭/NUL/format 문자 400) 통과. 프록시 신뢰를 제거한 변형에서 2건 실패했다. 학번 정규화의 수정 전 실패 실행은 하지 않았다.

## 6. 재인증 직전 경합 제거

- `src/main/java/com/campuson/api/eclass/ConnectionCoordination.java:37`, `src/main/java/com/campuson/api/eclass/EclassSyncService.java:92`: 세대 G·sync 소유자·유효기간을 한 UPDATE에서 확인하고 claim을 커밋한 뒤 저장 비밀번호로 학교 요청한다. 종료 finally에서 소유자 조건으로 해제한다. 이전 isCurrent 경로는 삭제했다.
- `src/main/java/com/campuson/api/eclass/ConnectionCoordination.java:46`, `src/main/java/com/campuson/api/eclass/ConnectionCoordination.java:52`: 로그인/로그아웃/연결 해제/탈퇴는 사용자·연결 잠금 안에서 claim을 검사하여 활성 상태면 롤백하고 커넥션 없이 20ms 대기한다. 재시도 상한은 인증 제한 + 5초(기본 15초), 계속 활성/인터럽트 시 409다. 만료 claim은 즉시 통과한다. 이미 실행 중인 학교 요청을 취소하는 방식은 아니다.
- `src/main/java/com/campuson/api/eclass/HttpEclassGateway.java:235`, `src/main/java/com/campuson/api/eclass/HttpEclassGateway.java:274`: 인증은 여러 요청/리다이렉트를 합해 request-timeout(기본 10초) 안으로 제한한다. 수집은 호출당 전체 2분, 개별 요청 제한도 적용한다. 그래서 claim/예약 lease를 실제 HTTP 상한보다 길게 둔다.
- `DistributedSyncTest.logoutWaitsWithoutHoldingConnectionUntilSavedPasswordRequestCompletes`, `reloginWaitsForClaimAndOldSyncNeverOverwritesNewConnection`은 FakeEclass.hold에 실제 재인증이 들어간 뒤 변경을 시작하고 활성 claim에 막힌 시점의 latch를 기다린다. 풀리기 전에는 변경이 완료되지 않고 DB 조회는 가능하며, 해제 뒤 정상 완료·새 비밀번호/쿠키 보존을 확인한다. 만료 회수는 Clock 이동 테스트다. claim 검사 제거 변형 실행에서 두 테스트 모두 실패했다.
- 사용자 영향·대기 상한·시계 동기화·트랜잭션/커넥션 경계는 `docs/adr/0009-distributed-auth-sync.md`에 기록했다. 실제 여러 프로세스 부하나 서버 시계 차이는 미검증이다.

## 마이그레이션·문서

새 마이그레이션은 `src/main/resources/db/migration/V6__distributed_coordination.sql` 1개다. V1~V5는 변경하지 않았다. TEXT/ENUM은 없으며 주석은 -- 공백 형식이다. README, ADR-0005/0007/0008 및 신규 ADR-0009, ERD 인계, 부채 원장을 한국어로 갱신하고 테스트 수를 XML에 맞췄다.

## 최종 전체 테스트 결과

Git Bash, 작업 디렉터리 backend/:

```sh
GRADLE_USER_HOME=C:/Users/jinwoo/.gradle sh gradlew cleanTest test --console=plain
```

최종 실행은 위 명령의 stdout/stderr만 `build/final-test.log`로 리다이렉트했다. XML `build/test-results/test/TEST-*.xml` 전체를 합산했다.

| tests | failures | errors | skipped | 통과 |
|---:|---:|---:|---:|---:|
| 163 | 0 | 0 | 4 | 159 |

BUILD SUCCESSFUL. skipped는 PostgreSQL 17의 PostgresSchemaTest 2건·PostgresSchemaUpgradeTest 2건. Docker 없음으로 컴파일과 H2 경로만 검증됐다. 실제 학교·외부 DB·Docker/CI·JAR 47건 스모크는 실행하지 않았다.

## 회귀 변형 검증과 실행 중 오류·경고 (최종 결과와 분리)

원본 전체 코드를 수정 전으로 되돌린 검증은 하지 않았다. 임시로 이전 동작을 일부 재현하고 원복하는 변형 검증을 했다. 첫 변형 전체 실행은 tests=162/failures=7/errors=0/skipped=4로 의도대로 실패했다(`build/regression-probe.log`). 실패한 테스트:

- `com.campuson.api.auth.RemainingAuthHttpTest.outageWithRecentAuthenticationDeletesDataAndRevokesTokenAndIcs()`
- `com.campuson.api.auth.RemainingAuthHttpTest.refreshDoesNotRenewAuthenticationTimeOrOfflineEligibility()`
- `com.campuson.api.eclass.EclassHtmlParserTest.quizListExplicitItemIdMatchesTodoDespiteChangedTitle()`
- `com.campuson.api.eclass.EclassHtmlParserTest.differentOriginalIdsWithSameTitleAndDayAreNeverMerged()`
- `com.campuson.api.global.security.ClientAddressResolverTest.trustedChainIsReadFromRightAndStopsAtFirstUntrustedHop()`
- `com.campuson.api.global.security.ClientAddressResolverTest.ipv6CidrSupportsTrustedProxyAndClient()`
- `com.campuson.api.schedule.EclassImportHttpTest.mergedAliasesRemoveOnlyOwnersDuplicateAndPreserveCanonicalIdAndDone()`

강화한 claim 테스트에서 검사만 제거한 별도 실행 명령:

```sh
GRADLE_USER_HOME=C:/Users/jinwoo/.gradle sh gradlew cleanTest test --console=plain --tests "*DistributedSyncTest.logoutWaits*" --tests "*DistributedSyncTest.reloginWaits*"
```

2건 모두 의도한 실패(`build/claim-regression-probe.log`). 원래 구현을 finally로 복원하고 최종 전체 명령을 다시 실행해 통과했다. 초기 claim 테스트는 검사 제거에도 타이밍상 통과하여, 실제 Busy 예외가 발생한 뒤 latch를 여는 방식으로 강화했다.

개발 중 실제 오류: 저장소 루트에서 gradlew를 찾아 실행한 두 번은 `sh: gradlew: No such file or directory`로 종료했다. backend/ 작업 디렉터리로 바로잡았다. 존재하지 않는 EclassItem.java/build.gradle/EclassProperties.java 읽기와 PowerShell wildcard를 rg에 전달한 읽기 오류가 있었으며 올바른 파일·디렉터리로 다시 확인했다. 중복 보존 테스트 픽스처의 개인 일정에 due_at을 주지 않아 CHECK 위반이 났고 실제 도메인 제약에 맞게 수정했다(첫 수정 문자열은 PowerShell 파이프 Unicode 인코딩 때문에 적용되지 않아 재수정). 기존 T9 vs id=5 병합 기대는 새 정책에 맞게 수정했다. 이 개발 중 실패를 최종 0 failure와 섞지 않았다.

후속 점검에서 JDBC 시간 인자를 UTC OffsetDateTime으로 명시하고 반복 XFF 헤더를 하나의 체인으로 처리했다. 재실행 중 독립 서비스 학교 진입 latch가 한 번 5초 내 열리지 않았다(당시 선행 Future 예외는 수집되지 않아 원인을 단정하지 않음). 대기 실패 시 Future 예외를 드러내도록 보강했고, 0초 쿨다운이 미래 updated_at에서 활성화되지 않도록 조건을 고쳤다. 독립 서비스 테스트는 updated_at을 1초 미래로 넣어 이 경계를 결정적으로 검증한다. 최종 전체 재실행 163건은 통과했다.

최종 Gradle 출력에 컴파일/JVM 경고는 0건이다. 테스트 안의 의도적인 실패·거절·제약 검증에서 발생하는 애플리케이션 로그는 별개다. 외부 API 429/레이트 리밋 오류는 관측하지 않았다. 검증한 429는 이 애플리케이션의 의도된 throttle/쿨다운 응답이며 외부 API 오류가 아니다.

FIX-DONE
