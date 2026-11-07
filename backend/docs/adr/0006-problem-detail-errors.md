# ADR-0006 ProblemDetail + ErrorCode 단일 예외

## 상태
채택

## 맥락
스터디 코드의 오류 본문은 세 종류였다: 필드 Map, `{"errors":[]}`, `{"error":""}`. 기계가 읽을 code와 requestId가 없었고, 같은 필드에 위반이 둘이면 Map에서 하나가 다른 하나를 덮어썼다. Day 27~28에서는 401/403 JSON을 문자열 이어 붙이기로 만들었다. 메시지에 따옴표가 들어가면 깨지는 구조였다. ProblemDetail(RFC 9457)은 Day 7에서 후보로만 적어 두었다.

## 결정
1. **형식:** 모든 오류 응답은 `application/problem+json`의 ProblemDetail이다. `ApiProblems.of(code, detail, request, errors)`가 유일한 생성 지점이다. 표준 필드(`type`, `title`, `status`, `detail`, `instance`)에 확장 필드 `code`, `requestId`, `timestamp`를 넣고, 필드 오류가 있을 때만 `errors`(`FieldErrorItem(field, message)` 목록)를 넣는다.
2. **예외 하나:** `BusinessException(ErrorCode)` / `BusinessException(ErrorCode, String detail)`를 쓴다. HTTP 상태와 기본 메시지는 `ErrorCode` enum이 가진다. 이메일 중복 오류는 가입 제거와 함께 삭제하고, 학교 세션 만료/연결 실패 코드를 추가했다. Claude 리뷰(H3) 반영으로 `ECLASS_SYNC_IN_PROGRESS`(409, "e-class 동기화가 이미 진행 중입니다.")와 `TOO_MANY_REQUESTS`(429)를 더해 현재 22개다.
   - **429와 `Retry-After`:** 다시 시도할 시각이 필요한 거절은 `BusinessException`의 하위 `global/error/RateLimitedException`으로 던지고, `GlobalExceptionHandler.handleRateLimited`가 같은 ProblemDetail 본문에 `Retry-After` 헤더(초)를 붙인다. 로그인·탈퇴 실패 한도와 수동 동기화 쿨다운이 이 경로를 쓴다.
3. **전역 핸들러:** `GlobalExceptionHandler`가 도메인·검증·없는 경로·405/415·무결성·예상 못 한 예외를 같은 형식으로 바꾼다. 405는 `Allow`, 415는 `Accept`를 제공한다. 내부 예외 메시지는 노출하지 않는다. `AuthFlowHttpTest`가 405/415와 인증 입력 실패를 검증한다.
   - **남은 공백:** `ResponseEntityExceptionHandler`를 상속하지 않으므로, 따로 잡지 않은 Spring MVC 표준 예외(예: 406 `HttpMediaTypeNotAcceptableException`)는 아직 500 `INTERNAL_ERROR`로 떨어진다. [부채 원장](../tech-debt-ledger.md)의 "나중에 고칠 것"이다.
4. **필드 오류를 모두, 정렬해서:** `MethodArgumentNotValidException`의 `getFieldErrors()` 전부를 목록에 넣고(global error는 field `_global`) field → message 순으로 정렬한다. Map이 아니라 목록이라 같은 필드의 위반이 덮어쓰이지 않고, 순서가 고정돼 테스트가 안정적이다. `HandlerMethodValidationException`(경로·쿼리 파라미터의 `@Positive`, `@Max` 등)도 같은 방식이다.
5. **보안 핸들러도 같은 형식:** `ProblemAuthenticationEntryPoint`(401)와 `ProblemAccessDeniedHandler`(403)는 `ApiProblems.of(...)`로 만든 객체를 `ObjectMapper.writeValue`로 직렬화한다. 문자열을 이어 붙이지 않는다.

현재 로컬 HTTP 스모크의 실제 401 응답 예시는 [README 오류·로그](../../README.md#오류로그)에 기록한다. 잘못된 학교 비밀번호는 `INVALID_CREDENTIALS`, 만료된 학교 세션은 `ECLASS_REAUTH_REQUIRED`, 학교 응답/연결 실패는 `ECLASS_UNAVAILABLE`이다. 학교 원문 진단이나 입력 비밀번호를 detail로 그대로 전달하지 않는다.

## 스터디 근거
- [Day 3](https://velog.io/@snowmile1224/Spring-Study-Day-3-Input-Validation%EA%B3%BC-Global-Exception-Handling-Valid%EC%99%80-RestControllerAdvice-ilzw3bx3): `@Valid`와 `@RestControllerAdvice`. 필드 위반 덮어쓰기 부채의 출처.
- [Day 7](https://velog.io/@snowmile1224/Spring-Study-Day-7-1%EC%A3%BC%EC%B0%A8-%EB%B2%84%ED%8D%BC-%EC%A0%80%EC%9E%A5-%EA%B3%84%EC%95%BD%EA%B3%BC-Absence-of-Value%EC%9D%98-%EC%98%A4%EB%A5%98-%EA%B2%BD%EA%B3%84): Optional → 예외 → 404 경계, 내부 메시지 비노출, ProblemDetail 후보.
- [Day 27~28](https://velog.io/@snowmile1224/Spring-Study-Day-27-Day-28-4%EC%A3%BC%EC%B0%A8-%EB%A7%88%EB%AC%B4%EB%A6%AC-%EC%8B%9C%ED%97%98-y03atpnv): 401/403 JSON 문자열 이어 붙이기, 400/404/409 형식 불일치.

## 검증
- `EventControllerWebMvcTest.invalidCreateRequestReturns400WithAllFieldErrorsAndNeverCallsService`: `dueAt`, `title` 순서로 2건.
- `AuthFlowHttpTest.missingOrMalformedTokensHaveConsistentProblems`: 401 ProblemDetail, request id, `WWW-Authenticate`.
- `EventHttpTest.otherMembersEventReturns404`, `NoticeHttpTest.sizeAbove50Returns400`: 소유권·파라미터 오류의 공통 형식.
- `AuthFlowHttpTest.repeatedWrongPasswordsStopReachingTheSchool`, `ManualSyncCooldownHttpTest.manualSyncRightAfterLoginIsRejectedWithRetryAfter`: 429 `TOO_MANY_REQUESTS`와 `Retry-After`.

## 결과와 트레이드오프
- 클라이언트는 `status`가 아니라 `code`로 분기할 수 있다(예: 401 중 `INVALID_CREDENTIALS`와 `INVALID_REFRESH_TOKEN` 구분).
- `type`은 아직 `about:blank`다. 오류 문서 URL을 둘지는 정하지 않았다.
- 인증 재연결처럼 분기가 필요한 경계에서는 `BusinessException.code()`로 명시적으로 구분한다(`EclassSyncService`의 세션 만료 처리). 실패한 저장 트랜잭션은 밖에서 처리한다.
