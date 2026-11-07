# ADR-0008 Request ID와 민감정보 비기록

## 상태
채택

## 맥락
오류 응답을 받은 사용자가 문의하면, 그 요청의 서버 로그를 바로 찾을 수 있어야 한다. 동시에 로그에는 비밀번호, 토큰, ICS 구독 주소처럼 그 자체가 자격 증명인 값이 남으면 안 된다. 스터디 Day 29에서는 Authorization 헤더를 마스킹했는데, 마스킹하면서 스킴(`Bearer`)까지 버려 디버깅에도 쓸모가 없었다.

## 결정
1. **`RequestIdFilter`**(`global/logging`, `OncePerRequestFilter`)
   - 들어온 `X-Request-Id`가 `^[A-Za-z0-9-]{1,64}$`에 맞을 때만 재사용하고, 아니면 `UUID.randomUUID()`를 쓴다. 줄바꿈 등을 넣은 로그 인젝션을 막는다.
   - MDC 키 `requestId`에 넣고, 체인 실행 전에 응답 헤더 `X-Request-Id`를 설정한다. `ApiProblems`가 같은 MDC 값을 본문 `requestId`에 넣으므로 헤더와 본문이 같다.
   - `finally`에서 `메서드 경로 -> 상태 (ms)` 한 줄을 남기고 MDC를 지운다.
   - 경로 마스킹은 `SensitivePaths.mask(uri)`가 한다. URI를 먼저 URL 디코딩(`UriUtils`)하고 소문자로 바꾼 뒤 `/ics/`로 시작하는지 본다. 일치하면 `/ics/***.ics`, 디코딩할 수 없는 경로는 `/***`다. ICS 토큰은 인증 없이 일정을 읽는 열쇠다. 처음에는 원문 URI에 `startsWith("/ics/")`만 했는데, Codex 리뷰(#2)가 `/%69cs/<token>.ics`나 `/ICS/...` 같은 변형이 마스킹을 비켜 가고, 오류 본문 `instance`에도 토큰이 그대로 실린다고 지적했다.
   - 같은 `SensitivePaths.mask`를 `ApiProblems`의 `instance`에도 쓴다. 그래서 404 등 오류 본문도 토큰을 되돌려주지 않는다(`IcsHttpTest.errorBodyNeverEchoesFeedToken`, `IcsHttpTest.rotatedTokenInvalidatesOldUrl`의 `$.instance == "/ics/***.ics"`, `SensitivePathsTest` 3건).
   - Authorization 헤더와 쿼리 스트링은 기록하지 않는다. 마스킹 규칙을 두는 대신 아예 남기지 않는다.
2. **등록 위치:** `@Component`를 붙이지 않고 `LoggingConfig`의 `FilterRegistrationBean`으로 `Ordered.HIGHEST_PRECEDENCE`, `/*`에 등록한다. Spring Security 필터 체인보다 먼저 돌기 때문에 `ProblemAuthenticationEntryPoint`·`ProblemAccessDeniedHandler`가 만든 401/403에도 requestId가 붙는다. `@Component`가 아니므로 `@WebMvcTest`가 자동으로 끌어오지 않는다.
3. **로그 패턴:** `application.yml`의 `logging.pattern.console`에 `[%X{requestId}]`를 넣었다.
4. **e-class 수집 실패 로그(Claude 리뷰 M7, Codex 검증 F4):** 수집 실패 로그에는 memberId와 안전한 식별자만 남긴다. 예외 메시지·학교 응답 원문·쿠키·비밀번호는 남기지 않는다.
   - `BusinessException`(네트워크 오류가 바뀐 `ECLASS_UNAVAILABLE` 포함): memberId + ErrorCode. `ECLASS_REAUTH_REQUIRED`/`UNAUTHORIZED`는 일상적인 재로그인 요구라 `info`, 그 밖(`ECLASS_UNAVAILABLE`, `INVALID_CREDENTIALS` 등)은 `warn`.
   - 저장 비밀값 복호화 실패(`UnreadableSecretException`): memberId만 `warn`, 연결을 `REAUTH_REQUIRED`로 바꾸고 저장 비밀번호를 지운다.
   - 그 밖의 예상 못 한 RuntimeException: memberId + 예외 클래스 이름만 `warn`, `ECLASS_UNAVAILABLE`로 기록한다.
   - 재로그인·로그아웃으로 연결 세대가 바뀌어 버린 옛 결과(성공·실패·복호화 실패 모두): 위 규칙 대신 `info` 한 줄("결과를 버림")만 남긴다. 새 연결에는 아무것도 쓰지 않았으므로 경고가 아니다. `EclassSyncHttpTest`가 출력 로그를 캡처해 레벨과 비밀값 미노출을 확인한다.

실제 접근 로그(local 스모크, 발췌):

```
21:04:51.285 [http-nio-127.0.0.1-62964-exec-2] INFO  [1b6a0b1a-d435-433e-96f0-60f439de60c2] c.c.a.global.logging.RequestIdFilter - POST /api/auth/login -> 401 (130ms)
21:04:51.860 [http-nio-127.0.0.1-62964-exec-5] INFO  [0e8c6471-75c3-4ceb-8459-2e3d2fb5c023] c.c.a.global.logging.RequestIdFilter - GET /ics/***.ics -> 200 (8ms)
```

첫 줄의 requestId는 같은 요청의 401 응답 본문과 같다([README](../../README.md#오류로그)). 이번 스모크 로그에 학교 비밀번호·JWT·ICS 토큰 원문은 없었다. `AuthFlowHttpTest.missingOrMalformedTokensHaveConsistentProblems`는 응답 request id와 인증 오류 형식을 검증한다.

Codex 리뷰 반영 후 스모크에서 접두사를 percent-encoding한 `GET /%69cs/<token>.ics`(200)와 인증 없는 `POST /ics/<token>.ics`(401)를 보냈다. 두 요청 모두 로그에는 아래처럼 찍혔고, 로그 전체와 401 본문 어디에도 토큰 원문은 없었다.

```
INFO  [740bb8fa-2ef5-44c5-9fd9-79a417f99257] c.c.a.global.logging.RequestIdFilter - GET /ics/***.ics -> 200 (7ms)
INFO  [bf8c4b5e-3121-4633-bb53-c4674d1707fd] c.c.a.global.logging.RequestIdFilter - POST /ics/***.ics -> 401 (1ms)
```

## 설정 격리: 환경변수 사고
Phase 1의 첫 `bootRun`이 local H2가 아니라 외부 PostgreSQL(Neon)에 붙었다. 개발 PC 셸에 다른 프로젝트의 `SPRING_PROFILES_ACTIVE=prod`와 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`가 남아 있었기 때문이다. Spring Boot에서 OS 환경변수는 `application-*.yml`보다 우선한다. 그 DB에는 다른 테이블이 있었고 Flyway가 "non-empty schema but no history table"로 migrate 전에 멈춰서, 외부 DB에는 아무것도 쓰지 않았다.

스터디 Day 9에서 OS 환경변수가 yml을 덮어써 빌드가 깨진 경험과 같은 원인이다. 대응은 다음과 같다.
- `build.gradle.kts`의 Test 태스크는 `SPRING_`로 시작하는 환경변수를 모두 지우고 `SPRING_PROFILES_ACTIVE=test`만 넣는다. 테스트가 외부 DB에 붙는 경로를 막았다.
- prod 비밀값(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ECLASS_ENCRYPTION_KEY`, `PUBLIC_BASE_URL`)은 `application-prod.yml`에서 기본값 없이 환경변수로만 받는다. 값이 없으면 기동이 실패한다(fail-fast).
- `bootRun` 태스크도 `SPRING_`로 시작하는 환경변수를 지운다. `bootRun`은 로컬 개발 전용이고 운영 실행은 Docker 이미지(`SPRING_PROFILES_ACTIVE=prod`)가 맡기 때문이다. 다른 프로필이 필요하면 `--args='--spring.profiles.active=...'`로 명시한다. 외부 DB용 `SPRING_*`가 남은 셸에서 `gradlew bootRun`이 local H2로 기동하는 것을 확인했다.

## 스터디 근거
- [Day 29](https://velog.io/@snowmile1224/Spring-Study-Day-29-%EC%9A%94%EC%B2%AD-%EB%A1%9C%EA%B9%85%EA%B3%BC-%EC%84%A4%EC%A0%95-%EB%B6%84%EB%A6%AC-Request-IDMDC%EC%99%80-Profile%EB%B3%84-Secret-%EA%B4%80%EB%A6%AC-4qe9fhmb): Request ID·MDC, 프로필별 Secret 관리, Authorization 마스킹이 스킴을 버린 문제.
- Day 9(글 주소 없음): OS 환경변수가 yml 설정을 덮어쓴 경험.

## 결과와 트레이드오프
- 응답의 `requestId` 하나로 서버 로그 한 줄을 찾을 수 있다.
- 쿼리 스트링을 남기지 않으므로 `GET /api/events?from=...&to=...`의 기간은 로그에서 알 수 없다. 필요해지면 허용 목록 방식으로 남긴다.
- 서비스 간 전파(분산 추적)는 없다. 서버가 하나라 지금은 필요하지 않다.

## 2026-11-08 후속

클라이언트 주소는 신뢰 CIDR 정책의 `ClientAddressResolver`를 로그인·탈퇴에 공통 적용한다. 전달 헤더·학교 응답·비밀값을 추가로 로그에 남기지 않는다. DB 예약/claim 경합·고의 오류의 테스트 로그와 외부 API 429는 보고서에서 구별한다. 학교 HTTP 중 DB 트랜잭션을 유지하지 않는 원칙은 동일하다([ADR-0009](0009-distributed-auth-sync.md)).
