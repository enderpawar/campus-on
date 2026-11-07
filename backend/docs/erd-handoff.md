# ERD 반영·인계 계약

## 1. 기준과 범위

- 기준: 사용자 제공 `STack DB.sql`과 [ERD](https://www.erdcloud.com/d/B5bWYfGmFisJTYLka).
- 원본은 MySQL LMS 전체 모델이다. 합의는 **기존 F1·F3·F6 유지 + 관련 ERD 구조를 PostgreSQL에 확장**하는 방식이다. MySQL DDL 전체 실행이나 모든 LMS 기능 구현이 아니다.
- 추가 사용자 지시로 이메일 가입을 제거하고, [Planary](https://github.com/yoobinkim541/plannary_smart_planner)의 e-class 폼·쿠키 인증/페이지 수집 방식을 사용한다. 인증 성공 시 앱 사용자를 자동 등록한다.
- 과목·수강 소속과 일정 수집은 이 흐름에 필요한 만큼 포함한다. 학사 행정, 자료실/게시판 쓰기, 메시지, AI, 공지 크롤러, 다른 학교 어댑터는 이 구현에 포함하지 않는다.

## 2. 실제 대응 구조

| ERD/기존 구조 | 현재 테이블·코드 | 적용 내용과 차이 |
|---|---|---|
| `users` | `member.Member` → `users` | `user_id`, `external_ref`(학번), `user_name`, `department_name`, `grade`, 역할·상태·생성/동기화 시각. 기존 id 보존 |
| 사용자 학교·토큰 버전 | `users.school_id`, `token_version`, `school` | 앱 확장. `(school_id, external_ref)` UNIQUE. 역할은 이 앱의 학생 대상 `STUDENT`이며 학교 권한 추출 결과가 아님 |
| `courses` | `catalog.Course` → `courses` | 과목명·유형·상태·`legacy_kjkey`, 학교별 외부 키 UNIQUE. 개설 학기·학점 등 미수집 필드는 구현하지 않음 |
| `course_enrollments` | `CourseEnrollment` | 사용자-과목 소속 UNIQUE. 내 과목 API는 이 소속으로 제한 |
| `boards` / `posts` | `notice.Board` / `Notice` | 기존 공지 id를 유지하며 `notice`를 `posts`로 rename. 게시판별 학교, 게시 시각·원문 URL·요약·본문 |
| 공지 대상 학과 | `post_target_departments` | 앱 확장. 순서 있는 중복 제거 목록, 빈 목록=학과 제한 없음 |
| 학내 연결 비밀 | `eclass_connections` | 앱 확장. 암호화 쿠키·선택 저장 비밀번호, 동기화 상태·오류 코드·시각. 원문 비밀은 API에 노출하지 않음 |
| 통합 일정 | 기존 `event` | 앱 확장 유지. `course_id`, 표시 과목명, `duration_seconds`, `source_url`, `date_only`, nullable `due_at`, QUIZ 추가 |
| ICS 자격 | 기존 `calendar_feed` | 앱 확장 유지. 회원별 한 URL, 재발급 회전, no-store, 로그/오류 경로 토큰 마스킹 |

### 원본 DDL과 의도적으로 다른 제약

- MySQL `UNSIGNED`/`TINYINT(1)`/`DATETIME`을 그대로 쓰지 않고 PostgreSQL/H2의 정수·boolean·timezone timestamp를 사용한다. JPA/Flyway가 관리한다.
- 원본 `users.user_name NOT NULL`은 현재 어댑터가 검증 가능한 실명을 제공하지 않아 nullable이다. 학번이나 임의 문자열로 이름을 채우지 않는다. 학과·학년도 모르면 null이며 프로필 PATCH로 보완한다.
- 수집 공지에 대응하는 로컬 작성자가 없으므로 `posts.author_id`는 nullable이다. 특정 사용자에게 임의 귀속하지 않는다.
- 안정적인 KJ 키가 없는 원문에서 과목을 임의 생성하지 않는다. 일정에 과목 표시명만 있을 수 있다.
- 시각 없는 날짜는 `dateOnly=true`와 해당 KST 날짜의 00:00 기준값으로 보존하고 ICS에서는 종일로 표현한다. 날짜 미정은 null로 보존한다. 원본에 없는 09:00/23:59 같은 마감 시각을 생성하지 않는다.

## 3. Flyway 이관과 기존 데이터

1. **V1·V2는 그대로 유지한다.** 변경은 `V3__eclass_identity.sql`, `V4__catalog_schedule_posts.sql`, `V5__eclass_connection_generation.sql`, `V6__distributed_coordination.sql`로 쌓는다. 적용 후 수정은 새 버전으로만 한다.
2. V3는 `member`를 `users`로 rename한다. 기존 이메일을 학번으로 추측하지 않는다. 기존 계정은 `external_ref='legacy:<id>'`, `SUSPENDED`, `token_version+1`로 격리하고 이메일·로컬 비밀번호 해시 컬럼은 제거한다. 해당 JWT는 더 이상 인증되지 않는다.
3. 기존 사용자·일정·피드·공지 id와 연결 관계는 보존한다. 기존 이메일 계정 데이터를 새 학교 계정에 자동 합치지 않는다. 별도 이관이 필요하면 소유권 확인과 정책 승인이 먼저다. 기존 ICS 비밀값은 이관 자체로 회전하지 않는다.
4. V4는 과목·소속을 추가하고, `notice`→`posts` rename 후 학교/기존 source별 게시판을 만들고 연결한다. 원문 URL·요약·게시 시각을 보존한다.
5. V5는 `eclass_connections.connection_generation VARCHAR(36) NOT NULL`을 추가한다. 기존 행은 `'legacy-' || user_id`로 채우고, 이후 로그인마다 새 UUID로 바꾼다.
6. `SchemaUpgradeTest`(H2)와 `PostgresSchemaUpgradeTest`(PostgreSQL 17)가 같은 시나리오(`SchemaUpgradeScenarios`)를 실행한다. V2 데이터 생성 → 최신 버전 이관 후 id·토큰 버전·상태·공지 내용, V4 연결 → V5 backfill·NOT NULL → V6 lease/claim·throttle 테이블 쓰기까지 검증한다. PostgreSQL 쪽은 로컬에 Docker가 없어 skipped지만 CI(PR #1)에서 실행·통과했다.
7. local 전용 V1000 시드는 새 게시판/게시글 구조의 예시 4건이다. 운영 Flyway 위치에는 포함하지 않는다. local DB는 메모리 DB이므로 실행 시 재생성한다.

운영 적용 전에는 백업·복원 절차와 PostgreSQL CI 결과를 확인해야 한다. 이번 작업은 외부 DB에 접속하거나 운영 마이그레이션을 실행하지 않았다.

V6 추가 구조: `auth_throttle_mutex`(단일 행 짧은 잠금), `auth_throttle_failure`(버킷·실패 수·창 만료), `auth_throttle_reservation`(시도 UUID·버킷 복합 PK·lease 만료). `eclass_connections`에 `sync_lease_owner/until`, `reauth_claim_owner/until` 네 컬럼을 nullable로 추가한다. 기존 데이터는 null로 시작한다. 엔티티의 비밀값·세대와 별개인 조정 컬럼은 JDBC 조건부 UPDATE만 사용한다.

## 4. 수집·트랜잭션·삭제 불변식

- e-class HTTP 인증/수집은 DB 트랜잭션 밖에서 실행한다. 자동 등록과 스냅샷 반영은 별도 서비스의 짧은 트랜잭션이다. 공유 과목 생성은 학교 행 락으로 직렬화한다. 락 순서는 학교 → 사용자 → 연결(`eclass_connections`)이다. 로그인은 사용자 행을 처음 읽을 때부터 잠가(`MemberRepository.lockByExternalRefAndSchoolId`) 커밋된 최신 `token_version`으로 토큰을 발급한다.
- 연결 세대(`connection_generation`): 동기화는 시작할 때 읽은 세대를 context에 담는다. 성공·실패·복호화 실패 반영은 연결 행을 PESSIMISTIC_WRITE로 잠근 뒤(`EclassConnectionRepository.lockById`) 세대가 그대로일 때만 쓰고, 썼는지를 boolean으로 돌려준다. 로그아웃→재로그인 등으로 세대가 바뀌면 새 연결에는 아무것도 쓰지 않고, 성공·실패와 관계없이 "결과를 버림" info 한 줄만 남긴다(warn은 실제로 반영된 실패만). 저장 비밀번호 재인증 직전에는 세대와 sync lease 소유자를 조건부 UPDATE로 검사하며 claim을 선점한다. claim 기간(인증 전체 제한 + 5초, 기본 15초) 중 연결 교체·삭제는 짧은 트랜잭션 재시도로 기다린다. 기한을 넘으면 409이며 대기 중 커넥션은 보유하지 않는다. 이미 학교로 보낸 요청은 회수할 수 없다. V5는 기존 연결 행을 `legacy-<user_id>`로 채운다(`SchemaUpgradeTest`, H2).
- 수집 upsert 키는 `(member_id, source, external_uid)`이다. 같은 외부 키의 id와 로컬 완료 표시는 유지한다. 부분 수집에서 빠진 항목을 삭제하지 않고, 사용자가 수집 일정을 숨기거나 지울 수도 없다(부채 원장 (B)). 원문에 항목 키가 없으면 제목 기반 식별이므로 제목 변경을 동일 항목으로 인식한다고 보장하지 않는다. 중복 제거는 양쪽 원문 항목 id가 있으면 같은 id일 때만 합친다. 한쪽에 id가 없을 때는 같은 과목·종류·정규화 제목의 일대일 후보이며 같은 KST 마감일(또는 한쪽 미상)일 때만 합친다. 후보가 여럿이거나 날짜가 다르면 모든 행을 보존한다. 이번 수집에서 합친 상대 external_uid를 alias로 전달하여 같은 회원의 ECLASS 중복 행만 삭제한다. 정본 id와 양쪽 완료 상태의 OR를 보존하며 개인·다른 회원 일정은 건드리지 않는다.
- import 경계 길이 규칙(`schedule/ImportLimits`): 제목은 200, 과목명은 300 코드포인트로 자른다. `source_url`이 2000자를 넘으면 null, `external_uid`가 200자를 넘으면 `h:`+SHA-256 hex로 바꾼다. 컬럼을 줄이거나 늘리면 이 상수도 함께 바꾼다.
- 과목명이 null/공백이면 과목을 새로 만들지도, 기존 `course_name`을 덮어쓰지도 않는다(`Course.refresh`는 null 무시). 기존 과목이 있으면 일정을 그 과목에 연결하고, 기존 과목이 없고 이름도 없을 때만 일정을 과목 없이 수집한다.
- KJ 키(`courses.legacy_kjkey`, `VARCHAR(40)`)는 `[\p{IsHangul}A-Za-z0-9._-]{1,40}`만 받는다. 한글을 허용하므로 `비…`로 시작하는 비정규 과목도 수집된다.
- 쿠키는 AES-256-GCM으로 저장한다. 비밀번호 저장은 `rememberCredentials=true`일 때만 한다. prod는 전용 `ECLASS_ENCRYPTION_KEY`가 필수이며 JWT/cron 키로 대체하지 않는다. 키 교체 등으로 저장 비밀값을 복호화할 수 없으면 연결을 `REAUTH_REQUIRED`로 바꾸고 저장 비밀번호를 지운다(재로그인 필요).
- 연결 수명: 로그인에서 생성/갱신, 로그아웃은 같은 트랜잭션에서 `eclass_connections` 행(쿠키·저장 비밀번호)을 삭제하고 다음 로그인에서 다시 만든다. 연결 해제는 저장 쿠키·비밀번호만 지우고 사용자·일정은 유지한다. 미기억 연결은 학교 세션 만료 시 재로그인이 필요하다. 저장 비밀번호를 학교가 거절하면 비밀번호를 지우고 `REAUTH_REQUIRED`(`lastErrorCode=INVALID_CREDENTIALS`)로 두며 다시 시도하지 않는다.
- 자동 수집 대상(`EclassConnectionRepository.findDueMemberIds`): `ACTIVE` 회원만, `PENDING`/`READY`는 `sync-delay` 경과 후, `FAILED`는 `campus.eclass.failure-backoff`(기본 PT30M) 경과 후. `REAUTH_REQUIRED`는 고르지 않는다. 이미 진행 중인 회원은 건너뛴다.
- ICS 피드는 `ACTIVE` 회원의 것만 응답한다. V3에서 `SUSPENDED`로 격리한 옛 계정의 피드는 행은 남지만 404다.
- 탈퇴: 외부에서 현재 학교 비밀번호 재확인(통신 장애만 최근 인증 대체, `auth_time` 기본 10분; 없거나 오래되면 503, 틀린 비밀번호 401) → `WithdrawalStore.delete`의 한 트랜잭션에서 일정·피드·수강 소속·연결 비밀·사용자 삭제. 공유 학교·과목·공지는 유지한다. 새 사용자 소유 테이블을 추가하면 이 삭제 경계와 롤백 테스트도 갱신한다.
- 학교 계정 자체는 삭제하지 않는다. 탈퇴 후 정상 학교 로그인은 새 앱 사용자 생성이며 기존 앱 데이터/토큰의 부활이 아니다.

## 5. 화면 요구 데이터 처리 결과

| 항목 | 현재 API 계약 | 검증·남은 경계 |
|---|---|---|
| E05 과목명·강의 길이 | 일정의 `courseId`, `courseName`, `durationSeconds` | `EclassImportHttpTest`, `EclassHtmlParserTest`가 실제 값·null·소유권·재수집을 검증. 길이는 원문에 명시된 경우만 제공하며 모든 강의 페이지에서 확보한다고 보장하지 않음 |
| N06 공지 본문 | 상세 `body`: plain text, 원문의 문단/빈 줄/목록 표시 보존, 부재 null | `NoticeHttpTest.ownSchoolNoticeDetailReturnsAllFieldsInKst`. 크롤러 구현/본문 공급은 여전히 별도 |
| N09 대상 학과 | `targetDepartments`: 순서 있는 학과명 목록, `[]`=제한 없음 | 같은 상세 테스트로 복수 학과와 문단을 함께 검증. 태깅 생성/추천은 별도 |
| E03/E09 표시일·오늘 할 일 집계 | 현재는 마감일 기준 기간 목록 | 제품 결정 대기. 단일 표시일·가용 구간·마감일 목록 중 선택 전 `startsAt`을 표시일로 전용하지 않음 |
| E16 임박 푸시 | 구현 없음 | 기존 범위 미합의/사용자 결정 유지. ICS나 D-day가 푸시를 대체하지 않음 |

E05는 API 저장/조회 계약을 반영했지만 실학교 원문에서 강의 길이 확보 범위는 검증하지 않았다. N06·N09는 조회 API 계약에 한해 구현됐으며 수집·자동 태깅이 구현됐다는 뜻은 아니다. R01 개인정보 방침, R02 최소 수집의 정책적 근거, A08 학기 표시도 [부채 원장](tech-debt-ledger.md)에서 별도로 추적한다.

## 6. 검증 증거와 한계

- 2026-11-08 Git Bash의 `backend/`에서 `GRADLE_USER_HOME=C:/Users/jinwoo/.gradle sh gradlew cleanTest test --console=plain`: 163건 중 159건 통과, 실패 0, 오류 0, 4건 skipped. XML 합계 tests=163/failures=0/errors=0/skipped=4. PG17 빈 DB 2건·데이터 업그레이드 2건은 로컬 Docker 없음으로 skipped이고, GitHub Actions(PR #1)에서는 실행·통과했습니다(skipped 0). 이전 2차 교차 검증 후는 142/140/0/2였습니다.
- 실제 JAR + 로컬 학교 HTTP 서버 + H2: 로그인/수집/일정/과목/공지/ICS/세션 만료/탈퇴 등 47개 HTTP 확인 통과. 이 스모크는 e-class/ERD 전환 시점 실행이며 Claude 리뷰·Codex 검증 수정 후에는 다시 돌리지 않았다. 실제 HTTP 수집에서 발견한 과목 링크 공유 시 과제 누락을 수정하고 회귀를 유지했다.
- `HttpEclassGatewayTest`는 실제 Java HTTP 클라이언트와 로컬 서버로 폼·CSRF·쿠키·리다이렉트·인증 표식·실패를 검증한다. 일반 기능 테스트의 `EclassTestConfiguration`은 외부 학교 경계만 대체한다.
- 실제 학교 계정 라이브 인증은 미검증이다. PostgreSQL/Testcontainers와 Docker compose는 CI에서만 확인했다(로컬 Docker 없음). 실제 로그인 UI/폼 변경, 학교의 자동화 허용 정책, 실명/학과/학년·강의 길이의 신뢰할 수 있는 추출 경로는 배포 전 확인해야 한다.
- 강의계획서는 명시적 시험 날짜가 있는 HTML만 처리한다. PDF 추출·주차 기반 날짜 추정은 하지 않는다.
