# e-class 인증·ERD 확장 독립 검토 (좌측 Claude)

- 대상: `backend/` 현재 작업 트리 (auth/, member/, eclass/, catalog/, schedule/, notice/, V3/V4, application*.yml, compose.yaml, `.github/workflows/backend-ci.yml`, src/test, README·ADR·erd-handoff·ledger)
- 방법: 코드 정적 읽기만 했다. 메인 검토자가 auth·eclass·member·security·V3을 직접 읽었고, 읽기 전용 scout 3개가 각각 (schedule/catalog/notice/V4/ERD), (테스트의 엉뚱한 통과), (문서·설정·로그)를 맡았다. 아래 지적은 모두 메인 검토자가 해당 줄을 다시 확인했다.
- 하지 않은 것: 코드 수정, git, 빌드·테스트·앱 실행, 실제 학교·외부 DB 접속. 이 파일만 썼다.
  - 리뷰 시작 전, 이전 작업(Codex 변경 이전)에서 띄워 둔 내 `bootRun` 서비스가 남아 있어 종료했다. 이번 검토를 위해 실행한 것은 아니다.
- 참고 저장소 `github.com/yoobinkim541/plannary_smart_planner`는 웹·GitHub API 모두 **404**였다(비공개이거나 주소가 다름). 그래서 실제 폼 필드·로그인 성공 판정을 원본과 대조하지 못했다. **429 한도 오류는 이번 검토 중 한 번도 나오지 않았다.** 아래 결과는 모두 실제로 수행한 검토다.

## 결론

트랜잭션 경계, 탈퇴 원자성, 새 id 재진입, JWT/token_version, 쿠키 격리·리다이렉트 출처 검증, 비밀값 암호화 저장, 수집 id·완료 보존은 주장대로 구현되어 있다. 하지만 **학교 계정 잠금과 수집 전체 실패로 이어지는 결함이 3건(High)** 있고, 레거시 격리의 구멍(ICS)과 수집 데이터 정합성 문제가 Medium으로 남는다. **REQUEST_CHANGES**.

## High

### H1. 바뀐(틀린) 저장 비밀번호로 5분마다 학교 로그인을 무한 재시도 → 학교 계정 잠금 위험
- `eclass/EclassSyncService.java:34-37`: fetch가 `ECLASS_REAUTH_REQUIRED`이고 저장 비밀번호가 있으면 `gateway.authenticate(studentId, password)`.
- 실패하면 `:41-42`에서 `accounts.failed(memberId, INVALID_CREDENTIALS)`. `EclassConnection.failed`(`eclass/EclassConnection.java:28-31`)는 상태를 `FAILED`로 바꾸고 `updatedAt`만 갱신한다. **암호문 비밀번호는 그대로 남는다.**
- `eclass/EclassConnectionRepository.java:10` `findDueMemberIds`는 `updatedAt <= cutoff`만 보고 상태를 거르지 않는다. `scheduledSync`(`EclassSyncService.java:53-58`)가 `sync-delay`(기본 PT5M)마다 같은 회원을 다시 고른다.
- 결과: 사용자가 학교 비밀번호를 바꾸면 서버가 옛 비밀번호로 하루 약 288회 로그인 실패를 낸다. 학교 SSO에 실패 횟수 잠금이 있으면 학생의 실제 학교 계정이 잠긴다 [INFERENCE: 학교 잠금 정책은 확인하지 않음]. 저장 비밀번호가 없는 `REAUTH_REQUIRED` 연결도 5분마다 만료 쿠키로 upstream을 호출한다.
- 제안:
  - 저장 비밀번호로 재인증했는데 `INVALID_CREDENTIALS`면 `encrypted_password`를 지우고 `REAUTH_REQUIRED`로 바꾼다.
  - `findDueMemberIds`는 `READY`/`PENDING`만 고르고, `FAILED`는 지수 백오프를 둔다.
  - 회귀 테스트: 저장 비밀번호가 틀린 상태에서 스케줄을 2회 돌렸을 때 `authenticate` 호출이 1회 이하인지 확인한다.

### H2. 이름 없는 과목 하나가 그 사용자의 수집 전체를 매번 실패시킴 (NOT NULL 위반)
- 파서가 의도적으로 이름 없는 과목을 만든다:
  - `eclass/EclassHtmlParser.java:107-108`: 과제·퀴즈 링크이거나, 이름이 2자 미만 또는 200자 초과면 `name = null`. `:109-111`은 그래도 `EclassCourse(kj, null)`을 등록한다.
  - `HttpEclassGateway.java:130-131`은 다른 페이지에 이름이 없으면 null 과목을 그대로 유지한다.
  - `EclassHtmlParserTest:73,86`도 `new EclassCourse("QUIZ101", null)`을 정상 입력으로 쓴다.
- 이름 없는 과목이 저장 단계로 그대로 들어간다:
  - `schedule/EclassImportService.java:49` → `upsertCourse` → `:75` `Course.collected(..., name=null)`, `:76` `course.refresh(name, now)`.
  - `catalog/Course.java:19`는 `course_name nullable=false`, `V4__catalog_schedule_posts.sql:6`은 `course_name VARCHAR(300) NOT NULL`이다. `Course.refresh`(`:43-46`)는 null도 그대로 대입한다.
- 결과:
  - 새 과목이면 flush에서 제약 위반이 난다. 이미 있는 공유 과목이면 이름이 null로 덮여 역시 위반이 난다.
  - `EclassSyncService.java:43-45`가 이 예외를 `ECLASS_UNAVAILABLE`로 바꿔 매 주기 실패하고, 그 사용자의 과목·과제는 영영 들어오지 않는다(로그도 남지 않음, M7).
  - 과목 목록에 이름이 안 잡히는 과목(지난 학기 todo, 셀렉터 미일치)이 하나만 있어도 생긴다 [INFERENCE: 실제 빈도 미확인].
- 테스트가 놓친 이유: `EclassImportHttpTest`는 항상 이름 있는 과목만 넣는다.
- 제안:
  - `upsertCourse`는 이름이 null이면 새 과목을 만들지 않고, 기존 과목에 소속만 연결한다(태스크 경로 `:53-59`가 이미 이렇게 한다).
  - `Course.refresh`는 null 이름을 무시한다.
  - null 이름 과목을 신규·기존 두 경우로 넣는 import 테스트를 추가한다.

### H3. 인증 없는 로그인 엔드포인트가 학교 계정 비밀번호 대입의 무제한 중계기가 됨
- `global/security/SecurityConfig.java:28`: `POST /api/auth/login`은 `permitAll`이다. 요청마다 `AuthService.login` → `HttpEclassGateway.authenticate`가 학교에 2~3회 요청(GET 폼, POST, 필요 시 main)을 보낸다.
- 학번은 순차적이라 추측하기 쉽다. 그런데 rate limit·학번별 백오프·IP 제한이 코드 어디에도 없다(`rate|throttl|lockout` grep 결과 0건). 문서(ledger·ADR)에도 언급이 없다.
- 결과: 제3자가 이 서버를 통해 임의 학생의 학교 비밀번호를 대입하거나, 일부러 실패를 쌓아 학교 계정을 잠글 수 있다. 학교 측이 서버 IP를 차단하면 모든 사용자가 로그인할 수 없게 된다. 탈퇴(`DELETE /api/me`)와 수동 동기화(`POST /api/eclass/sync`, 1회 최대 `MAX_REQUESTS=180`)도 같은 증폭 경로다.
- 제안:
  - 학번 단위와 IP 단위로 로그인 실패 백오프를 둔다(예: 학번당 5회 실패 시 15분).
  - 수동 동기화는 회원별 single-flight와 쿨다운을 둔다.
  - ledger (B)에 기록한다.

## Medium

### M1. SUSPENDED(레거시) 계정의 ICS 피드가 계속 열림. 본인은 폐기할 수단도 없음
- `schedule/CalendarFeedRepository.java:14-15` `select f.member.id from CalendarFeed f where f.token = :token`과 `CalendarFeedService.render`(`:65-67`)는 `accountStatus`를 보지 않는다.
- `V3__eclass_identity.sql:15-18`은 "인증 비활성화"를 주장하지만 `calendar_feed`는 그대로 둔다. `SchemaUpgradeTest`도 피드 201을 보존만 하고, 차단 여부는 확인하지 않는다.
- 레거시 사용자는 로그인할 수 없으므로 README:98의 "재발급·탈퇴로 폐기"를 쓸 수 없다. 토큰을 아는 사람은 기한 없이 일정을 읽는다.
- 제안: 질의에 `and f.member.accountStatus = 'ACTIVE'`를 추가하거나, 새 마이그레이션으로 비활성 회원의 피드를 삭제한다. SUSPENDED 피드 → 404 테스트를 추가한다.

### M2. 학교 원문 길이를 자르지 않아 긴 항목 하나가 수집 전체를 롤백시킴
- `Event.java:47` title은 200자, `course_name`은 300자, `external_uid`는 200자, `source_url`은 2000자다. 그런데 파서는 `.todo_title`(`EclassHtmlParser.java:131`), 목록 링크 텍스트(`:149`), 강의계획서 시험 셀 전체 텍스트(`:156-158`)를 자르지 않는다. 계획서 주차 셀은 200자를 쉽게 넘는다 [INFERENCE].
- `validKj`(`:270`)는 63자까지 허용하지만 `courses.legacy_kjkey`는 `VARCHAR(40)`이다(`V4:7`, `Course.java:21`).
- 결과는 H2와 같다. "value too long" → 롤백 → `ECLASS_UNAVAILABLE` 반복.
- `EventLifecycleHttpTest`의 "e-class 일정·DB는 200자" 검증은 150자 저장소 직접 삽입이라, 이 경로를 다루지 않는다.
- 제안: import 경계에서 코드포인트 단위로 자르고, `validKj` 상한을 40으로 맞추거나 컬럼을 넓힌다. 201자 원문 제목 테스트를 추가한다.

### M3. 같은 과제가 todo와 과제 목록에서 서로 다른 id로 두 번 수집됨. 제목 식별·잔존 항목은 사용자가 지울 수도 없음
- todo 항목 id는 `kj:kind:item`(`EclassHtmlParser.java:137`, 예 `DESIGN101:report:TASK42`), 제목은 `[과제] …`이다.
- 목록 행 id는 `stable(kj, type, href 또는 제목)`(`:175-177`), 제목은 접두사가 없다.
- `collect`/`put`은 externalId 기준 `putIfAbsent`라서, 두 곳에 모두 보이는 과제는 이벤트 2개(목록·ICS 중복)가 된다. 한쪽을 완료 처리해도 다른 쪽은 남는다 [INFERENCE: 실제 e-class가 두 곳 모두에 노출한다는 가정. 픽스처는 서로 다른 과제라 겹침을 검증하지 않는다].
- 추가 문제:
  - 과목 링크만 있는 행은 제목이 식별자다(`:175-176`). 교수가 제목을 바꾸면 새 이벤트가 생긴다.
  - `EclassImportService.java:40`에 따라 사라진 항목은 영구히 남는다.
  - `Event.ensureDeletable`이 ECLASS 일정 삭제를 409로 막으므로 사용자가 지울 수단이 없다.
- 제안: 실제 항목 id(쿼리 파라미터)를 우선하고, todo와 목록이 같은 id를 만들도록 맞춘다. 완전 수집 N회 동안 안 보이면 숨기는 tombstone(`last_seen_at`)을 두거나, 수집 일정에 "숨기기"를 허용한다. 겹침 픽스처 테스트를 추가한다.

### M4. 연도 없는 날짜를 항상 현재 KST 연도로 해석해 연말·연초 마감이 과거로 감
- `EclassHtmlParser.java:205`: `KOREAN_DATE`에 연도가 없으면 `LocalDate.now(clock.withZone(KST)).getYear()`를 쓴다.
- 12월 28일에 "1월 5일" 마감을 읽으면 올해 1월 5일, 즉 과거 날짜가 된다. todo 표기 `D-2 9월 22일`처럼 날짜와 D-day가 함께 있으면 날짜가 우선이라 D-day로 보정되지도 않는다.
- `EclassHtmlParserTest`의 KST 테스트는 같은 해만 다룬다.
- 제안: D-n이 함께 있으면 교차 검증한다. 해석 결과가 약 6개월 넘게 과거면 다음 해로 넘긴다. 시계를 12/28로 둔 테스트를 추가한다.

### M5. 비정규 과목(`비…` KJKEY)이 정규식에서 거부되어 `비정규` 분기가 도달 불가
- `EclassHtmlParser.java:270` `validKj`는 `[A-Za-z0-9._-]`만 받는다.
- 그런데 `Course.collected`(`Course.java:38`)는 `externalId.startsWith("비")`로 `비정규`를 판정하고, ERD의 `legacy_kjkey` 예시도 `비2026B64101`이다.
- 결과: 비정규 과목은 수집되지 않고, 그 과제는 과목 연결을 잃는다.
- 제안: 한글을 허용(`[\p{IsHangul}A-Za-z0-9._-]{1,40}`)하고 `비…` 키 테스트를 추가한다.

### M6. 수집 파이프라인(`EclassSyncService`)이 자동 테스트에서 한 번도 실행되지 않음
- `application-test.yml:13`이 `sync-enabled: false`다.
- `POST /api/eclass/sync`, `sync()`, `scheduledSync()`를 부르는 테스트가 없다. 가짜 게이트웨이 `fetch`(`support/EclassTestConfiguration.java:21`)는 빈 스냅샷만 돌려주는데, 그마저 호출되지 않는다.
- 그래서 다음이 회귀 보호 없이 스모크(47개 HTTP 확인)에만 의존한다:
  - 저장 비밀번호 재인증 분기
  - `REAUTH_REQUIRED`/`FAILED` 상태 기록
  - 쿠키 재암호화
  - 실제 파서 출력 → importer 경로
- H1·H2는 이 공백 때문에 테스트를 통과했다.
- 가짜 게이트웨이 자체도 실제와 다르다:
  - 이름을 항상 `"테스트"`로 돌려주지만(`:19`) 실제 게이트웨이는 항상 null이다(`HttpEclassGateway.java:104`).
  - 입력 학번을 그대로 되돌려서, `AuthService.java:31`의 학번 불일치 방어 분기를 시험하지 못한다.
- 제안:
  - 프로그래밍 가능한 가짜로 HTTP 테스트를 만든다(재인증 → 저장 비밀번호 → 성공, 비밀번호 없음 → `REAUTH_REQUIRED`, 스냅샷이 실제로 반영됨).
  - `campus.eclass.base-url`을 loopback 픽스처 서버로 지정한 Spring 통합 테스트를 1개 둔다.

### M7. 수집 실패를 기록하지 않고, 복호화 실패를 "일시 장애"로 분류해 영구 재시도함
- main 코드의 로그는 `GlobalExceptionHandler.java:120,127`과 `RequestIdFilter.java:40`뿐이다.
- `EclassSyncService.java:43-45`는 모든 `RuntimeException`(파서 버그, 제약 위반, 복호화 실패)을 로그 없이 `ECLASS_UNAVAILABLE`로 바꾼다. 그래서 H2·M2 같은 결정적 실패를 운영에서 구분할 수 없다.
- `EclassSecrets.decrypt` 실패(`:46-47`, 키 교체 등)는 사용자에게 "일시 장애"로 보이고 5분마다 재시도된다. 반면 erd-handoff는 "키 변경 시 재로그인"이라고 적는다.
- 제안:
  - 예외 클래스와 memberId만 담은 `warn` 로그를 남긴다(비밀값 제외).
  - 복호화 실패는 `ECLASS_REAUTH_REQUIRED`로 바꾸고 저장 비밀값을 지운다.

### M8. 로그아웃해도 학교 연결이 살아 있어 배경 수집이 계속됨 (동의 범위 문제)
- `AuthService.logout`(`:49-52`)은 `token_version`만 올린다. 그래서 `eclass_connections`의 암호화 쿠키와 opt-in 비밀번호가 남고, `scheduledSync`가 5분마다 그 사용자의 학교 계정에 접속한다.
- 비밀번호를 저장한 사용자는 로그아웃 후에도 무기한 수집된다. 오래 쓰지 않은 사용자도 수집 대상에서 빠지지 않는다.
- README:64("모든 기기의 기존 access·refresh 무효")에도, ADR에도 이 동작이 적혀 있지 않다.
- 제안: 로그아웃 시 연결을 해제하거나 수집을 멈춘다(최소한 쿠키·비밀번호 삭제). 아니면 정책으로 명시한다. 마지막 앱 사용 후 N일이 지나면 수집을 중단한다.

### M9. 탈퇴가 학교 실시간 인증에 묶여 삭제권을 행사하지 못하는 경우가 생김
- `auth/WithdrawalService.java:19-31`은 학교 로그인이 성공해야만 삭제한다. 학교 장애(`ECLASS_UNAVAILABLE`), 졸업·휴학으로 e-class 계정 소멸, 학교 비밀번호 분실 상태에서는 유효한 JWT가 있어도 탈퇴할 수 없다.
- 개인정보 정책이 아직 미결이라는 점은 알려져 있다. 다만 "삭제 불가" 경로가 생긴다는 점은 문서에 없다.
- 제안: 학교 인증 실패가 `INVALID_CREDENTIALS`가 아닌 경우(장애·계정 없음)를 위해, 최근 발급 JWT와 추가 확인 문구로 탈퇴하는 대체 경로를 둔다. 또는 정책 결정 대기 항목으로 ledger에 명시한다.

### M10. 데이터가 있는 상태의 업그레이드(V3/V4)는 H2에서만 검증됨. CI가 녹색이어도 PostgreSQL 이관은 증명되지 않음
- `SchemaUpgradeTest`는 H2에서 V2까지 적용한 뒤 레거시 행을 넣고 V4까지 올린다(SUSPENDED, token_version 5→6, id 보존 확인). 하지만 업그레이드 뒤 Hibernate validate나 앱 동작은 확인하지 않는다.
- `PostgresSchemaTest`는 빈 DB에만 마이그레이션한다. 그래서 PostgreSQL에서 V3의 RENAME/UPDATE/`SET NOT NULL`과 V4의 boards backfill(`GROUP BY` 삽입 후 `board_id SET NOT NULL`)은 0행에 대해서만 실행된다.
- 제안: `SchemaUpgradeTest`의 Testcontainers 버전을 만들고, 업그레이드된 DB로 JPA 컨텍스트를 띄워 validate한다.

## Low

- **L1. 공유 과목 이름을 아무 회원의 스냅샷이나 덮어씀. 수강 소속은 해제되지 않음.**
  - `EclassImportService.java:76`에서 회원마다 파싱 결과로 공유 `courses.course_name`을 바꾼다. `Event.getCourseName()`(`Event.java:207`)은 공유 이름을 우선하므로, 회원 A의 일정 과목명이 회원 B의 파싱 결과로 흔들린다.
  - `course_enrollments`는 해제(`CANCELLED`) 경로가 없어 `/api/courses`에 수강 취소한 과목이 계속 남는다.
- **L2. 요청 예산과 과목 상한이 맞지 않음.**
  - `MAX_COURSES=30`, 과목당 4페이지에 계획서 링크 최대 6개, `MAX_REQUESTS=180`(`HttpEclassGateway.java:39-40`)이다.
  - 과목이 30개면 최소 1+10+30×4=161에 todo 탐색(최대 20×3)과 리다이렉트 홉까지 더해져 상한을 넘는다. 그러면 부분 결과 없이 전체가 `ECLASS_UNAVAILABLE`이 된다.
  - 전체 동기화 기한도 없다. 최악의 경우 180 × 10초로 Tomcat 스레드(수동 동기화)나 스케줄러 스레드를 약 30분 점유한다. 스케줄러는 단일 스레드로 전 회원을 순차 처리하므로 "5분 간격"은 회원 수가 늘면 지켜지지 않는다.
- **L3. 학번 정규화·형식 검증이 없음.**
  - `LoginRequest`는 1~64자 임의 문자열이고 `AuthService.java:28`은 `strip()`만 한다. e-class가 대소문자나 앞자리 0을 관대하게 받으면 같은 사람이 계정 둘을 갖는다 [INFERENCE].
  - `AuthService.java:31`의 학번 일치 검사는 실제 게이트웨이가 입력을 그대로 되돌리므로(`HttpEclassGateway.java:104`) 항상 참이다. 주석이 말하는 신원 검증 효과가 없다.
  - 제안: 학교별 학번 패턴(예: 숫자 8~10자리)을 검증하고, 로그인 후 페이지에서 실제 로그인 ID를 읽을 수 있으면 그 값을 쓴다. `legacy:` 접두사와 섞이지 않도록 형식을 제한한다.
- **L4. 백그라운드 재인증 결과를 검증하지 않음.**
  - `EclassSyncService.java:36`은 로그인 경로(`AuthService.java:31-33`)와 달리 `profile`이나 쿠키가 비었는지 확인하지 않는다.
  - 빈 쿠키면 다음 `fetch`가 `REAUTH_REQUIRED`가 되어 H1 루프에 합류한다.
- **L5. 강의계획서 행 처리에서 `orElseThrow()`(`EclassHtmlParser.java:158`)가 터질 수 있음.**
  - 시험 키워드가 셀 경계에 걸쳐 행 전체에서만 일치하면 `NoSuchElementException`이 나서 전체 수집이 실패한다.
  - "24:00" 표기는 `LocalTime.of`가 예외를 내서 해당 항목이 날짜 미정이 된다.
- **L6. 날짜만 있는 마감이 같은 날의 시각 있는 마감보다 먼저 정렬됨.** 날짜만 있는 마감은 00:00 KST로 저장되고, `findInRange`는 `dueAt, id` 순으로 정렬하기 때문이다. D-day, 범위 조회, ICS `VALUE=DATE`는 맞다. ICS의 날짜 전용 일정에는 DTEND가 없는데, RFC 5545상 유효하다.
- **L7. `course_enrollments.user_id` 인덱스가 없음.**
  - UNIQUE가 `(course_id, user_id)` 순서라 `user_id` 단독 조회에는 쓰이지 않는다. PostgreSQL은 FK에 인덱스를 자동으로 만들지 않는다.
  - 이 인덱스가 필요한 곳: `findCoursesByMemberId`, `deleteAllByUserId`(탈퇴), CASCADE.
- **L8. 공지 목록에 N+1이 있고, 게시판 종류를 거르지 않음.**
  - `NoticeResponse`가 LAZY `@ElementCollection` 대상 학과를 항목마다 읽는다. size 50이면 쿼리가 51회 이상 나간다.
  - 목록 응답이 본문 전체를 포함한다.
  - `findAllBySchoolId`는 board나 post_kind로 거르지 않아, 과목 게시판 글이 `posts`에 들어오면 학교 전체에 노출된다.
- **L9. `posts.author_id`에 ON DELETE 규칙이 없음.** 지금은 항상 null이지만, 작성자를 채우기 시작하면 탈퇴의 `members.delete`가 FK 위반으로 실패한다.
- **L10. 비밀값 처리 보강.**
  - `LoginRequest`, `WithdrawRequest`, `EclassSession`의 record 기본 `toString`이 비밀번호·쿠키를 포함한다. 지금 이를 로그에 쓰는 곳은 없다.
  - AES-GCM에 AAD(`user_id`+컬럼)와 키 id가 없어, 행·컬럼 간 암호문 교환을 탐지하지 못하고 키 교체도 할 수 없다.
  - `GlobalExceptionHandler.java:120`은 제약 위반의 원문 메시지를 로그에 남긴다. PostgreSQL에서는 `Key (school_id, external_ref)=(1, 학번)` 같은 Detail이 포함될 수 있다 [INFERENCE]. 제약 이름이나 SQLState만 남기는 것을 제안한다.
- **L11. 기본 프로필이 `local`임** (`application.yml:5`). `SPRING_PROFILES_ACTIVE` 없이 `java -jar`로 실행하면 저장소에 공개된 JWT·e-class 키와 H2로 기동한다. 이 경우 누구나 임의 회원의 토큰을 위조할 수 있다. Docker만 `prod`를 설정한다. 기존 설계를 이어받은 것이지만 이제는 학교 자격까지 다룬다.
- **L12. JWT가 필요한 엔드포인트에서 학교 세션 오류도 401을 씀.** `ECLASS_REAUTH_REQUIRED`/`INVALID_CREDENTIALS`가 `/api/eclass/sync`에서 401로 나온다. 흔한 "401이면 JWT refresh 후 재시도" 인터셉터는 무한 루프에 빠진다. 프론트 계약에 `code`로 구분하라고 명시하거나 상태 코드를 바꾼다(409/424 등).
- **L13. 락 순서 주석과 실제 코드가 다름.** `EclassAccountService.java:34`는 "school before user"라고 하지만 `WithdrawalStore.delete`(`:29`)와 `disconnect`(`:86`)는 학교 락 없이 사용자만 잠근다. 현재 경로에서 교착은 찾지 못했다. 동시 로그인 flush와 탈퇴가 겹치면 0행 UPDATE로 500이나 409가 날 수 있다 [INFERENCE].
- **L14. 테스트 약점.**
  - `AuthFlowHttpTest`의 쿠키 비노출 단언(`assertFalse(...contains(studentId))`)은 사실상 실패할 수 없다. 복호화한 값과 `v1.` 접두사를 단언하는 편이 낫다.
  - 다음 테스트가 없다: `rememberCredentials=false` 재로그인 시 저장 비밀번호 삭제, 32바이트 미만 키 거부, nonce·envelope 변조.
  - `build.gradle.kts`는 `SPRING_*`만 지우므로, `CAMPUS_ECLASS_*` 환경변수가 있으면 테스트 동작이 바뀐다.
  - 탈퇴 후 옛 JWT가 거부되는 것은 IDENTITY id가 재사용되지 않는다는 사실에만 기대고 있다. DB 복원이나 시퀀스 리셋에 대비해 `iat < users.created_at` 토큰을 거부하는 방어를 추가하는 것을 고려한다.
- **L15. 문서 불일치.**
  - `tech-debt-ledger.md:11-12`의 (A) "상환" 행이 이제 없는 테스트를 인용한다(`AuthFlowHttpTest.validationErrorsListEveryField`, `duplicateEmailReturns409WithCode`, `missingTokenReturnsProblemJsonWithRequestId`). 현재 이름은 `missingOrMalformedTokensHaveConsistentProblems` 등이다.
  - ADR-0008:41의 prod fail-fast 목록에 `ECLASS_ENCRYPTION_KEY`가 빠졌다.
  - 비활성 계정 로그인은 403 `FORBIDDEN`(`EclassAccountService.java:41`)인데 README의 로그인 항목에 403이 없다.
  - `catalog/CourseController.java:18-19`는 컨트롤러에서 `@Transactional`을 걸고 저장소를 직접 호출한다. 이는 ADR-0001("컨트롤러는 서비스만 호출")·ADR-0004("트랜잭션은 서비스")와 어긋난다.
  - erd-handoff에 `post_target_departments`(ERD에 없는 앱 확장)와 posts의 ERD 차이(`published_at` NOT NULL, `post_kind` 기본값 `NOTICE`, `deleted_at`·`audience` 등 미포함)를 표시해야 한다.

## 주장대로 확인된 것

- **네트워크와 트랜잭션 분리:** `AuthService.login`, `EclassSyncService.sync`, `WithdrawalService.withdraw`의 학교 HTTP는 트랜잭션 밖에서 실행되고, 저장·삭제는 별도의 짧은 `@Transactional`(`EclassAccountService`, `WithdrawalStore`)이다. import는 `Propagation.MANDATORY`로 `synchronizedSnapshot` 안에서만 실행된다.
- **탈퇴:** 사용자 락 → 일정 → 피드 → 소속 → 연결 → 사용자 순서로 한 트랜잭션에서 삭제하고, 공유 학교·과목·공지는 보존한다. `WithdrawalRollbackTest`는 사용자 삭제 시점에 실패를 주입하고, 트랜잭션 안에서 부재를 확인한 뒤 롤백으로 복원되는지를 인과적으로 검증한다.
- **재진입:** 같은 학번으로 재로그인하면 새 IDENTITY id가 생기고, 옛 access/refresh/ICS는 거부된다(`findAuthSnapshot`이 없는 id나 비활성 계정을 거름).
- **레거시 격리(JWT 경로):** V3가 `external_ref='legacy:'||id`, `SUSPENDED`, `token_version+1`을 적용하고, V1/V2는 수정되지 않았다. `findAuthSnapshot`과 `increaseTokenVersion`이 ACTIVE만 대상으로 한다. ICS만 예외다(M1).
- **비밀값:** 비밀번호는 기본으로 저장하지 않는다(재로그인 시 opt-out이면 null로 덮음). opt-in일 때와 쿠키는 별도 키의 AES-256-GCM(12바이트 무작위 nonce, 128비트 태그)으로 저장한다. 키가 32바이트 미만이면 기동이 실패한다. 응답 DTO에는 비밀값이 없다. `EclassSyncContext.toString`은 가린다.
- **게이트웨이 경계:** HTTPS만 허용하고(loopback만 HTTP 예외), 같은 출처·포트와 `/ilos|/my|/course` 경로 제한, 퍼센트 인코딩·정규화 우회 차단, 리다이렉트 5회 상한, 응답 본문 2MiB 상한을 둔다. 요청마다 독립 쿠키 저장소를 쓴다. POST가 200이어도 main 페이지의 인증 표식으로 다시 확인한다(loopback 테스트로 검증).
- **수집 보존:** 재수집해도 이벤트 id와 `done`이 보존되고(`refreshImported`는 `done`을 건드리지 않음), 개인 일정은 영향받지 않는다. 소유권은 `(member_id, source, external_uid)` UNIQUE와 `findByIdAndMemberId`(남의 것은 404)로 지킨다.
- **날짜:** 범위 조회는 [from 00:00 KST, to+1 00:00 KST)이고 최대 62일이다. 미정 일정은 `/api/events/undated`에만 나오고 ICS에서는 빠진다. 날짜만 있는 일정은 KST 날짜의 `DTSTART;VALUE=DATE`로 나간다. D-day는 KST 기준이고, 날짜가 없으면 null이다.
- **엔티티와 DDL:** V1+V3+V4와 엔티티 매핑(컬럼명·길이·NULL 허용, `QUIZ` 포함 CHECK)이 일치하고, H2와 PostgreSQL 공통 문법이다. 실제 PostgreSQL 실행은 미검증이다.
- **문서·카운트:** README의 23개 엔드포인트가 컨트롤러·`SecurityConfig`와 일치한다. ErrorCode는 20개, 테스트 메서드는 110개이고 skipped 2개는 `PostgresSchemaTest`다. compose와 CI docker 잡이 `JWT_SECRET`·`ECLASS_ENCRYPTION_KEY`를 필수로 전달한다.
- **PATCH /api/me:** 생략은 유지, null은 비우기다. 로그인 시 프로필 갱신은 null로 사용자 수정값을 덮지 않는다(`Member.refreshProfile`).

## 판정

REQUEST_CHANGES

- 필수 수정: H1(저장 비밀번호 재시도 루프), H2(이름 없는 과목 NOT NULL), H3(로그인·동기화 rate limit).
- 같은 변경에서 함께 처리할 것을 권장: M1(SUSPENDED ICS), M2(길이 절단), M6(수집 파이프라인 테스트).
