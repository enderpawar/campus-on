# Claude 수정 독립 검증 결과

검증일: 2026-11-07. 대상: 현재 `backend/` 코드와 테스트, README, ADR-0005~0008, 부채 원장, ERD 인계 문서. Claude 원문 리뷰는 검증 항목을 찾는 데만 사용하고 구현·단언을 직접 확인했다. 아래 Java 경로는 `backend/src/main/java/com/campuson/api/` 기준, `test/`는 `backend/src/test/java/com/campuson/api/` 기준이다. 문서 경로는 `backend/` 기준이다.

판정: **REQUEST_CHANGES**. 비밀번호 폐기·이름 없는 과목·ICS 차단 등은 구현됐다. 그러나 동시 로그인으로 실패 제한을 초과할 수 있고, 제목 중복 제거로 다른 과제가 누락되며, 재로그인과 이전 동기화 사이의 세대 구분이 없다.

## 실행 범위와 증거 수준

- 소스/문서/기존 테스트 XML을 읽었다. git 명령, 실제 학교 계정·외부 DB 접속, 앱 실행은 하지 않았다. 직접 작성한 파일은 이 보고서 하나다. 테스트 명령의 Gradle 내부 산출물 갱신은 허용된 실행 범위다.
- Git Bash에서 `backend/`를 작업 디렉터리로 `sh gradlew test --console=plain`을 실행했다. 첫 실행은 Gradle 다운로드 단계의 네트워크 권한 오류로 종료됐다.
- 실행 프로세스의 `GRADLE_USER_HOME=C:/Users/jinwoo/.gradle`만 지정해 같은 명령을 다시 실행했다. **BUILD SUCCESSFUL in 8s**, 모든 태스크(특히 `:test`)가 **UP-TO-DATE**였다. 사용자 `SPRING_*` 환경변수는 지우거나 변경하지 않았다.
- 따라서 이번 실행에서 130개 테스트를 새로 수행했다고 주장하지 않는다. 아래 통과 수치는 기존 XML 집계와 현재 소스/Gradle의 최신성 판정에 대한 확인이다. 새 테스트 파일이나 임시 재현 코드는 만들지 않았다. 동시성·오탐 결함은 명시한 실행 순서와 입력에 대한 정적 검증이다.

## 주장별 판정

### 1. H1 — 확인

- `eclass/EclassConnection.java:32`: `INVALID_CREDENTIALS`이면 암호화 비밀번호를 null로 하고 `REAUTH_REQUIRED`를 기록한다. `ECLASS_REAUTH_REQUIRED`도 같은 상태로 간다.
- `eclass/EclassConnectionRepository.java:14`: `PENDING/READY`는 cutoff 이하, `FAILED`는 failedCutoff 이하이며 ACTIVE 회원만 선택한다. `REAUTH_REQUIRED`는 선택되지 않는다. `eclass/EclassSyncService.java:63`에서 각각 syncDelay·failureBackoff를 빼며 기본값은 `:37`~`:39`의 PT5M/PT30M/PT1M이다.
- `eclass/EclassSyncService.java:81` 및 `:102`: 세션 만료이고 저장 비밀번호가 있을 때만 재인증한다. 거절은 `:91`의 실패 기록 경로로 간다. 정상적인 직렬 실행에서는 같은 틀린 저장 비밀번호로 재인증하지 않는다.
- `test/eclass/EclassSyncHttpTest.java:83`은 실제 만료→비밀번호 변경→배경 실행→상태/비밀번호/인증 횟수→두 번째 배경 실행의 인증 횟수를 확인한다. 두 번째 직접 호출은 여전히 만료 쿠키 fetch를 할 수 있지만, 학교 **로그인** 호출 수는 늘지 않고 자동 선택에서도 제외된다.
- 단, 재로그인과 겹친 이전 작업이 새 연결을 실패 처리할 수 있는 문제는 발견 F3이다.

### 2. H2 — 확인

- `schedule/EclassImportService.java:71`: null/공백 이름은 fitted=null. 새 과목은 `:76`에서 생성하지 않는다. 기존 과목은 계속 사용하고 소속을 연결한다.
- `catalog/Course.java:43`: null/공백 이름으로 기존 이름을 덮지 않는다.
- `test/schedule/EclassImportHttpTest.java:159`는 이름 없는 신규 과목의 일정은 저장하면서 courseId=null·내 과목 0개를 확인한다. `:174`는 기존 이름 유지와 일정의 과목 연결을 확인한다. 단순히 예외가 없다는 검사보다 강한 단언이다.

### 3. H3 — 부분

- 순차 요청의 계약은 확인했다. `auth/LoginAttemptThrottle.java:26`의 기본값은 학번 5회·클라이언트 20회·15분이다. 첫 실패부터 시작하는 고정 창이며 `:59`에서 증가하고 창을 연장하지 않는다. `:48`은 해당 학번 키만 지운다.
- `auth/AuthService.java:30`은 strip한 학번으로 학교 요청 전에 check하고, `INVALID_CREDENTIALS`만 failed로 센다. `auth/AuthController.java:18`은 getRemoteAddr를 전달한다. `auth/WithdrawalService.java:24`는 같은 학번 카운터만 사용한다.
- `global/error/GlobalExceptionHandler.java:47`은 429 응답에 Retry-After를 붙인다. `global/error/RateLimitedException.java:9`는 초로 올림하고 최소 1초를 준다. 한도에 도달한 이후 요청이 차단되며, 5번째 실패 자체는 학교 인증을 거친 401이다.
- `eclass/EclassSyncService.java:45`의 원자적 집합 add/finally remove는 같은 JVM의 동시 수동/배경 실행을 막는다. 수동 중복은 409, 배경 중복은 skip이다. 쿨다운은 DB updatedAt 기준 429이다.
- **실패 한도를 동시 요청에 대해 보장하지 못한다(F1).** 단일 인스턴스에서도 발생하며, 분산 메모리 한계와 별개다.

### 4. M1 — 확인

- `schedule/CalendarFeedRepository.java:14`는 ACTIVE만 조회한다. `schedule/CalendarFeedService.java:65`는 부재를 `CALENDAR_FEED_NOT_FOUND`로 변환한다.
- `test/schedule/IcsHttpTest.java:50`은 같은 피드의 기존 200을 먼저 확인하고 사용자를 SUSPENDED로 변경한 뒤 404와 오류 코드를 확인한다. 잘못된 토큰 때문에 통과하는 테스트가 아니다.

### 5. M2 — 확인(길이 단위 주의)

- `schedule/ImportLimits.java:18`은 제목/과목명을 코드포인트로 자른다. URL(`:24`)과 외부 UID(`:29`)의 상한 판정은 Java UTF-16 length 기준이다. 따라서 보충 문자가 포함되면 URL은 더 일찍 버려지고 UID는 더 일찍 해시될 수 있다. 컬럼 초과 방지라는 목적에는 보수적인 동작이다.
- `schedule/EclassImportService.java:60`~`:66`, `:73`에서 실제 저장 경계에 적용한다. UID는 UTF-8 SHA-256, `h:`와 소문자 hex 64자다.
- `test/schedule/EclassImportHttpTest.java:189`는 250자 한글 제목과 2000자 초과 URL만 검증한다. 300자 과목명, 긴 UID, 서로게이트 쌍 경계는 테스트로 확인하지 않는다. 해당 구현은 소스로 확인했다.

### 6. M3 부분 수정 — 부분

- `eclass/HttpEclassGateway.java:172`는 해시 형식 ID의 목록 항목을 비해시 ID의 todo 항목과 과목키·종류·정규화 제목으로 맞춰 제거한다. 접두사/공백/대소문자 정규화는 `:197`이다. todo 날짜가 없으면 목록 날짜를 보충한다(`:190`). fetch 결과에 실제 적용한다(`:154`).
- `test/eclass/EclassHtmlParserTest.java:172`는 파서가 만든 2개를 1개로 만들고 todo ID·목록 마감을 유지하는 것을 확인한다. 단, 테스트 자체가 todo `T9`와 목록 `id=5`를 동일하다고 가정하며 실제 항목 ID 일치는 검증하지 않는다.
- **동일 제목의 다른 과제까지 제거하는 오탐이 있다(F2).** 모든 todo가 실제 ID를 갖는 것도 아니므로 비해시=todo/해시=목록이라는 구분도 완전하지 않다(`eclass/EclassHtmlParser.java:137`, `:194`). ID 없는 todo와 목록은 둘 다 해시여서 합쳐지지 않을 수 있다.
- 잔존/숨김 미구현은 사실이다. `schedule/EclassImportService.java:41`은 없는 항목을 삭제하지 않고, `schedule/Event.java:141`의 삭제 제한도 남아 있다. 이전 수집으로 이미 DB에 생성된 목록 ID 중복을 dedupe가 정리하지도 않는다.

### 7. M4/M5/L5 — 확인

- `eclass/EclassHtmlParser.java:200`, `:240`: 연도 없는 한국어 날짜는 KST 오늘과 거리(일수)가 가장 짧은 -1/0/+1년의 유효 날짜를 고른다. 동률은 먼저 순회한 과거 쪽이다. 2월 29일은 후보 중 윤년이 있어야 해석되며, 세 해 모두 비윤년이면 null이다. 이 결과는 명시된 후보 범위와 일치한다.
- `test/eclass/EclassHtmlParserTest.java:148`은 12월→다음 1월, 1월→지난 12월을 확인한다. 동률·윤일·KST 자정 경계는 이 추가 테스트에 없다. D-day가 같이 있어도 명시 날짜 해석을 교차 보정하지 않는 점도 남는다. nearestYear는 날짜 정보가 부족할 때의 휴리스틱이므로 오래된/먼 미래 학기 날짜까지 정확하다고 볼 수 없다.
- `eclass/EclassHtmlParser.java:295`는 한글 포함 1~40자 KJ를 허용한다. `test/eclass/EclassHtmlParserTest.java:132`, `:141`은 한글 data 속성·인코딩 쿼리와 40/41자 경계를 확인한다.
- `eclass/EclassHtmlParser.java:156`은 orElse(null) 후 행 텍스트로 대체한다. `:232`는 24:00(:00 포함)을 다음 날 00:00으로 처리하고 24:01은 거부한다. `test/eclass/EclassHtmlParserTest.java:156`, `:161`의 회귀 단언도 해당 분기를 실제로 지난다.

### 8. M7 — 부분

- `eclass/EclassSecrets.java:35`의 해독 실패는 `UnreadableSecretException`이다. `eclass/EclassSyncService.java:87`에서 memberId만 warn하고 `accounts.secretsUnreadable`을 거쳐 비밀번호 삭제·REAUTH_REQUIRED로 간다(`eclass/EclassConnection.java:39`).
- 예상 못 한 RuntimeException은 `eclass/EclassSyncService.java:95`에서 memberId와 예외 클래스만 warn 후 ECLASS_UNAVAILABLE로 기록한다. 메시지·스택·쿠키·비밀번호는 이 로그에 넣지 않는다.
- **모든 나머지 예외가 warn되는 것은 아니다.** BusinessException은 `:91`에서 로그 없이 기록/재던짐한다. 실제 네트워크 오류도 gateway가 BusinessException으로 바꾸므로 운영 실패 로그 공백이 남는다(F4). 문서의 “예상 못 한 예외”라는 좁은 표현은 맞지만 “그 밖의 예외 전부”라는 주장은 성립하지 않는다.

### 9. M8 — 부분

- `auth/AuthService.java:62`의 한 트랜잭션에서 token_version 증가와 `accounts.forgetConnection`을 호출한다. `eclass/EclassAccountService.java:99`의 REQUIRED 트랜잭션은 호출자에 합류한다. 연결이 없어도 logout은 성공한다.
- 로그아웃만 겹치고 재로그인이 없다면 기존 작업은 연결을 새로 만들지 못한다. 성공 반영은 사용자 락 후 현재 연결의 존재를 요구한다(`eclass/EclassAccountService.java:65`). logout의 사용자 UPDATE와 직렬화돼, 먼저 반영 후 삭제되거나 삭제 후 반영이 거부된다. 실패 처리도 findById().ifPresent()여서 재생성하지 않는다.
- 다만 로그아웃이 이미 진행 중인 HTTP를 취소하지는 않는다. 이전 context의 쿠키/비밀번호로 fetch/재인증이 이어질 수 있다(`eclass/EclassSyncService.java:77`~`:84`). 즉 **향후 스케줄 대상 제거**는 맞고 **로그아웃 응답 이후 학교 요청이 전혀 없음**은 보장하지 않는다. 재로그인이 겹치면 F3의 이전 작업 반영 문제도 있다.
- `test/auth/AuthFlowHttpTest.java:85`는 직렬 logout의 토큰 무효화·행 삭제를 검증한다. `test/auth/LogoutConcurrencyTest.java:36`은 동시 logout 증가를 검증하며 진행 중 동기화 경합 검증은 아니다.

### 10. M6 테스트의 실효성 — 부분

- `test/support/EclassTestConfiguration.java:26`은 실제로 학번별 비밀번호·스냅샷·세션 만료·auth 호출 수를 조절한다. `:42`는 만료 쿠키를 거부하고 유효 쿠키의 스냅샷을 반환한다. 가짜가 무조건 성공해서 위 분기들이 건너뛰어지는 구조는 아니다.
- `EclassSyncHttpTest` 6개는 실제 sync/import 서비스·저장소·HTTP 계약을 탄다. `:46`은 비어 있지 않은 스냅샷의 일정/과목명을 검사하고, `:63`은 만료 후 인증 횟수 2회를 확인한다. `:109`는 깨진 암호문을 DB에 넣어 비밀번호 폐기와 추가 인증 없음까지 확인한다.
- `:72`의 이름은 “LeavesTheSchedule”이지만 일정을 미리 만들지 않아 일정 보존까지 증명하지는 않는다. `:100`은 fetch 장애를 주입하지 않고 accounts.failed를 직접 호출하므로 **실패 상태의 선택 cutoff**만 검증한다.
- `ManualSyncCooldownHttpTest` 1개(`:28`)는 로그인 직후 유효 JWT로 sync를 요청하고 429/code/Retry-After를 검사한다. 세션을 미리 만료시켜 인증 횟수로 통과 우회를 잡는다. 쿨다운 만료 후 허용은 검증하지 않는다.
- `LoginAttemptThrottleTest` 3개(`:17`, `:28`, `:34`)는 Clock으로 만료/키 분리/성공 초기화를 인과적으로 확인한다. 다만 사용자 설정 3/5로 시험하며 기본 5회는 `AuthFlowHttpTest.java:36`이 별도로 확인한다.
- single-flight 409, Async 프록시 경로, 스케줄러 메서드 전체 실행, logout/relogin 경합, 탈퇴 실패 한도 HTTP, 변형 학번·프록시 정책, 동시 스로틀을 검증하는 추가 테스트는 없다. Fake의 실명은 여전히 “테스트”이며 실제 gateway(`eclass/HttpEclassGateway.java:105`)는 null이므로 실학교 프로필 추출이나 신원 선택자 검증도 증명하지 않는다.

### 11. 132개 중 130개 통과·실패 0·2 skipped — 부분(기존 산출물 수치 확인)

- 현재 테스트 소스의 `@Test`는 132개이며 ADR-0007 표의 클래스별 수와 모두 일치한다.
- 기존 `backend/build/test-results/test/TEST-*.xml` 25개를 XML로 합산한 결과 tests=132, failures=0, errors=0, skipped=2이다. `TEST-com.campuson.api.PostgresSchemaTest.xml:2` 및 `:4`~`:8`의 두 메서드만 skipped다. 예: `TEST-com.campuson.api.eclass.EclassSyncHttpTest.xml:2`에 tests=6이다.
- XML timestamp는 2026-11-07T12:41 무렵(UTC)인 **기존 실행 결과**다. Docker 없이 skip하도록 한 선언은 `test/PostgresSchemaTest.java:24`에서 확인된다. XML 자체는 skip의 구체적 사유를 담지 않아 이번에 Docker 부재를 별도 실행으로 확인했다고 하지는 않는다.
- 이번 허용 명령은 UP-TO-DATE였다. 따라서 새 실행으로 130개 통과를 독립 재현했다는 수준까지는 확인하지 못했다.

### 12. 문서 일치·테스트 이름·ErrorCode 22개 — 부분

- `global/error/ErrorCode.java:8`~`:29`를 직접 세면 22개다. README `:44`~`:48`, ADR-0005 `:17`~`:18`, ADR-0006 `:11`~`:12`, ADR-0007 `:24`~`:43`, 부채 원장 `:148`~`:165`는 수정된 설정/오류/테스트 개수와 대체로 일치한다.
- 새 수정 근거로 인용한 테스트 메서드는 실제 존재한다. 원장의 (A) `:11`~`:15`는 삭제된 옛 이름을 명시적으로 역사로 구분했고, (C) `:75`도 이메일 인증 시절 기록임을 밝힌다. 거기의 옛 메서드 이름을 현행 검증 근거로 간주하지 않았다.
- ADR-0008 `:42`에 ECLASS_ENCRYPTION_KEY가 추가됐고 `src/main/resources/application-prod.yml:13`과 맞는다. README `:67`의 비활성 로그인 403도 구현과 맞는다.
- 다만 “중복 제거 완료” 및 “수집 실패 로그”의 보장 수준은 F2/F4만큼 좁혀야 한다. 문서 자체의 작은 불일치도 있다(F5).
- README `:171`은 47개 HTTP 스모크가 수정 전 실행이며 수정 후 다시 실행하지 않았다고 명확히 구분한다. ADR-0007 `:45`와 erd-handoff `:72`는 같은 시간적 구분을 그 자리에서 설명하지 않는다. 47건 실학교/수정 후 재검증 증거로 해석하면 안 된다. 이번에는 해당 스모크를 실행하지 않았다.

### 13. 남긴 부채에 대한 판단 — 부분(미구현 사실 확인, 우선순위는 일부 재검토)

- **M9:** `auth/WithdrawalService.java:21`은 실시간 학교 인증 성공 후에만 삭제한다. 원장 `:69`의 정책 결정 대기는 타당하다. 대체 탈퇴·지원 경로의 요구사항을 결정할 필요가 있으며 JWT가 유효해도 학교 장애/계정 소멸로 삭제 불가한 사실을 해소한 것으로 보면 안 된다.
- **M10:** `test/SchemaUpgradeTest.java:11`은 H2 데이터 업그레이드만 검사하고 `test/PostgresSchemaTest.java:22`는 새 컨테이너에 매핑/제약을 검사한다. Docker가 필요한 **실행 미검증**은 타당하나, Docker 부재가 테스트 코드 작성 자체를 막지는 않는다. 원장 `:31`대로 PostgreSQL 데이터 포함 V2→V4+validate 테스트를 작성하고 Docker CI를 배포 조건으로 삼는 편이 타당하다.
- **M3 잔존·숨김:** 원장 `:46`의 미구현은 사실이다. 부분 수집에서 즉시 삭제하지 않는 결정은 합리적이다. 사용자 숨김/last_seen 설계는 후속으로 둘 수 있지만, F2의 정상 항목 누락은 단순 잔존 부채로 미룰 문제가 아니다.
- **장기 미사용 수집:** 원장 `:47`은 ACTIVE지만 앱을 오래 쓰지 않는 회원을 뜻한다. 실제 SUSPENDED 회원은 쿼리에서 제외된다. 활동 시각 기반 수집 중단 규칙 미정은 사실이며 보유·동의 정책과 함께 결정하는 것이 타당하다.
- **단일 인스턴스 메모리:** 원장 `:48`대로 재시작/다중 인스턴스에는 공유되지 않는다. 단일 인스턴스 시제품의 명시적 한계로 둘 수 있다. 다만 F1은 그 범위 안에서도 발생하므로 분산 저장소 도입 때까지 보류할 근거가 되지 않는다.

## 새로 확인한 결함과 구체적 수정안

### F1. High — 동시 요청이 학교 비밀번호 실패 한도를 초과한다

- 위치: `auth/LoginAttemptThrottle.java:34`, `:40`, `auth/AuthService.java:32`, `:34`, `:36`, `auth/WithdrawalService.java:24`.
- 근거: check는 현재 완료된 실패 수를 읽기만 한다. 학교 HTTP가 끝난 후에야 failed가 카운터를 증가시킨다. 예를 들어 실패 4회 상태에서 같은 학번 요청 20개가 모든 학교 응답보다 먼저 check를 통과하면 20개 모두 학교 인증으로 간다. 처음 실패가 0인 경우에도 동일하다. ConcurrentHashMap.compute는 증가 유실만 막고 검사→학교 요청 사이의 입장을 제한하지 않는다. 따라서 정확한 학번·동일 IP만 사용해도 학교 잠금 유발을 충분히 줄이지 못한다. 기존 테스트는 모두 순차다.
- 수정안: 학번/IP별 원자적 입장 예약 API를 도입해 완료 실패와 진행 중 시도를 함께 예산에 포함한다. 학교 HTTP 동안 DB 트랜잭션을 잡지 말고 예약 토큰으로 결과를 완료/반환한다. 모든 로그인/탈퇴 인증이 같은 정책을 공유하게 한다. latch로 학교 응답을 보류하고 한도보다 많은 동시 요청을 보내 실제 gateway 입장 수와 429를 검증한다.

### F2. Medium — 제목 기반 dedupe가 서로 다른 과제를 조용히 누락시킨다

- 위치: `eclass/HttpEclassGateway.java:175`, `:182`, `:197`; `eclass/EclassHtmlParser.java:179`.
- 근거: 과목 K1에 todo `K1:report:A`, 제목 `[과제] 주간 보고서`, 마감 9/10과 별도 목록 `reportView.acl?id=B`, 제목 `주간 보고서`, 마감 9/17이 있으면 key가 같아서 목록 B는 dropped에 들어간다. 실제 ID·원문 URL·서로 다른 마감을 비교하지 않는다. 두 todo A/B가 같은 제목이면 목록 날짜 하나가 날짜 없는 두 todo에 공통 보충될 수도 있다. 현재 테스트도 ID T9와 id=5를 일치 확인 없이 합친다. 완료되어 todo에서 빠진 이전 과제와 새 동명 과제가 함께 있는 경우 특히 문제가 된다.
- 수정안: 원문 item ID를 양 경로에서 추출하고 같은 과목/종류/항목 ID로 통일한다. ID가 없으면 최소한 일대일 후보·동일 원문 URL 또는 일치하는 마감 등 추가 근거를 요구하고 모호한 매칭은 보존한다. 서로 다른 ID/마감의 동명 과제, 두 todo+두 목록, ID 없는 todo, 기존 DB 중복에 대한 회귀/이관 전략을 추가한다.

### F3. Medium — 이전 동기화가 재로그인으로 갱신된 연결을 덮는다

- 위치: `eclass/EclassAccountService.java:43`, `:56`, `:65`, `:75`; `eclass/EclassSyncService.java:77`, `:86`; `eclass/EclassConnection.java:12`.
- 근거: context에는 연결 세대/version이 없고 결과 저장은 memberId만으로 현재 연결을 찾는다. 실행 순서: A가 옛 쿠키/비밀번호를 읽고 HTTP 대기 → logout이 행 삭제 → 같은 사용자가 login하여 새 연결 생성 → A가 완료되어 새 행에 옛 쿠키/READY를 기록한다. A의 옛 비밀번호가 거절되면 accounts.failed가 새 정상 연결의 비밀번호를 삭제하고 REAUTH_REQUIRED로 만들 수도 있다. 로그인만 겹쳐도 같은 문제다. inFlight는 sync끼리만 직렬화하므로 authenticated/logout에는 적용되지 않는다.
- 수정안: 로그인/연결 생성마다 교체되는 generation UUID를 저장하고 context에 포함한다. 성공·실패·복호화 실패 반영은 현재 generation과 일치할 때만 사용자 락 아래 수행한다. 삭제 후 재생성을 구별하지 못하는 단순 초기화 버전은 피한다. 취소 상태도 작업 경계에서 확인하여 logout 후 후속 fetch/재인증을 중단하고, 이미 전송된 요청을 회수하지 못한다는 계약은 명시한다. latch 기반 logout→relogin/재로그인 단독 경합 테스트를 추가한다.

### F4. Low — 실제 학교 통신 장애는 여전히 warn 없이 처리된다

- 위치: `eclass/HttpEclassGateway.java:295`, `:302`; `eclass/EclassSyncService.java:91`, `:95`.
- 근거: HTTP 비정상 상태/IOException은 BusinessException(ECLASS_UNAVAILABLE)으로 바뀐다. sync의 BusinessException catch는 DB에 실패만 남기고 warn하지 않는다. RuntimeException catch의 새 로그가 실제 통신 장애를 포착하지 못한다. M7의 예상 못 한 예외 진단은 개선됐지만 일반 수집 장애의 로그 가시성은 여전히 부족하다.
- 수정안: BusinessException 경로에도 memberId·안전한 ErrorCode만 담는 로그를 추가한다(일상적인 재로그인 요구는 info 또는 지표로 구분 가능). 비밀값/원문 메시지는 제외하고 gateway 장애 주입 테스트에서 로그·상태를 함께 확인한다. 아니면 문서를 “예상 못 한 런타임 예외만 warn”으로 정확히 한정한다.

### F5. Low — 문서의 일부 현행 계약 설명이 실제 동작과 다르다

- 위치: `docs/erd-handoff.md:48`, `docs/adr/0007-test-strategy.md:84`, `src/main/resources/application-test.yml:14`.
- 근거: 인계 문서는 이름 없는 과목의 일정은 과목 없이 수집된다고 일반화하지만 기존 과목이 있으면 연결한다(`schedule/EclassImportService.java:74`, `:80`; `test/schedule/EclassImportHttpTest.java:185`). ADR의 “slice 테스트는 EventController 하나”와 달리 같은 문서 `:21`의 GlobalExceptionHandlerWebMvcTest도 slice다. test 설정 주석은 쿨다운을 EclassSyncHttpTest가 켠다고 쓰지만 실제로 켜는 클래스는 ManualSyncCooldownHttpTest다.
- 수정안: “기존 과목이 없고 이름도 없을 때만 과목 없이 수집”으로 한정하고 slice 클래스 수/이름과 쿨다운 전용 테스트 이름을 고친다. 새 테스트 근거 이름과 132/130/2·ErrorCode 22라는 수치 자체는 맞다.

## 요청받은 경계에 대한 추가 의견

- **Async 프록시:** `global/config/EclassExecutionConfig.java:10`이 Async를 켜고 `:14`가 명명된 executor를 제공한다. AuthService의 주입된 다른 빈 호출(`auth/AuthService.java:45`)이 프록시를 지나므로 requestSync의 Async가 self-invocation으로 무효화되는 구조는 아니다. 같은 대상 빈의 inFlight를 수동/Async/스케줄 실행이 공유한다. 스케줄러 내부 syncInBackground 호출은 의도적으로 동기 실행이며, 전체 회원 처리는 순차다. 테스트에서는 sync-enabled=false여서 이 프록시 실행을 검증하지 않는다.
- **프록시 IP:** 현 구성에는 forward-headers-strategy 설정이 없다(`src/main/resources/application.yml:1`). 프록시 뒤 기본 getRemoteAddr라면 모든 사용자가 프록시 IP의 20회 예산을 공유해 서로 차단한다. 이것만으로 공격자가 IP별 예산을 우회한다고 단정할 수는 없다. 반대로 신뢰 범위 없이 전달 헤더를 받도록 설정하면 주소 위조 여지가 생기므로 신뢰 프록시/직접 접근 차단을 함께 정해야 한다. README `:48`의 설정 한 줄만으로 배포 정책이 완성되지는 않는다.
- **학번 변형:** 앞뒤 Character.isWhitespace 계열 공백은 AuthService.strip으로 합쳐져 이 변형은 카운터 우회가 안 된다. 대소문자/선행 0/그 외 학교가 허용할 수 있는 변형은 통일하지 않는다. 실제 gateway는 입력 학번을 그대로 profile에 돌려주므로 응답 학번 비교로 실학교 정규화 여부를 확인할 수 없다(`eclass/HttpEclassGateway.java:105`). 실제 학교가 숫자 학번만 받는지/변형을 같은 계정으로 보는지는 이번 범위에서 미확인이다. 확인된 학교 규칙으로 형식을 제한하고 학교/계정 식별자를 정규화해야 하며, 무조건 소문자화하는 추측 구현은 권하지 않는다.
- **쿨다운 UX:** `eclass/EclassAccountService.java:82`는 마지막 성공·실패·로그인 완료 시각을 공통으로 쓴다. 시도 시작 시각이 아니며 실패 백오프도 완료 시각 기준이다. 로그인 직후 수동 sync 1분 차단은 문서화된 의도와 맞지만, 비동기 큐 거절/대기 중에는 첫 수집도 못 한 PENDING 사용자가 수동으로 복구할 수 없다. 제품에서 즉시 재시도를 허용하려면 last_manual_attempt_at/시도 시작 시각을 분리하고 첫 수집은 별도 정책으로 정하는 편이 낫다. 현재 구현 오류로 단정하지는 않는다.

## 검증 결과와 분리한 오류 기록

- 외부 서비스의 **429·레이트 리밋 오류는 만나지 않았다**. 코드/테스트의 기대 응답 429는 외부 도구 레이트 리밋과 구분한다.
- 첫 테스트 시도: `https://services.gradle.org/distributions/gradle-8.14.6-bin.zip` 다운로드 중 `Attempt 1/1 failed. Reason: Permission denied: getsockopt`, `java.net.SocketException: Permission denied: getsockopt`, exit code 1. 네트워크 권한 오류이며 429가 아니다. 기존 Gradle 사용자 캐시 지정 후 같은 명령은 exit code 0, UP-TO-DATE였다.

REQUEST_CHANGES
VERIFY-DONE
