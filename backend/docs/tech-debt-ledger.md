# 기술 부채 원장

velog "Spring Study" 시리즈에서 쓰던 형식(바로 고칠 것 / 나중에 고칠 것 / 고치지 않을 것)을 그대로 씁니다. (A)는 스터디에서 남긴 부채를 이 프로젝트에서 갚은 내역이고, (B)는 이번 프로젝트에서 새로 남은 부채입니다. 로컬에서 실행해 보지 못한 항목에는 `미검증`을 붙였습니다.

경로는 `backend/` 기준입니다. `main/`은 `src/main/java/com/campuson/api/`, `test/`는 `src/test/java/com/campuson/api/`를 줄인 것입니다.

## (A) 스터디 부채 상환 내역

| 출처 | 스터디 상태 | 상환한 코드 | 확인하는 테스트 | 상태 |
|---|---|---|---|---|
| [Day 3][d3]·[Day 7][d7] | 오류 본문 3종(field Map, `{"errors":[]}`, `{"error":""}`). `code`·`requestId` 없음, 같은 필드의 위반이 서로 덮어씀 | `main/global/error/ErrorCode.java`(현재 22개), `BusinessException.java`, `ApiProblems.java`, `GlobalExceptionHandler.java` ([ADR-0006](adr/0006-problem-detail-errors.md)) | `EventControllerWebMvcTest.invalidCreateRequestReturns400WithAllFieldErrorsAndNeverCallsService`, `GlobalExceptionHandlerWebMvcTest.businessExceptionKeepsItsStatusAndCode`, `AuthFlowHttpTest.unsupportedContentTypeAndBlankInputAreClientErrors`. 처음 인용한 `AuthFlowHttpTest.validationErrorsListEveryField`·`duplicateEmailReturns409WithCode`는 이메일 가입 제거와 함께 삭제됨(`DUPLICATE_EMAIL` 코드도 삭제) | 상환 |
| [Day 27~28][d27] | 401/403 JSON을 문자열 이어 붙이기로 생성(이스케이프 위험), 400/404/409와 형식 불일치 | `main/global/security/ProblemAuthenticationEntryPoint.java`, `ProblemAccessDeniedHandler.java` (`ObjectMapper` + `ApiProblems`) | `AuthFlowHttpTest.missingOrMalformedTokensHaveConsistentProblems`(옛 이름 `missingTokenReturnsProblemJsonWithRequestId`) | 상환 (`ProblemAccessDeniedHandler` 경로는 전용 테스트 없음) |
| [Day 26][d26]·[회고][retro] | `POST` slice 테스트 없음, "`@WebMvcTest`가 무엇을 안 띄우는지" 답하지 못함 | `test/schedule/EventControllerWebMvcTest.java` 클래스 주석, [ADR-0007](adr/0007-test-strategy.md) | `EventControllerWebMvcTest.invalidCreateRequestReturns400WithAllFieldErrorsAndNeverCallsService` | 상환 |
| [Day 34][d34]·[회고][retro] | JWT 만료 테스트가 `Thread.sleep`에 의존 | `main/global/config/ClockConfig.java`, `JwtProvider`의 `Clock` 주입 | `JwtProviderTest.accessTokenValidBeforeExpiryAndRejectedAfterWithoutSleeping` | 상환 |
| [Day 25][d25]·[Day 34][d34]·[회고][retro] | 로그아웃해도 토큰이 만료까지 유효, stateless 한계를 설명하지 못함 | `main/member/MemberRepository.java`(`increaseTokenVersion` 원자적 UPDATE), `main/auth/AuthService.java`(`logout`, 학교 연결 삭제 포함), `main/global/security/JwtAuthenticationFilter.java` ([ADR-0005](adr/0005-jwt-access-refresh-token-version.md)) | `AuthFlowHttpTest.logoutInvalidatesExistingTokensAndForgetsSchoolConnection`(옛 이름 `logoutInvalidatesExistingAccessAndRefreshTokens`), `LogoutConcurrencyTest.concurrentLogoutsNeverLoseAnIncrement` | 상환 (처음 구현한 dirty checking 방식은 동시 로그아웃에서 증가를 잃어 Codex 리뷰 #1로 교체, (C) 참고) |
| [Day 33][d33] | 동시 요청 경합은 "고치지 않을 것" | 학교 학번·수강 소속·일정 외부 키의 DB UNIQUE와 짧은 학교 락, 무결성 예외의 공통 응답 | `GlobalExceptionHandlerWebMvcTest.dataIntegrityViolationBecomes409DataConflict`, 같은 학번 반복 로그인 검사. 자동 등록/수집의 동시 경합 자체를 재현하는 테스트는 없음 | 부분 상환 `미검증`(경합 재현) |
| [Day 32][d32]·[회고][retro] | H2 호환 모드 ≠ 실제 DB(MySQL 1064). 다음 목표가 Testcontainers | `test/PostgresSchemaTest.java`, `test/PostgresSchemaUpgradeTest.java` (PostgreSQL 17, `@ServiceConnection`) | `PostgresSchemaTest` 2건, `PostgresSchemaUpgradeTest` 2건(V2→V6 데이터 업그레이드) | 상환: CI(PR #1)에서 4건 실행·통과. 로컬은 Docker 없어 skipped. CI는 skip을 실패로 처리 |
| [Day 31][d31] | Dockerfile에 의존성 레이어 캐시 없음 | `Dockerfile`(`./gradlew dependencies`를 `COPY src` 앞에 분리), `.dockerignore`, `compose.yaml` | CI `docker` 잡 | 상환: CI(PR #1)에서 compose 기동·health check 통과 |
| [Day 32][d32] | `actions/*@v4` deprecation 경고 | `.github/workflows/backend-ci.yml`(`actions/checkout@v5`, `actions/setup-java@v5`) | CI 실행 | 상환: PR #1에서 `test`·`docker` 잡 통과 |
| [Day 29][d29] | Authorization 마스킹이 스킴을 버림 | 접근 로그에 헤더·본문·쿼리 미기록, `SensitivePaths`로 ICS 경로와 오류 instance 마스킹 | `AuthFlowHttpTest.missingOrMalformedTokensHaveConsistentProblems`, `SensitivePathsTest`, `IcsHttpTest.errorBodyNeverEchoesFeedToken`, 실제 앱 스모크 로그 | 상환 |
| Day 9 | OS 환경변수가 yml을 덮어써 빌드가 깨짐 | `build.gradle.kts` Test 태스크: `SPRING_*` 제거 + `SPRING_PROFILES_ACTIVE=test`. `bootRun` 태스크도 `SPRING_*` 제거 | `gradlew test` 전체, 외부 DB용 `SPRING_*`가 남은 셸에서 `gradlew bootRun`이 local H2로 기동하는 것 확인 | 상환 (같은 사고가 이번 첫 `bootRun`에서 재현된 뒤 막음) |
| [회고][retro] §4 | "계층 분리·트랜잭션 경계·인증·테스트를 공모전 프로젝트 코드에 적용" | 이 백엔드 전체 ([ADR-0001](adr/0001-feature-first-packages.md)~[ADR-0004](adr/0004-transaction-boundary.md)) | Claude 독립 리뷰 반영 후 132개 중 130개 통과, 실패 0, PostgreSQL 2개 skipped | 상환 |

## (B) 이번 프로젝트에서 남은 것

### 바로 고칠 것

| 항목 | 내용 | 라벨 |
|---|---|---|
| `PostgresSchemaTest`와 CI `docker` 잡 | 로컬에 Docker가 없어 한 번도 돌지 않았습니다. 첫 푸시 후 CI 결과를 확인하고, Testcontainers가 Docker API 버전 문제로 실패하면 `testcontainers.version`을 올립니다 | `미검증` |
| 데이터가 있는 상태의 PostgreSQL 업그레이드(리뷰 M10) | 작성 완료. `SchemaUpgradeScenarios`를 H2/PG17이 공유하여 V2 데이터 보존·격리, V4 연결의 V5 backfill/NOT NULL, V6 조정 테이블·컬럼 쓰기를 검사합니다. | 실행 `미검증`(Docker 없음으로 PG skipped), H2 통과 |

### 나중에 고칠 것

| 항목 | 내용 | 라벨 |
|---|---|---|
| Spring Boot 4 이전 | 스터디에서 검증한 API를 그대로 쓰려고 3.5.16을 택했습니다 | |
| refresh 재사용 탐지 | 탈취된 refresh 토큰의 재사용을 감지하지 못합니다. Redis나 토큰 테이블로 회전·재사용 탐지를 넣습니다([회고][retro]의 다음 목표와 연결). [ADR-0005](adr/0005-jwt-access-refresh-token-version.md) | |
| 기기별 로그아웃 | `token_version`을 올리므로 로그아웃하면 모든 기기에서 로그아웃됩니다 | |
| Google 소셜 로그인(F1) | 명세의 `oauth_provider`가 아직 없습니다 | |
| CORS 출처 설정 | 웹 도메인이 확정되면 `SecurityConfig`에 추가합니다 | |
| 학교별 시간대 | `CampusTime.ZONE`이 `Asia/Seoul` 고정입니다. 3차 학교 확장 때 학교 속성으로 옮깁니다 | |
| 공지 크롤러·AI·학교 확장 | 서울과기대 인증·과목/할 일 최소 수집은 이번 구현에 포함됨. 공지 크롤러(`Notice.collected` 공급), AI, 다른 학교 어댑터, 강의계획 PDF/추정 날짜는 미구현 | 합의 범위 밖 |
| 운영 ERD 이관 검증 | 관련 구조는 V3·V4로 반영하고 H2 이관을 검증함. V5(연결 세대 컬럼)는 H2 적용·validate만 확인. 전체 LMS DDL 구현이 아님. 운영 백업/복원·실제 PostgreSQL CI 결과 확인 필요. [ERD 인계](erd-handoff.md) | `미검증` |
| 403 응답 테스트 | 비활성(정지) 계정의 로그인은 `EclassAccountService.authenticated`가 `BusinessException(FORBIDDEN)`으로 403을 냅니다(전역 핸들러 경로). 그러나 인증 회원은 모두 `ROLE_USER`라 Security의 `ProblemAccessDeniedHandler` 경로를 타는 요청은 없고, 이 핸들러의 테스트도 없습니다. 역할이 생기면 테스트를 추가합니다 | `미검증` |
| 수집 일정 잔존·숨김(리뷰 M3 나머지) | 이후 수집에서 사라진 항목은 지우지 않고, 사용자는 수집 일정을 숨기거나 삭제할 수 없습니다(`EVENT_NOT_EDITABLE`). 원문에 항목 id가 없으면 제목 기반 식별이라 제목이 바뀌면 새 일정이 생깁니다. todo↔과제/퀴즈 목록 중복은 일대일·같은 KST 날짜일 때만 합치고 모호하면 모두 보존합니다(Codex F2). 이전 수집으로 이미 DB에 생긴 중복은 정리하지 않습니다 | |
| 장기 미사용 회원 수집 | 로그아웃하지 않은 회원은 앱을 쓰지 않아도 계속 자동 수집됩니다. 마지막 앱 사용 기준 중단 규칙이 없습니다 | |
| 실패 제한·single-flight의 범위 | V6의 DB 실패 창·예약·sync lease로 여러 서버에 공유합니다. 메모리 경로 제거. 프록시는 기본 불신, CIDR을 명시한 peer의 XFF만 오른쪽부터 읽습니다. 학번은 NFKC/strip·공백/제어 문자 거부, throttle만 소문자 버킷입니다. | 학교 자체 잠금 기준·실학교 Unicode 규칙, 프로세스 간 부하·시계 차이는 `미검증` |
| 수동 동기화 쿨다운 기준 | 쿨다운은 마지막 성공·실패·로그인 완료 시각 기준입니다(시도 시작 시각 아님). 로그인 직후 1분 차단은 의도한 동작이지만, 첫 수집 전 PENDING 사용자도 수동 재시도를 못 합니다. 구현 오류가 아닌 UX 선택으로 둡니다(Codex 검증 의견) | |
| 수정 후 HTTP 스모크 | 47건 HTTP 스모크(실제 JAR + 로컬 학교 서버)는 e-class/ERD 전환 시점 실행입니다. Claude 리뷰·Codex 검증 수정 후에는 다시 돌리지 않았습니다 | `미검증` |
| 리뷰 Low 잔여 | L1 공유 과목명을 회원별 스냅샷이 덮어씀·수강 해제 없음, L2 요청 예산(180)과 과목 상한 불일치(HTTP 전체 기한은 2026-11-08 추가), L3 학번 정규화·형식 검증은 2026-11-08 해소, L6 날짜만 있는 마감 정렬, L7 `course_enrollments.user_id` 인덱스, L8 공지 목록 N+1·게시판 미필터, L9 `posts.author_id` ON DELETE, L10 record `toString`의 비밀값·AES-GCM AAD/키 id·제약 위반 원문 로그, L11 기본 프로필 `local`, L12 학교 세션 오류의 401 재사용, L13 락 순서 주석 불일치, L14 일부(32바이트 미만 e-class 키·nonce 변조 테스트, `CAMPUS_ECLASS_*` 환경변수 격리, `iat` 방어), L15 일부(`CourseController`의 컨트롤러 `@Transactional`, erd-handoff의 posts ERD 차이 표시). 상세는 `docs/reviews/claude-eclass-erd-review.md` | |
| 나머지 Spring MVC 표준 예외 | `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속하지 않아, 따로 잡지 않은 예외(예: 406 `HttpMediaTypeNotAcceptableException`)는 500 `INTERNAL_ERROR`로 떨어집니다. 415·405는 Codex #3에서 처리했습니다([ADR-0006](adr/0006-problem-detail-errors.md)) | |
| R01 개인정보 처리방침 | 명세 157행 기준 저장소 산출물에 처리방침이 없습니다. 코드 취약점이 아니라 운영·정책 과제입니다. 운영/정책 담당, 현재 처리하는 데이터·목적·보유/삭제 기준을 반영한 방침의 제공 위치, 서비스 공개 전 확인 조건을 정합니다. 임의 보유기간이나 동의 체크박스를 지어내지 않습니다 | `미충족`, 운영·정책 |
| R02 최소 수집의 정책적 근거 | 최소성의 정책 문서는 아직 없음. R01에서 학번·학교·선택 프로필·일정·피드·학교 세션 쿠키·선택 저장 학교 비밀번호별 목적/필수·선택/보유·삭제 기준을 대조해야 함. 암호화나 opt-in 파라미터만으로 방침·동의 UI가 갖춰진 것은 아님 | `부분 충족` |
| A08 프로필 학기 표시 | 화면 `00-공통.md` 46행 프로필 예시에는 학기가 있지만 응답은 학년까지만 있습니다. 실제로 표시할지와 데이터 원천(F2로 단정하지 않음)을 정합니다. 화면 문서 83행에서 제거된 배너 학기 요구와 섞지 않습니다 | `부분 충족`, Low |

### 고치지 않을 것

| 항목 | 이유 |
|---|---|
| Windows `bootRun` 종료 후 8080 점유 | 포크된 JVM이 남는 개발 환경 문제입니다. 수동 종료로 대응합니다 |

### 사용자 결정 대기

명세 상호검증((D))에서 판정은 합의했지만 처리 방향은 사용자·기획 결정이 필요한 항목입니다. 결정 전에는 판정을 바꾸지 않고, 범위 제외로도 처리하지 않습니다.

| 항목 | 현재 판정 | 선택지 |
|---|---|---|
| E03/E09 표시일 규칙(오늘 할 일 집계 C11 포함) | `부분 충족`, Medium | ① 마감일과 별도의 단일 할 일 날짜(표시일)를 둔다 ② 기간 동안 반복 노출되는 가용 구간을 모델링한다 ③ 화면을 마감일 목록으로 바꾸고 예시·집계를 고친다. 어느 쪽이든 날짜별 목록·날짜 점·오늘 남은 수·오늘 마감 수의 기준을 함께 정합니다. `startsAt`을 표시일로 전용하지 않습니다(ICS 시작 시각과 의미가 섞임). [ERD 인계](erd-handoff.md)의 E03과 같은 항목입니다 |
| E16/C6 마감 임박 푸시 | `미충족`·범위 미합의, Medium | 원본 F3 55행에 있고 합의된 제외 목록에는 없습니다. ① 후속 범위로 돌린다(그때 판정을 범위 밖으로 변경) ② 이번 범위에 넣는다(기기 등록·임박 기준·발송/중복 처리·시간대와 수용 기준을 먼저 정함). D-day나 ICS가 푸시를 대신하지 않습니다 |
| 탈퇴의 학교 실시간 인증 의존(리뷰 M9) | 결정·구현 완료 | 학교 장애만 JWT auth_time 최근 10분(설정 가능) 경로를 허용합니다. refresh는 시각 유지, 틀린 비밀번호는 항상 거부. 새 비밀값 저장 없음. 만료/클레임 부재는 503으로 데이터 보존 |

## (C) Codex 리뷰 반영

리뷰어: Codex(`openai-codex/gpt-6.1-sol`), 원문 `docs/reviews/codex-review.md`. 반영 후 `gradlew test`: 44개 중 42개 통과, 실패 0, 2개 skipped(`PostgresSchemaTest`). 이후 #9 테스트 보강으로 77개 중 75개 통과, 2개 skipped. (D)의 회원 탈퇴 추가 후 82개 중 80개 통과, 2개 skipped.

이 절은 **이메일 인증 버전 당시**의 리뷰 기록이다. 삭제된 가입/BCrypt/옛 테스트 이름은 당시 근거이며, 현행 전환 결과는 (E)와 ADR-0007을 기준으로 본다.

| # | 심각도 | 지적 | 처리 |
|---|---|---|---|
| 1 | High | 로그아웃이 dirty checking(읽고 `++` 후 커밋)이라 동시 로그아웃 시 `token_version` 증가가 유실되고, 그 사이 발급된 토큰이 살아남을 수 있음 | 수정. `Member.increaseTokenVersion()` 삭제, `MemberRepository.increaseTokenVersion(id)`(`@Modifying` 원자적 UPDATE, 0행이면 `UNAUTHORIZED`)를 `AuthService.logout`이 호출. 테스트 `LogoutConcurrencyTest.concurrentLogoutsNeverLoseAnIncrement`(8스레드 → 8). 옛 코드를 임시로 되살리고 50ms sleep을 넣었을 때 이 테스트가 실패하는 것을 확인한 뒤 임시 코드 제거. 교훈은 [ADR-0004](adr/0004-transaction-boundary.md): Day 12·15 지식은 맞았지만 공유 카운터에는 불완전했음 |
| 2 | Medium | ICS 토큰 마스킹이 원문 URI `startsWith("/ics/")`라 퍼센트 인코딩·대소문자 변형으로 우회되고, 오류 본문 `instance`에 토큰이 그대로 실림 | 수정. `global/logging/SensitivePaths.mask`(URL 디코딩 + 소문자화, 디코딩 불가면 `/***`)를 `RequestIdFilter` 로그와 `ApiProblems.instance`에 사용. 테스트 `SensitivePathsTest`(3), `IcsHttpTest.errorBodyNeverEchoesFeedToken`, `IcsHttpTest.rotatedTokenInvalidatesOldUrl`(`$.instance == "/ics/***.ics"`). [ADR-0008](adr/0008-observability-request-id.md) |
| 3 | Medium | 지원하지 않는 Content-Type이 500으로 떨어지고, 405에 `Allow` 헤더가 없음 | 수정. `ErrorCode.UNSUPPORTED_MEDIA_TYPE`(415) 추가(당시 총 18개, 이후 (D)의 `INVALID_PASSWORD`로 19개), `GlobalExceptionHandler`가 `HttpMediaTypeNotSupportedException` → 415 + `Accept`, 405에 `Allow`. 테스트 `AuthFlowHttpTest.unsupportedContentTypeReturns415NotServerError`, `unsupportedMethodReturns405WithAllowHeader`(지금은 `PUT /api/me`로 검증). 나머지 MVC 표준 예외(406 등)는 (B) 나중에 고칠 것 |
| 4 | Low | 공지 `page`에 상한이 없어 offset이 int를 넘으면 500 | 수정. `NoticeController`의 `page`에 `@Max(10_000)`. 테스트 `NoticeHttpTest.pageBeyondOffsetRangeReturns400NotServerError` |
| 5 | Low | U+3000 같은 유니코드 공백만 있는 이름·제목이 `@NotBlank`를 통과하고 `strip()` 후 빈 문자열이 되어 DB CHECK 위반 409 | 수정. `AuthService.signup`, `EventService.requireTitle`이 `strip()` 후 다시 검사해 400 `VALIDATION_FAILED`. 테스트 `AuthFlowHttpTest.unicodeWhitespaceOnlyNameReturns400NotConflict`, `EventHttpTest.unicodeWhitespaceOnlyTitleReturns400NotConflict` |
| 6 | Low | 기간 상한이 `between > 62`라 실제로는 양 끝 포함 63일까지 허용(문서의 "최대 62일"과 불일치) | 수정. `EventService.list`가 포함 일수(`between + 1 > 62`)로 검사. 62일 허용, 63일 거부. 테스트 `EventHttpTest.rangeAllowsExactly62InclusiveDays` |
| 7 | Low | `Bearer` 스킴을 대소문자 구분해 `bearer <token>`을 거부 | 수정. `JwtAuthenticationFilter`가 스킴을 대소문자 무시로 비교. 테스트 `AuthFlowHttpTest.bearerSchemeIsCaseInsensitive` |
| 8 | Low | 401 응답에 `WWW-Authenticate` 헤더가 없음 | 수정. `ProblemAuthenticationEntryPoint`가 `WWW-Authenticate: Bearer` 설정. `AuthFlowHttpTest.missingTokenReturnsProblemJsonWithRequestId`에서 단언 |
| 9 | Low | 테스트 공백: PUT/DELETE HTTP 테스트, ICS 피드의 수정·삭제 반영, 회원 간 격리 | 리뷰 반영 뒤 별도로 보강. `EventLifecycleHttpTest`(PUT 전체 교체, DELETE 후 404, 남의 PUT·DELETE 404와 원본 보존, e-class 일정 409), `IcsHttpTest.sameFeedUrlReflectsLaterEditsAndDeletions`, `feedContainsOnlyOwnRecentEventsIncludingDoneOnes` |
| 10 | Low | 문서는 HS256이라 하지만 `signWith(key)`는 키 길이에 따라 알고리즘을 고름 | 수정. `JwtProvider`가 `signWith(key, Jwts.SIG.HS256)`으로 명시. [ADR-0005](adr/0005-jwt-access-refresh-token-version.md) |
| 11 | Low | ADR-0005가 명세 F1이 로그아웃 즉시 무효화를 요구한다고 잘못 적음 | 수정(문서). F1 원문은 Google/간편 로그인, 학교 계정 연동, 프로필뿐이며, 즉시 무효화는 이 백엔드 범위를 정하며 사용자와 합의해 추가한 보안 요구사항이라고 [ADR-0005](adr/0005-jwt-access-refresh-token-version.md) 맥락을 고침 |

리뷰어가 문서대로 수용한 항목: refresh 토큰은 갱신 때 회전·폐기되지 않습니다. (B)의 "refresh 재사용 탐지"에 이미 있습니다.

## (D) 명세 상호검증 (Claude·Codex)

방법: Claude와 Codex가 명세 v0.1과 화면 문서를 기준으로 현재 코드·테스트를 각자 독립 대조했습니다(1라운드). 결과를 서로 교환한 뒤 Claude가 응답하고, Codex가 2라운드에서 이견을 정리해 아래 합의 목록을 냈습니다. Claude는 2라운드의 수정(C4 충족 범위 축소, C6 "범위 밖"→"범위 미합의", C11 부분 충족, C17 summary+url은 본문 대체가 아님, C15 학년/학과 분리, C19 적용 대상 누락과 합의 제외 분리)을 모두 받아들였습니다. 원문은 `build/`(gitignore)에 있어 이 표가 지속 기록입니다.

원칙: 판정과 처리를 분리합니다. ERD 인계나 원장 등록은 충족으로 바꾸지 않고, 명시적 범위 변경 없이는 범위 밖으로도 바꾸지 않습니다. 아래에 없는 C1·C2·C5·C7·C9·C10·C13·C14·C16·C18은 판정 차이가 없었습니다(C10 충족은 마감일 기준 기간 조회에 한정, C14는 요약 조회 충족·AI 생성 범위 밖).

| 항목 | 최종 판정 | 처리 | 결과 |
|---|---|---|---|
| R03 탈퇴 시 회원·일정·피드 삭제 | **충족** | 구현 | 현행 `WithdrawalService`는 학교 비밀번호를 DB 밖에서 확인하고 `WithdrawalStore.delete`가 일정·피드·소속·연결 비밀·사용자를 한 트랜잭션에서 삭제. 공유 학교·과목·공지 보존. 학교 계정 자체는 삭제하지 않으므로 재로그인 시 새 앱 id 생성. HTTP 4건/정확한 예외 주입 롤백 1건과 실제 앱 스모크로 검증 |
| E03/E09 표시일·마감일, 오늘 할 일 집계(C11) | `부분 충족`, Medium | 사용자 결정 | 대기. (B) "사용자 결정 대기", [ERD 인계](erd-handoff.md) E03 |
| E05 과목명·강의 길이(C8, C4) | API 계약 구현, 실제 학교 공급 검증은 `부분 충족` | ERD 반영 | `courseId/courseName/durationSeconds`와 조회·수집 회귀 구현. 원문 부재는 null. 모든 강의의 실제 길이 확보를 검증했다고 주장하지 않음 |
| N06 공지 상세 본문(C17) | **충족(조회 API 계약)** | ERD 반영 | `posts.body_text`와 상세 `body`, 문단/빈 줄/목록 보존을 테스트. 크롤러/본문 공급은 별도 |
| N09 대상 학과(C15) | **충족(조회 API 계약)** | ERD 반영 | 순서 있는 `targetDepartments`, 빈 목록=학과 제한 없음. 복수 학과 상세 조회를 테스트. 태깅 생성은 별도 |
| E16/C6 마감 임박 푸시 | `미충족`·범위 미합의, Medium | 사용자 결정 | 대기. 범위 밖으로 단정하지 않음 |
| R01 개인정보 처리방침 | `미충족`(저장소 산출물 기준) | 원장 등록 | (B) 나중에 고칠 것, 운영·정책 |
| R02 최소 수집의 정책적 근거 | `부분 충족` | 원장 등록 | (B) 나중에 고칠 것 |
| A08 프로필 학기 표시(C3 일부) | `부분 충족`, Low | 원장 등록 | (B) 나중에 고칠 것 |
| C3 학과·학년 수정 API | **충족** | 구현 | `PATCH /api/me`: 생략 유지/null 비우기, 학과 200자/학년 1~6. `MemberProfileHttpTest` 6건 |
| C12/E11 개인 일정 제목 | **충족** | 구현 | 개인 POST/PUT 80자, 수집/DB 200자. `EventLifecycleHttpTest.personalTitleIsLimitedTo80WhileImportedTitleMayBeLonger` |

### 3라운드: R03 구현을 Codex가 수용 기준 ①~⑦로 검증

Codex는 운영 코드 결함은 없다고 판정했지만, 테스트가 기준을 실제로 고정하는지 보면 ②·⑤·⑥이 미충족이라고 지적했습니다. 셋 다 받아들여 고쳤습니다.

| 지적 | 심각도 | 내용 | 처리 |
|---|---|---|---|
| F1 | Medium | 롤백 테스트가 예외 클래스와 최종 행 존재만 봐서, "지워졌다가 롤백됨"과 "처음부터 안 지워짐"을 구분하지 못함(다른 원인의 같은 예외로도 통과 가능) | `WithdrawalRollbackTest`를 다시 씀: 일정·피드 삭제 뒤 회원 삭제 단계에 고유 예외 주입, 같은 트랜잭션 안에서 일정·피드 부재를 기록, `assertSame`으로 예외 동일성 확인, 이후 세 행 복원 확인. `@Transactional`을 임시로 빼면 실패하는 것을 확인 |
| F2 | Low | 공유 Notice 보존을 확인하지 않음(기준 ⑤) | `withdrawalLeavesOtherMembersSharedSchoolAndNoticesIntact`가 고유 공지를 만들고 탈퇴 후 그 공지의 id·제목·url을 상세 API로 확인 |
| F3 | Low | 공백 비밀번호·깨진 JSON의 400을 직접 검증하지 않음(기준 ②) | `missingBlankMalformedOrWrongPasswordKeepsEverything`가 누락·공백·깨진 JSON 400과 불일치 401 뒤 데이터가 그대로인지 확인 |

3라운드 당시 결과는 82개 중 80개 통과, 실패 0, 2개 skipped였다. 현행 상태는 위 표와 아래 (E)로 갱신했다. E03·E16 사용자 결정, R01·R02·A08 정책/표시 결정은 여전히 남아 있다.

## (E) 확정 DDL 관련 구조 + e-class 직접 로그인 전환

사용자 합의: 기존 F1·F3·F6 기능과 PostgreSQL을 유지하면서 제공된 MySQL `STack DB.sql`의 관련 구조를 확장한다. 후속 지시로 이메일 회원가입을 제거하고 Planary 방식의 학교 학번·비밀번호 인증 후 앱 계정을 자동 생성한다. 그에 필요한 과목·할 일 수집은 이번 범위에 포함한다. 전체 LMS/AI/공지 크롤러로 범위를 넓히지 않는다.

| 변경 | 결과·근거 |
|---|---|
| 인증 cutover | signup/로컬 비밀번호 해시/이메일 필드 제거. 실제 폼·숨은 필드·쿠키·동일 origin·인증 표식 검증. `AuthFlowHttpTest`, `HttpEclassGatewayTest` |
| ERD 이관 | V1·V2 보존, V3 users/연결 비밀, V4 과목/소속/게시판/posts/일정 메타데이터. 기존 이메일 계정은 학번으로 추정하지 않고 정지·JWT 버전 증가. `SchemaUpgradeTest` |
| 저장 비밀 | 기본 비밀번호 미저장, 명시적 remember opt-in만 AES-256-GCM 저장. 쿠키도 암호화. 별도 필수 prod 키, 연결 해제/탈퇴 시 삭제. 변조·다른 키 복호화 거부 테스트 |
| 트랜잭션 | 네트워크 밖 조정 → 짧은 저장/삭제 트랜잭션, 학교→사용자 락 순서. 토큰 버전·완료 표시를 덮어쓰지 않는 DynamicUpdate |
| 일정 데이터 | 과목/길이/원문 링크, nullable 마감·D-day, 날짜 미정 API, 날짜만 있는 항목의 종일 ICS. 반복 수집에서 id·완료·개인/미수집 항목 보존 |
| 실제 재현·수정 | POST 200 후 인증 확인 전에 login URL이라는 이유로 거부하던 분기 수정. 같은 과목 링크를 쓰는 서로 다른 과제가 하나로 합쳐지는 문제는 실제 앱 HTTP에서 재현·수정하고 회귀 추가 |
| 탈퇴 | 학교 비밀번호 확인 뒤 전용 데이터 원자적 삭제·공유 데이터 보존. 새 로그인은 새 앱 id이며 옛 JWT/일정 부활 없음. 학교가 인증 불가능하면 비밀번호 재확인/탈퇴도 완료할 수 없는 운영 제약이 있음 |
| 검증 | `test bootJar` 성공, 110개 중 108개 통과/실패 0/PostgreSQL 2개 skipped. 실제 JAR + 로컬 학교 HTTP 서버 + H2에서 47개 HTTP 확인 통과. 외부 DB/실제 학교 계정 사용 없음 |

**남은 검증/정책:** PostgreSQL·Docker·CI, 실제 학교 인증/원문 선택자·학교의 자동화 허용 정책, 실명·학과·학년·강의 길이의 신뢰할 수 있는 공급 경로. 수집 중 회전한 쿠키는 해당 fetch 안에서만 유지되고 스냅샷 반환 계약에는 포함하지 않으므로 다음 수집에서 재인증이 필요할 수 있다. 날짜나 원문 항목 키가 없을 때 임의 값/추정 날짜를 만드는 대신 부재와 제한을 노출한다. R01·R02에 학교 자격 보관·삭제와 키 교체 시 재연결을 반영해야 한다. 최신 API/설정은 [README](../README.md), 상세 매핑은 [ERD 인계](erd-handoff.md)를 참고한다.

## (F) Claude 독립 리뷰 반영 (e-class 인증·ERD 확장)

리뷰어: Claude(독립 검토, 정적 읽기), 원문 `docs/reviews/claude-eclass-erd-review.md`(판정 REQUEST_CHANGES). 반영 후 `gradlew test`: 132개 중 130개 통과, 실패 0, 2개 skipped(`PostgresSchemaTest`, Docker 없음). 실제 앱 스모크는 다시 돌리지 않았습니다. ErrorCode는 `ECLASS_SYNC_IN_PROGRESS`(409)·`TOO_MANY_REQUESTS`(429)를 더해 22개입니다. 이후 Codex 교차 검증으로 H3·M3·M7·M8의 보장 범위가 좁혀졌고 후속 수정은 (G)에 있습니다.

| # | 심각도 | 지적 | 처리 |
|---|---|---|---|
| H1 | High | 학교가 거절한 저장 비밀번호로 5분마다 재로그인 → 학교 계정 잠금 위험 | 수정. 백그라운드 재인증에서 거절되면 저장 비밀번호 삭제, `REAUTH_REQUIRED`, `lastErrorCode=INVALID_CREDENTIALS`, 다시 시도 안 함. `EclassConnectionRepository.findDueMemberIds`는 `ACTIVE` 회원의 `PENDING`/`READY`만 `sync-delay` 뒤, `FAILED`는 `campus.eclass.failure-backoff`(PT30M) 뒤, `REAUTH_REQUIRED`는 제외. 테스트 `EclassSyncHttpTest.rejectedSavedPasswordIsForgottenAndNeverRetried`, `transientFailureIsRetriedOnlyAfterTheBackoff` |
| H2 | High | 이름 없는 과목이 `course_name NOT NULL`을 어겨 수집 전체가 매번 실패 | 수정. null/공백 과목명은 과목을 만들거나 덮어쓰지 않고(일정은 과목 없이 수집), `Course.refresh`는 null 무시. 테스트 `EclassImportHttpTest.unnamedNewCourseIsNotCreatedButItsTaskIsStillImported`, `unnamedSnapshotKeepsExistingCourseName` |
| H3 | High | 인증 없는 로그인이 학교 비밀번호 대입 중계기 | 수정. `auth/LoginAttemptThrottle`: 학번당 5회·클라이언트(`getRemoteAddr()`)당 20회 실패, 고정 창 15분, 초과 시 학교 요청 전에 429 + `Retry-After`. 성공은 그 학번 카운터만 지움. `DELETE /api/me`는 학번 기준만. 수동 동기화는 회원별 single-flight(409 `ECLASS_SYNC_IN_PROGRESS`)와 쿨다운 `manual-sync-cooldown`(PT1M, 429). 테스트 `LoginAttemptThrottleTest`, `AuthFlowHttpTest.repeatedWrongPasswordsStopReachingTheSchool`, `ManualSyncCooldownHttpTest.manualSyncRightAfterLoginIsRejectedWithRetryAfter`. 이 시점에는 동시 요청이 한도를 넘을 수 있었음 → (G) F1에서 진행 중 시도 예약으로 수정. 단일 인스턴스 메모리라는 한계는 (B) |
| M1 | Medium | SUSPENDED(레거시) 계정 ICS 피드가 계속 열림 | 수정. `findMemberIdByToken`이 `ACTIVE` 회원만 조회 → 404 `CALENDAR_FEED_NOT_FOUND`. 테스트 `IcsHttpTest.suspendedMembersFeedIsNotServed` |
| M2 | Medium | 긴 원문 하나가 길이 초과로 수집 전체 롤백, KJ 키 63자 vs 컬럼 40자 | 수정. `schedule/ImportLimits`: 제목 200·과목명 300 코드포인트로 자름, `source_url` 2000 초과는 null, `external_uid` 200 초과는 `h:`+SHA-256 hex. KJ 키 상한 40. 테스트 `EclassImportHttpTest.overlongTitleIsTruncatedAndOverlongSourceUrlIsDropped`, `EclassHtmlParserTest.kjKeyLongerThanColumnIsRejected` |
| M3 | Medium | todo와 과제 목록의 같은 과제가 두 번 수집, 잔존 항목 삭제 불가 | 부분 수정. `HttpEclassGateway.dedupe`가 같은 과목 키·종류·정규화 제목의 중복을 todo id로 하나만 남김. 테스트 `EclassHtmlParserTest.sameAssignmentOnTodoAndReportListKeepsOnlyTodoIdentity`. 이 규칙은 동명의 다른 과제를 누락시킬 수 있었음 → (G) F2에서 일대일·같은 날짜 조건으로 좁힘. 잔존·숨김·제목 식별은 미수정, (B) 나중에 고칠 것 |
| M4 | Medium | 연도 없는 날짜를 항상 올해로 해석 | 수정. KST 오늘과 가장 가까운 연도(−1/0/+1) 선택. 테스트 `EclassHtmlParserTest.yearlessDatesRollOverToTheClosestYear` |
| M5 | Medium | `비…` 비정규 과목 KJ 키 거부 | 수정. `[\p{IsHangul}A-Za-z0-9._-]{1,40}`. 테스트 `EclassHtmlParserTest.hangulNonRegularKjKeyIsAcceptedFromDataAttributeAndEncodedQuery` |
| M6 | Medium | 수집 파이프라인이 자동 테스트에서 한 번도 실행되지 않음 | 수정. `support/EclassTestConfiguration.FakeEclass`를 학번별 비밀번호·스냅샷·세션 만료·인증 호출 수를 조절하는 가짜로 바꿈. 테스트 `EclassSyncHttpTest`(6): `manualSyncImportsTheSchoolSnapshotIntoTheCalendar`, `expiredSessionWithRememberedPasswordReauthenticatesOnce`, `expiredSessionWithoutPasswordAsksForReloginAndLeavesTheSchedule` 외 3. loopback 픽스처 서버를 쓰는 Spring 통합 테스트는 만들지 않음 |
| M7 | Medium | 수집 실패 무로그, 복호화 실패를 일시 장애로 영구 재시도 | 수정. `eclass/UnreadableSecretException` → `REAUTH_REQUIRED` + 저장 비밀번호 삭제 + memberId만 담은 warn. 예상 못 한 RuntimeException은 예외 클래스 이름만 warn 후 `ECLASS_UNAVAILABLE`. 테스트 `EclassSyncHttpTest.undecryptableStoredSecretsRequireReloginInsteadOfEndlessRetry`. 이 시점에는 `BusinessException`(네트워크 장애 포함) 경로가 무로그였음 → (G) F4에서 수정 |
| M8 | Medium | 로그아웃 후에도 학교 연결이 남아 배경 수집 계속 | 수정. `POST /api/auth/logout`이 같은 트랜잭션에서 `eclass_connections` 행(쿠키·저장 비밀번호) 삭제, 다음 로그인에서 재생성. 테스트 `AuthFlowHttpTest.logoutInvalidatesExistingTokensAndForgetsSchoolConnection`. 향후 스케줄 대상에서 빠질 뿐, 이미 진행 중인 학교 요청은 취소하지 않음. 로그아웃→재로그인과 겹친 옛 작업의 반영은 (G) F3에서 세대 검사로 막음. 로그아웃하지 않은 장기 미사용 회원은 (B) |
| M9 | Medium | 탈퇴가 학교 실시간 인증에 묶임 | 수정. 학교 장애 시 최근 auth_time 대체, `RemainingAuthHttpTest` 검증. [ADR-0005](adr/0005-jwt-access-refresh-token-version.md) |
| M10 | Medium | 데이터 있는 업그레이드는 H2에서만 검증 | 테스트 작성 완료. H2/PG 공유 시나리오, PG17 실행은 Docker 부재로 skipped |
| L4 | Low | 백그라운드 재인증 결과 미검증 | 수정. 프로필 학번 일치와 비어 있지 않은 쿠키 확인, 아니면 `ECLASS_UNAVAILABLE` |
| L5 | Low | 계획서 행 `orElseThrow()` 예외, `24:00` 미해석 | 수정. 단일 셀이 없으면 행 텍스트로 대체, `24:00`은 다음 날 00:00. 테스트 `EclassHtmlParserTest.syllabusRowWithExamKeywordSplitAcrossCellsDoesNotThrow`, `midnightWrittenAs2400IsTheEndOfThatDay` |
| L14 | Low | 테스트 약점 | 부분 수정. `AuthFlowHttpTest`의 쿠키 비노출 단언 강화, `AuthFlowHttpTest.reloginWithoutRememberForgetsSavedPassword` 추가. 나머지는 (B) |
| L15 | Low | 문서 불일치 | 부분 수정. 이 원장 (A)의 없는 테스트 이름, ADR-0008 fail-fast 목록의 `ECLASS_ENCRYPTION_KEY`, README 로그인 403 수정. `CourseController`의 `@Transactional`과 erd-handoff의 posts ERD 차이 표시는 미수정 |
| L1~L3, L6~L13 | Low | 각 항목은 원문 참고 | 미수정. (B) "리뷰 Low 잔여" |

## (G) Codex 교차 검증 반영 (Claude 수정분)

검증자: Codex(정적 검증 + 기존 테스트 결과 확인), 원문 `docs/reviews/codex-fix-verification.md`(판정 REQUEST_CHANGES). H1·H2·M1·M4·M5·L5는 확인, H3·M3·M7·M8은 부분 확인이었고 새 결함 F1~F5를 지적했습니다. 반영 후 `gradlew test`: **138개 중 136개 통과, 실패 0, 2개 skipped**(`PostgresSchemaTest`, Docker 없음). 변경 클래스의 테스트 수: `AuthFlowHttpTest` 12, `LoginAttemptThrottleTest` 4, `EclassSyncHttpTest` 8, `EclassHtmlParserTest` 17. V5는 H2에서 Flyway 적용 + validate로만 확인했고 PostgreSQL은 미검증입니다. 47건 HTTP 스모크는 다시 돌리지 않았습니다.

| # | 심각도 | 지적 | 처리 |
|---|---|---|---|
| F1 | High | 동시 요청이 실패 한도 확인을 함께 통과해 학교 비밀번호 실패 한도를 넘음 | 수정. `LoginAttemptThrottle.begin(studentId, client)`가 진행 중 시도 슬롯을 예약하는 `Attempt`(try-with-resources)를 돌려줌. `failed()`는 실패로 계산, `succeeded()`는 학번 카운터 초기화, `close()`는 계산 없이 반환. 입장 조건은 완료 실패 + 진행 중 시도 < 한도. 진행 중 시도 때문에만 막히면 `Retry-After` 1초, 그 밖에는 창 종료까지. `AuthService.login`, `WithdrawalService.withdraw`가 사용. 테스트 `LoginAttemptThrottleTest.concurrentAttemptsReserveTheBudgetBeforeTheSchoolAnswers`, `AuthFlowHttpTest.concurrentWrongPasswordsCannotExceedTheStudentBudget`(가짜 학교가 응답을 붙잡은 동안 8개 동시 요청 → 5개만 학교 도달, 3개 429) |
| F2 | Medium | 제목 기반 dedupe가 동명의 다른 과제를 누락 | 수정. `HttpEclassGateway.dedupe`는 과목·종류·정규화 제목이 같은 todo 1개와 목록 1개가 정확히 일대일이고, 마감이 같은 KST 날짜(또는 한쪽 미상)일 때만 합침. 모호한 그룹은 모든 행 보존. 테스트 `EclassHtmlParserTest.sameTitleWithDifferentDeadlineIsNotMerged`, `ambiguousSameTitleCandidatesAreAllKept`. 이전 수집으로 DB에 남은 중복은 정리하지 않음 |
| F3 | Medium | 이전 동기화가 재로그인으로 새로 만든 연결을 덮음 | 수정. `V5__eclass_connection_generation.sql`이 `eclass_connections.connection_generation VARCHAR(36) NOT NULL` 추가(기존 행은 `legacy-` + user_id로 채움). 로그인마다 새 UUID. 동기화는 context에 세대를 담고, 성공(`synchronizedSnapshot`이 boolean 반환)·실패·복호화 실패 반영은 연결 행 PESSIMISTIC_WRITE 락(`EclassConnectionRepository.lockById`) 아래에서 세대가 같을 때만 수행. 락 순서 학교 → 사용자 → 연결(로그인도 사용자 행을 잠금). 저장 비밀번호로 재인증하기 전에도 세대를 확인. 이미 보낸 학교 요청은 회수할 수 없음. 테스트 `EclassSyncHttpTest.staleSyncFinishingAfterReloginIsDiscarded`, `staleSyncNeverReauthenticatesWithAReplacedConnectionsPassword`(가짜 학교 `FakeEclass.hold`/`holdFetch`로 응답 보류) |
| F4 | Low | 실제 학교 통신 장애(`BusinessException`)가 로그 없이 처리 | 수정. 동기화의 `BusinessException` 실패를 memberId + ErrorCode만으로 기록. `ECLASS_REAUTH_REQUIRED`/`UNAUTHORIZED`는 info, 그 밖(네트워크 오류의 `ECLASS_UNAVAILABLE`, `INVALID_CREDENTIALS` 등)은 warn. 세대가 바뀌어 버린 옛 결과는 성공·실패 모두 info(2차 검증 N2 후속) |
| F5 | Low | 문서 일부가 실제 동작과 다름 | 수정. `application-test.yml` 쿨다운 주석, [ERD 인계](erd-handoff.md)의 이름 없는 과목 설명(기존 과목은 연결), [ADR-0007](adr/0007-test-strategy.md)의 slice 테스트 목록(`EventControllerWebMvcTest`, `GlobalExceptionHandlerWebMvcTest`) |

**후속 반영(2026-11-08):** M10 작성, 프록시 IP 정책·학번 정규화 구현 완료. 학교 자체 규칙·PG 실제 실행, 수동 쿨다운 UX와 47건 JAR 스모크 재실행은 남음.

### 2차 교차 검증

검증자: Codex(`cleanTest test`로 새로 실행: 138개 중 136개 통과, 실패 0, 2개 skipped), 원문 `docs/reviews/codex-fix-reverification.md`(판정 REQUEST_CHANGES). F1~F5는 모두 해결로 판정했고 새 결함 2건과 테스트 공백을 지적했습니다. 반영 후 `gradlew test`: **142개 중 140개 통과, 실패 0, 2개 skipped**(`AuthFlowHttpTest` 13, `EclassSyncHttpTest` 10, `SchemaUpgradeTest` 2). 실행 중 경고 0건(아래 W1).

| # | 심각도 | 지적 | 처리 |
|---|---|---|---|
| N1 | Medium | 로그인이 사용자를 일반 조회한 뒤 잠가서, 그 사이 커밋된 로그아웃의 `token_version` 증가를 영속성 컨텍스트가 반영하지 못하고 즉시 무효인 토큰을 발급(Codex가 Hibernate/H2로 재현) | 수정. `MemberRepository.lockByExternalRefAndSchoolId`로 처음 읽을 때부터 PESSIMISTIC_WRITE. 테스트 `AuthFlowHttpTest.loginRacingAnUncommittedLogoutIssuesTokensForTheNewVersion`(미커밋 로그아웃이 버전을 올린 상태에서 로그인 → 커밋 후 발급된 access로 `/api/me` 200). 옛 "조회 후 잠금" 코드로 임시 되돌리면 이 테스트가 401로 실패하는 것을 확인하고 되돌림. 로그인이 사용자 행에 도달하도록 200ms를 기다리므로, 느린 환경에서는 경합을 덜 재현할 뿐 거짓 실패는 하지 않음 |
| N2 | Low | "세대가 바뀐 옛 결과는 info만 남긴다"는 문서가 구현과 다름(옛 세대의 실패는 warn) | 수정(코드). `EclassAccountService.failed`/`secretsUnreadable`가 반영 여부를 boolean으로 돌려주고, `EclassSyncService`는 버려진 실패·복호화 실패·재인증 생략도 info "결과를 버림" 한 줄로 기록. 문서가 원래 말한 계약을 구현이 따르도록 바꿈. 테스트 `staleFailureAfterReloginWithoutLogoutIsDiscardedQuietly`(로그 캡처: info 한 줄, 실패 코드 로그 없음) |
| T1 | 공백 | 로그아웃 없는 재로그인, 옛 세대의 실패·복호화 실패 경합 미테스트 | 테스트 추가. `EclassSyncHttpTest.staleFailureAfterReloginWithoutLogoutIsDiscardedQuietly`(수집 대기 중 로그아웃 없이 재로그인 → 새 연결 PENDING, 오류 코드 없음), `staleFailureReportsNeverTouchTheReplacedConnection`(옛 세대의 `INVALID_CREDENTIALS`·복호화 실패 보고는 false, 새 비밀번호·쿠키 유지, 현재 세대 보고만 반영) |
| T2 | 공백 | F4 로그 레벨·내용 회귀 테스트 없음 | 테스트 추가. `rejectedSavedPasswordIsForgottenAndNeverRetried`가 출력 로그를 캡처해 `code=INVALID_CREDENTIALS` warn 한 줄과 비밀번호·변경된 비밀번호·세션 쿠키 미노출을 확인 |
| T3 | 공백 | 기존 연결 행이 있는 DB에서 V5 backfill 미검증 | 테스트 추가(H2). `SchemaUpgradeTest.existingEclassConnectionsReceiveALegacyGenerationAndNewRowsMustCarryOne`: V4에서 연결 행을 넣고 V5 적용 → `legacy-42`, 기존 비밀값·상태 보존, 세대 없는 새 행 INSERT 거부 |
| W1 | 경고 | 테스트 실행마다 `OpenJDK ... Sharing is only supported for boot loader classes` | 수정. Mockito inline 에이전트가 bootstrap classpath에 붙어 CDS가 경고. `build.gradle.kts` 테스트 JVM에 `-Xshare:off` |

후속(2026-11-08): 세대 확인/학교 재인증 사이 경합은 DB claim과 연결 변경 대기로 해소. 양쪽 원문 id가 있으면 id 기준으로 병합하고 확정 alias의 DB 중복도 정리. V2/V4→최신 업그레이드는 H2/PG 공유 시나리오를 작성했고 PG 실행은 Docker 부재로 skipped. 아래 후속 기록을 참고.

[d3]: https://velog.io/@snowmile1224/Spring-Study-Day-3-Input-Validation%EA%B3%BC-Global-Exception-Handling-Valid%EC%99%80-RestControllerAdvice-ilzw3bx3
[d7]: https://velog.io/@snowmile1224/Spring-Study-Day-7-1%EC%A3%BC%EC%B0%A8-%EB%B2%84%ED%8D%BC-%EC%A0%80%EC%9E%A5-%EA%B3%84%EC%95%BD%EA%B3%BC-Absence-of-Value%EC%9D%98-%EC%98%A4%EB%A5%98-%EA%B2%BD%EA%B3%84
[d25]: https://velog.io/@snowmile1224/Spring-Study-Day-25-JWT-%EC%9D%B8%EC%A6%9D-%EC%8B%A4%ED%8C%A8-%EC%BC%80%EC%9D%B4%EC%8A%A4-CSRFCORS%EB%A1%9C%EA%B7%B8%EC%95%84%EC%9B%83%EC%9D%98-Stateless-%ED%95%9C%EA%B3%84
[d26]: https://velog.io/@snowmile1224/Spring-Study-Day-26-%ED%85%8C%EC%8A%A4%ED%8A%B8-%EB%B2%94%EC%9C%84%EC%99%80-Mock-%EC%A0%84%EB%9E%B5-Slice-Test%EC%9D%98-Security-%EC%9E%90%EB%8F%99-%EC%84%A4%EC%A0%95-%EC%B6%A9%EB%8F%8C-jnvlnq75
[d27]: https://velog.io/@snowmile1224/Spring-Study-Day-27-Day-28-4%EC%A3%BC%EC%B0%A8-%EB%A7%88%EB%AC%B4%EB%A6%AC-%EC%8B%9C%ED%97%98-y03atpnv
[d29]: https://velog.io/@snowmile1224/Spring-Study-Day-29-%EC%9A%94%EC%B2%AD-%EB%A1%9C%EA%B9%85%EA%B3%BC-%EC%84%A4%EC%A0%95-%EB%B6%84%EB%A6%AC-Request-IDMDC%EC%99%80-Profile%EB%B3%84-Secret-%EA%B4%80%EB%A6%AC-4qe9fhmb
[d31]: https://velog.io/@snowmile1224/Spring-Study-Day-31-%EC%BB%A8%ED%85%8C%EC%9D%B4%EB%84%88-%EC%9D%B4%EB%AF%B8%EC%A7%80-%EB%B9%8C%EB%93%9C-%EB%A9%80%ED%8B%B0%EC%8A%A4%ED%85%8C%EC%9D%B4%EC%A7%80-Dockerfile%EA%B3%BC-Compose-%EC%84%9C%EB%B9%84%EC%8A%A4-%EC%9D%98%EC%A1%B4-p87zpfk2
[d32]: https://velog.io/@snowmile1224/Spring-Study-Day-32-CI-%ED%8C%8C%EC%9D%B4%ED%94%84%EB%9D%BC%EC%9D%B8%EC%9D%98-%EC%8B%A4%EC%A0%9C-%EC%8B%A4%ED%96%89-%EA%B2%80%EC%A6%9D-GitHub-Actions-Job-%EC%9D%98%EC%A1%B4%EA%B3%BC-MySQL-1064-%EC%98%A4%EB%A5%98-81x13h5v
[d33]: https://velog.io/@snowmile1224/Spring-Study-Day-33-%EC%98%88%EC%95%BD-%EC%8B%9C%EA%B0%84%EB%8C%80-%EC%A4%91%EB%B3%B5-%EB%B0%A9%EC%A7%80-%EA%B5%AC%EA%B0%84-%EA%B2%B9%EC%B9%A8-%ED%8C%90%EC%A0%95%EA%B3%BC-%ED%95%98%EC%9C%84-%ED%98%B8%ED%99%98-%ED%99%95%EC%9E%A5
[d34]: https://velog.io/@snowmile1224/Spring-Study-Day-34-Day-35-5%EC%A3%BC%EC%B0%A8-%EB%A7%88%EB%AC%B4%EB%A6%AC-%EC%8B%9C%ED%97%98
[retro]: https://velog.io/@snowmile1224/Spring-Study%EB%A5%BC-%EB%A7%88%EC%B9%98%EB%A9%B0-5%EC%A3%BC-%EB%B0%B1%EC%97%94%EB%93%9C-%EA%B8%B0%EB%B3%B8%EA%B8%B0-%ED%8A%B8%EB%9E%99-%ED%9A%8C%EA%B3%A0%EC%99%80-%EB%8B%A4%EC%9D%8C-%EB%AA%A9%ED%91%9C-mx5am4xr

### 남은 한계 6건 후속 구현 (2026-11-08)

M9, M10 테스트 작성, id 우선 dedupe/기존 중복 정리, DB throttle/sync lease, 신뢰 프록시/학번 정규화, 재인증 claim의 연결 변경 대기를 구현했습니다. [ADR-0009](adr/0009-distributed-auth-sync.md)와 `docs/reviews/codex-remaining-fixes.md`에 결정·검증·한계를 기록합니다. 기존 (F)/(G) 표는 당시 검증 이력이며 현재 처리는 이 후속 기록이 우선합니다.

남은 한계: 원래 목록 픽스처는 KJKEY만 있어 원문 항목 id가 없습니다. 합성 item/seq/id 링크 파싱은 테스트했으나 실학교 HTML은 확인하지 않았습니다. id가 없는 경우 일대일·날짜 휴리스틱은 남고 모호한 후보는 보존합니다. 기존 비정규 Unicode 학번은 자동 이관하지 않습니다. 실학교·여러 프로세스 부하·서버 시계 차이·JAR 47건 스모크는 실행하지 않았습니다. PostgreSQL 테스트 4건은 CI(PR #1)에서 실행·통과했습니다.

최종 전체 결과(2026-11-08): **163건 중 159건 통과, 실패 0, 오류 0, 4건 skipped**. tests=163/failures=0/errors=0/skipped=4. PG 빈 DB·업그레이드 각 2건은 로컬 Docker 부재로 skipped, CI(PR #1)에서는 실행·통과. 명령: `GRADLE_USER_HOME=C:/Users/jinwoo/.gradle sh gradlew cleanTest test --console=plain`(Git Bash, backend/).
