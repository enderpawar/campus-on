# 백엔드 코드 리뷰

## 범위와 검증 제한

- 대상: `backend/`의 애플리케이션 코드·설정·마이그레이션·`src/test`·Docker 구성·문서 및 `.github/workflows/backend-ci.yml`.
- 기준: 명세 v0.1의 F1·F3·F6. 이번 요청에서 지정한 이메일 인증, Access/Refresh JWT, `token_version` 로그아웃, 일정 API·ICS 구독, 공지 조회를 구현 범위로 해석했다. Google 로그인·LMS 워커·공지 수집기·푸시·AI의 부재를 이번 구현의 신규 결함으로 계산하지 않았다.
- **정적 리뷰만 수행했다. git 명령, 빌드, 테스트, 애플리케이션 실행, DB 접속은 하지 않았다.** 이 파일 외 저장소 파일을 수정하지 않았다.
- 라이브러리 동작 두 곳은 로컬 캐시 JAR에 `javap -c`를 사용해 대조했다. Hibernate Validator 8.0.3.Final의 `NotBlankValidator`는 `String.trim()`을 호출하고, Spring Data JPA 3.5.13의 `PageableUtils.getOffsetAsInteger`는 int 범위를 넘는 offset에 `InvalidDataAccessApiUsageException`을 던진다. 이는 디스어셈블 결과이며 테스트 실행 결과가 아니다.
- 아래 `[INFERENCE]` 시나리오는 코드 및 라이브러리 계약에서 도출한 동작이다. 실행 재현·통과를 주장하지 않는다. 파일 경로는 저장소 루트 기준이다.

## 지적 사항

### 1. 로그아웃의 버전 증가가 원자적이지 않아 폐기한 토큰을 다시 살릴 수 있음

- **심각도: High**
- **파일:줄:** `backend/src/main/java/com/campuson/api/auth/AuthService.java:80-85`; `backend/src/main/java/com/campuson/api/member/Member.java:43-44,62-65`; `backend/src/main/java/com/campuson/api/member/MemberRepository.java:9-20`
- **근거:** 로그아웃은 일반 조회로 엔티티를 읽고 메모리의 `tokenVersion++`를 dirty checking으로 저장한다. 원자적 증가 쿼리, 잠금 조회, 낙관적 잠금 필드가 없다. `@Transactional`만으로 읽기-수정-쓰기 경합을 막지는 못한다.
  - `[INFERENCE]` A·B 로그아웃이 모두 버전 0을 읽는다. A가 1로 커밋한 뒤 L이 로그인해 버전 1 토큰을 받는다. L의 로그아웃 C가 2로 커밋하면 그 토큰은 무효다. 그러나 지연된 B가 메모리에 남은 값 1을 커밋하면 DB 버전이 2에서 1로 내려가고, L의 이미 폐기된 access·refresh가 다시 유효해진다. 필터와 refresh는 현재 DB 값과의 동등 비교만 한다(`JwtAuthenticationFilter.java:37-39`, `AuthService.java:74-77`).
  - 단순히 증가 횟수 하나를 잃는 문제가 아니라, 취소 상태의 단조성이 깨지는 보안 문제다. README 49행과 ADR-0005 12·32행의 전체 토큰 무효화 보장을 충족하지 못한다. `AuthFlowHttpTest.java:78-90`은 순차 로그아웃만 검사한다.
- **제안:** 서비스 트랜잭션 안에서 `UPDATE member SET token_version = token_version + 1 WHERE id = ?`와 같은 DB 원자적 증가를 사용하고 영향 행 수를 확인한다. 또는 회원 행을 쓰기 잠금으로 조회한다. PostgreSQL에서 위 순서의 교차 실행을 제어하는 회귀 테스트로 버전이 감소하지 않고 취소된 access·refresh가 부활하지 않는지 검증한다. 변경 시 dirty checking을 예제로 든 ADR-0004·README도 함께 수정해야 한다.

### 2. ICS 자격 증명 마스킹이 원문 경로의 한 가지 표기에만 적용됨

- **심각도: Medium**
- **파일:줄:** `backend/src/main/java/com/campuson/api/global/logging/RequestIdFilter.java:40,45-47`; `backend/src/main/java/com/campuson/api/global/error/ApiProblems.java:17-20`
- **근거:** 접근 로그는 `getRequestURI()`가 정확히 `/ics/`로 시작할 때만 가린다. 오류 응답의 `instance`에는 같은 원문 URI를 아무 마스킹 없이 넣는다.
  - `[INFERENCE]` `/ %69cs/`가 아니라 공백 없는 `/%69cs/<유효한 토큰>.ics`처럼 접두사의 unreserved 문자를 percent-encoding한 요청은 `startsWith("/ics/")`에 걸리지 않아 토큰이 접근 로그에 남는다. 해당 요청이 최종적으로 허용되는지와 무관하게 필터까지 도달한 원문 경로의 로그 분기는 같다.
  - `[INFERENCE]` 유효한 ICS URL에 인증 없이 POST하면 GET만 공개한 보안 규칙(`SecurityConfig.java:34-37`)에 따라 401 경로를 타며, `ProblemAuthenticationEntryPoint.java:33-34`가 만든 `instance`에 여전히 유효한 토큰이 들어간다. 회전된 URL의 404도 같은 방식으로 원문을 반환한다. 응답 본문까지 수집하는 클라이언트 진단·지원 도구로 자격 증명이 복제될 수 있다.
  - 이것을 다른 사용자가 응답만으로 토큰을 알아내는 인증 우회로 주장하는 것은 아니다. 문제는 민감 경로의 마스킹 누락과 자격 증명 복제 경로다. ADR-0008 7·14행 및 README 82행의 비기록 취지에 비해 방어 범위가 좁다.
- **제안:** 원문 URI의 단순 접두사 검사 대신, 안전하게 해석한 애플리케이션 경로를 기준으로 ICS 요청을 식별하고 로그에는 토큰 없는 고정 경로를 사용한다. ProblemDetail의 `instance`도 ICS 경로에서는 토큰 없는 값으로 만든다. 정상·percent-encoded 접두사·잘못된 메서드·404 경로에서 로그와 오류 본문에 실제 토큰이 남지 않는지 검증한다. 캐시는 별개로, 현재 Spring Security 기본 헤더와 ICS의 `no-store`를 유지한다.

### 3. 지원하지 않는 Content-Type이 415가 아니라 500으로 변환됨

- **심각도: Medium**
- **파일:줄:** `backend/src/main/java/com/campuson/api/global/error/GlobalExceptionHandler.java:87-106`; `backend/src/main/java/com/campuson/api/auth/AuthController.java:24-36`
- **근거:** `HttpRequestMethodNotSupportedException`은 처리하지만 `HttpMediaTypeNotSupportedException`에 대한 처리는 없다. 마지막 `@ExceptionHandler(Exception.class)`가 이를 `INTERNAL_ERROR` 500으로 바꾼다.
  - `[INFERENCE]` `/api/auth/login`에 `Content-Type: text/plain`과 본문을 보내면 DTO를 읽을 메시지 컨버터가 없어 415 예외가 발생하고, 현재 advice가 500 및 error 로그로 바꾼다. 동일한 요청 본문 구조를 쓰는 signup·refresh·일정 쓰기 API도 영향을 받는다.
  - ProblemDetail이라는 모양만 유지할 뿐 클라이언트 오류가 서버 장애로 잘못 분류된다. 405 분기도 지원 HTTP 메서드를 알리는 `Allow` 헤더를 보존하지 않는다. README 67·115행과 ADR-0006의 통합 오류 정책에는 HTTP 상태·헤더 보존까지 필요하다.
- **제안:** 표준 MVC 예외는 `ResponseEntityExceptionHandler` 등을 통해 원래 상태와 필수 헤더를 유지하고 공통 `code`·`requestId`를 추가하거나, 해당 예외별 핸들러를 추가한다. 적어도 잘못된 Content-Type은 415, 잘못된 메서드는 405와 `Allow`를 반환하는지 회귀 테스트로 확인한다.

### 4. 공지의 큰 페이지 번호가 서버 오류를 발생시킴

- **심각도: Low**
- **파일:줄:** `backend/src/main/java/com/campuson/api/notice/NoticeController.java:26-30`; `backend/src/main/java/com/campuson/api/notice/NoticeService.java:22-27`; `backend/src/main/java/com/campuson/api/global/error/GlobalExceptionHandler.java:103-106`
- **근거:** `page`에는 하한만 있다. `size`가 50일 때 허용되는 `page=50000000`은 offset 2,500,000,000을 만든다. Spring Data JPA의 `PageableUtils.getOffsetAsInteger`가 `Integer.MAX_VALUE` 초과를 거부하는 것은 캐시 JAR의 바이트코드에서 확인했다.
  - `[INFERENCE]` 이 예외는 현재 공통 핸들러에서 500 `INTERNAL_ERROR`로 변환된다. 데이터가 적거나 없는 경우에도 사용자가 지정할 수 있는 숫자 범위가 JPA offset 범위와 맞지 않는다.
  - `NoticeHttpTest.java:96-100`은 size 상한만 검사한다.
- **제안:** `(long) page * size`가 JPA offset 범위를 넘지 않도록 요청 단계에서 검사해 400을 반환한다. 허용할 최대 페이지·offset을 API 계약에 명시하고 경계 직전·초과를 검증한다.

### 5. Unicode 공백 정규화와 입력 검증의 기준이 달라 빈 이름·제목이 DB까지 도달함

- **심각도: Low**
- **파일:줄:** `backend/src/main/java/com/campuson/api/auth/SignupRequest.java:15`; `backend/src/main/java/com/campuson/api/auth/AuthService.java:56-57`; `backend/src/main/java/com/campuson/api/schedule/CreateEventRequest.java:9`; `backend/src/main/java/com/campuson/api/schedule/UpdateEventRequest.java:10`; `backend/src/main/java/com/campuson/api/schedule/EventService.java:52,60`
- **근거:** 입력은 `@NotBlank`로 검사하지만 저장 직전에 `String.strip()`을 사용한다. 설치된 Hibernate Validator의 `NotBlankValidator`가 `String.trim()`으로 검사하는 것은 바이트코드에서 확인했다. `trim()`과 `strip()`의 Unicode 공백 처리는 다르다.
  - `[INFERENCE]` 이름이나 제목을 전각 공백 U+3000만으로 보내면 Bean Validation을 통과한 뒤 `strip()` 결과가 빈 문자열이 된다. DB의 `ck_member_name_not_blank` 또는 `ck_event_title_not_blank`에 걸려 요청 값 오류가 400 대신 409 `DATA_CONFLICT`로 안내된다(`V1__provisional_schema.sql:21,39`, `GlobalExceptionHandler.java:96-99`). 동시성 충돌이 아니며 같은 입력의 재시도로 해결되지 않는다.
- **제안:** 정규화한 값으로 필수·길이 검증을 수행하고 빈 값이면 저장 전에 `VALIDATION_FAILED`로 거부한다. 회원가입과 일정 생성·수정에 같은 기준을 적용한다. U+3000 같은 Unicode 공백 입력을 검증 경계 테스트에 포함한다.

### 6. 양 끝 날짜를 포함하므로 최대 62일 계약이 실제로는 63일임

- **심각도: Low**
- **파일:줄:** `backend/src/main/java/com/campuson/api/schedule/EventService.java:32-42`; `backend/README.md:51`
- **근거:** 상한은 `DAYS.between(from, to) > 62`로 검사하지만 쿼리의 끝은 `to.plusDays(1)`이어서 `to` 날짜까지 포함한다.
  - `[INFERENCE]` `from=2026-11-01`, `to=2027-01-02`는 날짜 차가 62라 통과하지만 실제 조회 구간은 63일이다. 코드의 오류 메시지와 README는 최대 62일이라고 한다. `EventHttpTest.java:98-106`은 역순만 검사하고 상한 경계는 검사하지 않는다.
- **제안:** 포함 일수 `DAYS.between(from, to) + 1`로 제한하거나 문서의 계약을 명확히 변경한다. 62일은 허용하고 63일은 거부하는 경계 테스트를 둔다.

### 7. Bearer 스킴의 대소문자를 구분해 표준상 유효한 인증 요청을 거부함

- **심각도: Low**
- **파일:줄:** `backend/src/main/java/com/campuson/api/global/security/JwtAuthenticationFilter.java:22,35-37`
- **근거:** 헤더가 정확히 `Bearer `로 시작해야만 토큰을 읽는다. HTTP 인증 스킴 이름은 대소문자를 구분하지 않는다.
  - `[INFERENCE]` 같은 정상 access 토큰도 `Authorization: bearer <token>` 또는 `BEARER <token>`이면 검증에 진입하지 않고 보호 API에서 401이 된다. 토큰 자체의 유효성과 무관한 상호운용성 문제다. 테스트 도우미는 항상 `Bearer `만 만든다(`ApiTestClient.java:59-60`).
- **제안:** 스킴 이름은 대소문자 구분 없이 비교하되 토큰 값은 원문 그대로 검증한다. 정상·대소문자 변형·다른 스킴을 HTTP 수준에서 검증한다.

### 8. 보호 API의 401 응답에 인증 challenge가 없음

- **심각도: Low**
- **파일:줄:** `backend/src/main/java/com/campuson/api/global/security/ProblemAuthenticationEntryPoint.java:29-34`; `backend/src/test/java/com/campuson/api/auth/AuthFlowHttpTest.java:105-114`
- **근거:** EntryPoint는 상태·콘텐츠 타입·본문만 설정하고 `WWW-Authenticate`를 설정하지 않는다. HTTP 401은 적용 가능한 인증 challenge를 포함해야 한다. 현재 테스트도 401 상태·ProblemDetail·requestId만 검사한다.
- **제안:** 보호 API의 EntryPoint에 `WWW-Authenticate: Bearer`를 추가하고, 무효 토큰처럼 구분 가능한 경우에는 적절한 `invalid_token` challenge를 반환한다. 본문의 기존 `UNAUTHORIZED` 및 requestId 형식은 유지하면서 헤더도 검증한다.

### 9. 현재 테스트는 일정 변경·삭제 및 동일 ICS URL의 갱신·회원 격리를 입증하지 않음

- **심각도: Low**
- **파일:줄:** `backend/src/test/java/com/campuson/api/schedule/EventHttpTest.java:53-130`; `backend/src/test/java/com/campuson/api/schedule/IcsHttpTest.java:36-81`
- **근거:** 일정 HTTP 테스트에는 PUT·DELETE 요청이 없다. 소유권 404는 PATCH `/done` 한 경로에서만 검사한다. ICS 테스트는 생성 직후의 출력, 토큰 회전, 알 수 없는 토큰만 검사한다.
  - PUT이 기존 값을 그대로 반환하거나 DELETE가 아무 일도 하지 않는 회귀를 현재 일정 테스트는 검출하지 못한다.
  - 같은 ICS URL에서 일정 수정·삭제가 다음 조회에 반영되는지, 다른 회원의 일정이 제외되는지 검사하지 않는다. 피드를 최초 결과로 고정하거나 회원 조건을 누락하는 회귀를 현재 ICS 테스트는 검출하지 못한다. F3의 핵심은 파일 한 번 생성이 아니라 같은 URL을 통한 지속 갱신이다.
  - 실제로 PUT·DELETE·피드 갱신이 현재 고장났다고 주장하는 지적은 아니다. 검증 범위에 대한 지적이며, 정상 구현의 정적 근거는 서비스와 저장소에 존재한다.
- **제안:** PUT 후 별도 조회로 값 교체·선택 필드 null 처리를, DELETE 후 별도 조회로 제거를 확인한다. 동일 피드 URL에서 수정·삭제 전후 결과를 확인하고 두 회원의 고유 일정을 함께 만들어 교차 노출이 없는지 검사한다. 남의 일정에 대한 PUT·DELETE는 404이며 원본이 바뀌지 않는지도 확인한다. 이 테스트들을 기존 통합 테스트 관례에 맞춰 추가한다.

### 10. HS256 고정이라는 문서와 실제 알고리즘 선택이 다름

- **심각도: Low**
- **파일:줄:** `backend/docs/adr/0005-jwt-access-refresh-token-version.md:10`; `backend/README.md:120`; `backend/src/main/java/com/campuson/api/global/security/JwtProvider.java:17,34,72`
- **근거:** 문서와 주석은 HS256이라고 하지만 구현은 `Keys.hmacShaKeyFor(secretBytes)` 및 알고리즘 인자 없는 `signWith(key)`를 사용한다.
  - `[INFERENCE]` jjwt는 키 길이에 맞춰 알고리즘을 선택하므로 운영 secret이 48바이트 이상이면 HS384, 64바이트 이상이면 HS512를 사용할 수 있다. README 36행은 32바이트 이상만 요구하며 상한은 없다. 더 강한 알고리즘을 사용하는 것이 취약점이라는 뜻은 아니고, 외부 검증기 설정에 사용할 문서 계약이 정확하지 않다는 뜻이다.
- **제안:** HS256이 확정 계약이면 발급 알고리즘을 명시하고 검증 정책도 일치시킨다. 고정할 의도가 없다면 ADR·README·주석을 키 길이에 따른 HMAC 알고리즘 선택으로 바로잡는다.

### 11. ADR이 원본 F1에 없는 보안 요구를 명세의 직접 요구로 인용함

- **심각도: Low**
- **파일:줄:** `backend/docs/adr/0005-jwt-access-refresh-token-version.md:7`; `캠퍼스 올인원 플랫폼 구현 명세 v0.1.md:39-43`
- **근거:** ADR은 “명세 F1은 로그아웃 시 토큰을 즉시 무효화하라고 요구한다”고 적는다. 원본 F1에는 Google·간편 로그인, 별도의 학교 계정 연동, 프로필만 있고 로그아웃·JWT·token_version 요구는 없다. 이번 사용자 요청과 README 5행에는 해당 구현 목표가 명시되어 있으므로 보안 기능 자체가 불필요하다는 뜻은 아니다. 원본 명세와 이번 백엔드의 구체화된 계약을 구분해야 한다.
- **제안:** 즉시 무효화를 이번 구현에서 추가한 보안 요구로 표기하고 원본 F1과 연결되는 결정임을 명시한다. 미구현 Google 로그인은 기술 부채로 관리 중이라는 기존 설명과 함께 범위의 출처를 일관되게 유지한다.

## 기준별 대조 결과와 남은 제한

### F1·F3·F6 및 보안

- **F1:** 이메일 정규화·BCrypt, 두 토큰 발급, 요청마다 DB `token_version` 대조, refresh에서의 `typ`·버전 대조 경로가 존재한다. `JwtProvider.java:46-60`은 서명 파싱과 주입 Clock을 통한 만료 검증 뒤 `typ`·`ver`·subject를 확인한다. 단일 로그아웃 뒤 기존 access·refresh 거부를 검증하는 테스트도 존재한다. 다만 동시 로그아웃 보장은 지적 1 때문에 불충분하다.
- **refresh의 이전 토큰은 갱신 후에도 유효하다.** `AuthService.java:70-77`은 읽기 전용이며 서버 상태를 갱신하지 않는다. 새 토큰 쌍을 발급해도 이전 refresh를 폐기하지 않고, 같은 버전이면 계속 갱신할 수 있다. ADR-0005 34-35행과 부채 원장 37행에 이미 공개한 설계 부채로서 신규 발견이나 숨겨진 문서 불일치로 계산하지 않았다. 일회용 refresh 회전·재사용 탐지가 완료된 것으로 해석하면 안 된다. 정상적인 단일 로그아웃 뒤에는 이전 refresh도 버전 검사로 거부된다.
- **JWT 추가 하드닝:** `exp` 존재를 직접 요구하지는 않는다. 현재 발급 경로는 항상 만료를 넣으므로, 이를 즉시 악용 가능한 무기한 토큰 발급 취약점으로 계산하지 않았다. 공유 서명키를 사용하는 다른 발급자를 향후 도입한다면 필수 클레임과 알고리즘 정책을 명시할 필요가 있다.
- **F3:** 개인 일정 생성·수정·삭제·완료 처리, 마감일 기준 기간 조회 및 D-day, 토큰 재발급과 ICS 출력 경로가 존재한다. `EventService.java:81-83`의 소유자 조건 조회를 수정·삭제·완료에 공통 사용한다. ICS는 32바이트 SecureRandom 토큰을 발급하고, 토큰으로 회원을 찾은 뒤 회원 조건으로 일정을 조회한다. 출력의 CRLF·TEXT escaping·UTF-8 folding 구현과 관련 단위 테스트를 대조했으며 이 부분의 구체적인 오류는 확인하지 못했다.
- **ICS 캐시:** 성공 응답에는 명시적인 `Cache-Control: no-store`가 있다(`IcsController.java:28-31`). 보안 설정은 Spring Security 기본 캐시 방지 헤더를 끄지 않았으므로, `/api/calendar-feed` JSON에 컨트롤러가 별도 헤더를 넣지 않았다는 이유만으로 캐시 누출로 지적하지 않았다. 현재 HTTP 테스트는 성공 ICS의 `no-store`만 검사한다. 프록시·배포 플랫폼 로그 정책은 저장소에서 확인할 수 없어 안전하다고 보증하지 않는다.
- **F6:** 학교는 요청 파라미터가 아니라 인증된 회원의 학교에서 가져온다. 목록·카테고리 조회·상세 모두 저장소 조건에 학교 ID가 들어가고, 다른 학교의 상세는 404다. 최신순은 `postedAt DESC, id DESC`로 안정화한다. 공지의 한 줄 요약·학년 값은 응답할 수 있으나 생성하는 수집기·AI는 이번 조회 범위 밖이다.

### 트랜잭션·오류·테스트

- `application.yml:7`에서 OSIV가 꺼져 있다. 서비스의 쓰기 메서드에는 `@Transactional`, 조회에는 `readOnly = true`가 있고 DTO 생성은 그 경계 안에서 수행한다. 회원 응답은 학교 fetch join 또는 트랜잭션 안의 접근으로 만든다. 일정 수정·완료·피드 회전의 dirty checking 자체는 타당하다. 이를 로그아웃의 동시성 보장과 혼동해서는 안 된다.
- 오류 생성은 `ApiProblems`를 공유하고, 401·403 핸들러는 ObjectMapper를 사용한다. 일반적인 검증·업무 예외의 형식 일관성은 코드에서 확인했다. 단, 표준 HTTP 예외의 의미·헤더는 지적 3·8의 보완이 필요하다. 인증 필터의 DB 조회는 MVC advice 바깥이므로 필터 장애까지 “모든 오류가 공통 ProblemDetail”이라고 보증할 근거도 부족하다.
- 통합 테스트는 실제 서비스·JPA 경로를 사용하고 HTTP 테스트 메서드에 테스트용 `@Transactional`이 없다. `markDonePersistsWithoutExplicitSave`는 PATCH 뒤 GET으로 커밋된 변경을 확인하므로 단순 mock echo가 아니다. JWT는 Clock을 움직여 검증한다. Slice 테스트가 필터를 끄는 한계는 ADR과 테스트 주석에 명시되어 있다.
- 403 전용 테스트가 없고 현재 역할 규칙으로 도달하는 요청도 없다는 점은 부채 원장 45행에 이미 기록되어 있다. 이번 리뷰에서 403 핸들러가 실제 실행되었다고 주장하지 않는다. 공지 테스트는 정렬된 실제 ID 순서와 마지막 페이지를 검증하지 않으며, PostgreSQL 테스트는 스키마 기동·CHECK 중심이지 F1·F3·F6 HTTP 동작 전체를 검증하지 않는다.

### CI·문서

- `.github/workflows/backend-ci.yml:14-28`은 Java 21에서 테스트를 실행하도록 구성하고, 30-49행의 Docker 잡은 테스트 잡 성공 후 Compose 기동과 health endpoint를 확인한다. 테스트 실패를 무시하는 설정은 확인하지 못했다. Docker health 확인을 인증·일정·공지·ICS 시나리오의 운영 DB 통합 검증으로 간주해서는 안 된다.
- `PostgresSchemaTest.java:24`는 Docker가 없으면 skip하도록 되어 있다. CI에 Docker가 존재한다는 주석만으로 두 테스트가 실제 실행됐다고 확정할 수는 없다. 이번 검토에서는 CI 실행 결과에 접근하거나 워크플로를 실행하지 않았다.
- Test 태스크의 `SPRING_*` 환경변수 제거(`build.gradle.kts:41-46`)와 `bootRun`에는 같은 보호가 없다는 문서 설명은 일치한다. 이번에는 그 보호를 신뢰해 테스트를 실행하지 않고, 요청대로 실행 자체를 피했다.
- ADR-0001·0002의 기능 우선 패키지·Spring Data 직접 의존, ADR-0003의 Flyway 소유·validate, ADR-0004의 서비스 경계·DTO 변환은 코드와 대체로 일치한다. 로그아웃의 동시성, 마스킹 범위, HTTP 오류 의미 및 위에 별도 기록한 문서 불일치는 예외다.
- README·ADR·부채 원장에 기록된 과거 “32건 중 30건 통과·2건 skipped” 및 스모크 성공은 이번 리뷰에서 재검증한 결과가 아니다. PostgreSQL·Docker·CI를 미검증으로 명시한 문구는 그대로 구분했다.

REVIEW-DONE
