# ADR-0005 Access/Refresh JWT와 token_version 로그아웃

## 상태
채택

## 맥락
명세 F1 원문은 Google/간편 로그인, 학교 계정 연동, 프로필만 적고 있고 로그아웃 시 즉시 무효화는 요구하지 않는다. 즉시 무효화는 이 백엔드의 범위를 정하면서 사용자와 합의해 추가한 보안 요구사항이다. 순수 stateless JWT는 서버가 상태를 갖지 않으므로 발급한 토큰을 만료 전에 죽일 수 없다. 스터디 Day 25에서 이 한계를 확인했지만 해결하지 못했고, Day 34 시험에서는 stateless의 정의를 "만료 시간으로 보완하는 것"으로 잘못 답했다. stateless는 서버가 요청 사이의 인증 상태를 저장하지 않는다는 뜻이고, 짧은 만료는 그 한계를 줄이는 완화책일 뿐이다.

## 결정
1. **토큰 두 종류:** `JwtProvider.issue`가 `signWith(key, Jwts.SIG.HS256)`으로 알고리즘을 명시해 access(기본 15분, `campus.jwt.access-ttl`)와 refresh(기본 14일, `campus.jwt.refresh-ttl`)를 발급한다. `signWith(key)`만 쓰면 키 길이에 따라 HS384/HS512가 골라질 수 있어 명시했다(Codex 리뷰 #10). 클레임은 subject(회원 id), `typ`(`ACCESS`/`REFRESH`), `ver`(token_version), `auth_time`(실제 학교 비밀번호 인증 시각의 epoch seconds)다. refresh는 `auth_time`을 그대로 복사하며 새 `iat`로 갱신하지 않는다. 클레임 없는 기존 토큰도 검증하지만 장애 시 탈퇴 대체에는 쓸 수 없다.
2. **검증:** `JwtProvider.verify(token, expected)`는 서명·만료·`typ`·`ver`를 확인하고 실패하면 `Optional.empty()`다. refresh를 access 자리에 넣으면 거부된다(`JwtProviderTest.refreshTokenIsRejectedWhereAccessTokenIsExpected`).
3. **token_version 로그아웃:** `users.token_version`을 둔다. `AuthService.logout`은 `MemberRepository.increaseTokenVersion(id)`로 원자적 증가하며, 0행이면 `UNAUTHORIZED`다. `JwtAuthenticationFilter`와 `AuthService.refresh`는 `findAuthSnapshot`의 활성 계정·현재 버전과 JWT `ver`를 대조한다. 배경 프로필 갱신은 `@DynamicUpdate`로 버전을 덮어쓰지 않는다.
4. **401은 EntryPoint가 만든다.** `JwtAuthenticationFilter`는 `Authorization` 헤더의 `Bearer` 스킴을 대소문자 구분 없이 받는다(RFC 9110의 auth-scheme 규칙, Codex 리뷰 #7). 실패해도 응답을 쓰지 않고 `chain.doFilter`만 호출한다. 인증 없이 보호 경로에 도달하면 `ExceptionTranslationFilter`가 `ProblemAuthenticationEntryPoint`를 불러 401 ProblemDetail을 쓰고, `WWW-Authenticate: Bearer` 헤더를 붙인다(Codex 리뷰 #8). 필터가 401을 직접 쓰면 permitAll 경로까지 막히고 응답 형식이 둘로 갈린다.
5. **비밀키:** `JwtProvider` 생성자의 `Keys.hmacShaKeyFor`가 32바이트 미만 키를 `WeakKeyException`으로 거부해 기동이 실패한다(`JwtProviderTest.secretShorterThan32BytesFailsAtConstruction`). prod는 `JWT_SECRET`에 기본값이 없다.
6. **학교 인증:** 이메일 가입·로컬 BCrypt를 제거하고 e-class 학번·비밀번호로 인증한다. 성공 후 같은 학교 학번의 앱 계정을 자동 등록/재사용한다. 숨은 폼 필드·쿠키와 최종 인증 표식을 확인한다. 비밀번호는 기본 미저장, 명시적 `rememberCredentials=true`일 때만 세션 쿠키와 함께 독립 `ECLASS_ENCRYPTION_KEY` 기반 AES-256-GCM으로 저장한다. JWT 키/cron 키 fallback은 없다. 연결 해제·탈퇴는 연결 비밀도 삭제한다.
7. `CampusOnApiApplication`에서 `UserDetailsServiceAutoConfiguration`을 제외해 기본 in-memory 사용자와 "generated security password" 로그를 만들지 않는다.
8. **로그아웃은 학교 연결도 끊는다(Claude 리뷰 M8).** `AuthService.logout`은 `token_version` 증가와 같은 트랜잭션에서 그 회원의 `eclass_connections` 행(암호화 쿠키·저장 비밀번호)을 지운다. 그래서 로그아웃 뒤 백그라운드 수집이 멈추고, 다음 로그인에서 연결이 다시 만들어진다. 로그아웃하지 않은 장기 미사용 회원의 수집 중단 규칙은 아직 없다([부채 원장](../tech-debt-ledger.md) (B)).
9. **로그인 실패 제한.** `LoginAttemptThrottle`은 DB 공유 실패 창·만료 예약으로 학번당 기본 5회·클라이언트당 20회를 제한한다. 로그인과 탈퇴가 학번·IP 예산을 함께 사용한다. 성공은 그 학번의 실패만 지우고 클라이언트 실패와 진행 중 예약은 유지한다. 주소는 `ClientAddressResolver`가 명시한 신뢰 CIDR에서 온 XFF만 오른쪽부터 해석한다. 기본은 소켓 주소다. 학교 자체 잠금 기준은 미검증이다.
10. **진행 중 시도 예약(Codex 검증 F1).** 완료된 실패만 세면 학교 응답 전에 동시 요청이 모두 확인을 통과해 한도를 넘는다. 그래서 `LoginAttemptThrottle.begin(studentId, client)`가 슬롯을 예약한 `Attempt`를 돌려주고 try-with-resources로 쓴다. 입장 조건은 "완료 실패 + 진행 중 시도 < 한도"다. `failed()`는 실패로 계산, `succeeded()`는 그 학번 카운터 초기화, `close()`는 계산 없이 슬롯만 반환한다. 진행 중 시도 때문에만 막히면 `Retry-After`는 1초, 그 밖에는 창이 끝날 때까지다. `AuthService.login`과 `WithdrawalService.withdraw`가 같은 API를 쓴다.

## 스터디 근거
- [Day 22](https://velog.io/@snowmile1224/Spring-Study-Day-22-%EB%B9%84%EB%B0%80%EB%B2%88%ED%98%B8-%EC%A0%80%EC%9E%A5-%EC%A0%84%EB%9E%B5-BCrypt-%ED%95%B4%EC%8B%B1%EA%B3%BC-%ED%9A%8C%EC%9B%90%EA%B0%80%EC%9E%85-API): BCrypt 해싱과 회원가입.
- [Day 23](https://velog.io/@snowmile1224/Spring-Study-Day-23-JWT-%EB%B0%9C%EA%B8%89%EA%B2%80%EC%A6%9D-HS256-%EC%84%9C%EB%AA%85%EA%B3%BC-%EB%A1%9C%EA%B7%B8%EC%9D%B8-API): HS256 서명, 로그인 실패 메시지를 하나로 두는 이유.
- [Day 24](https://velog.io/@snowmile1224/Spring-Study-Day-24-Servlet-Filter-%EC%9D%B8%EC%A6%9D%EC%9D%B8%EA%B0%80-%EA%B2%BD%EA%B3%84-SecurityFilterChain%EA%B3%BC-%EC%BB%A4%EC%8A%A4%ED%85%80-401403-%ED%95%B8%EB%93%A4%EB%9F%AC): SecurityFilterChain, 커스텀 401/403 핸들러.
- [Day 25](https://velog.io/@snowmile1224/Spring-Study-Day-25-JWT-%EC%9D%B8%EC%A6%9D-%EC%8B%A4%ED%8C%A8-%EC%BC%80%EC%9D%B4%EC%8A%A4-CSRFCORS%EB%A1%9C%EA%B7%B8%EC%95%84%EC%9B%83%EC%9D%98-Stateless-%ED%95%9C%EA%B3%84): 로그아웃의 stateless 한계. 이번에 갚은 부채.
- [Day 27~28](https://velog.io/@snowmile1224/Spring-Study-Day-27-Day-28-4%EC%A3%BC%EC%B0%A8-%EB%A7%88%EB%AC%B4%EB%A6%AC-%EC%8B%9C%ED%97%98-y03atpnv): Q7 오답 교정. 필터가 아니라 `ExceptionTranslationFilter` → EntryPoint가 401을 만든다.
- [Day 34](https://velog.io/@snowmile1224/Spring-Study-Day-34-Day-35-5%EC%A3%BC%EC%B0%A8-%EB%A7%88%EB%AC%B4%EB%A6%AC-%EC%8B%9C%ED%97%98): stateless의 정의에 대한 오답.
- [회고](https://velog.io/@snowmile1224/Spring-Study%EB%A5%BC-%EB%A7%88%EC%B9%98%EB%A9%B0-5%EC%A3%BC-%EB%B0%B1%EC%97%94%EB%93%9C-%EA%B8%B0%EB%B3%B8%EA%B8%B0-%ED%8A%B8%EB%9E%99-%ED%9A%8C%EA%B3%A0%EC%99%80-%EB%8B%A4%EC%9D%8C-%EB%AA%A9%ED%91%9C-mx5am4xr) §3-3: 로그아웃 즉시 무효화를 설명하지 못한 점.

## 검증
- `AuthFlowHttpTest.logoutInvalidatesExistingTokensAndForgetsSchoolConnection`: 로그아웃 뒤 기존 access로 `/api/me`가 401, 기존 refresh로 `/api/auth/refresh`가 401 `INVALID_REFRESH_TOKEN`, 학교 연결 삭제.
- `AuthFlowHttpTest.repeatedWrongPasswordsStopReachingTheSchool`, `LoginAttemptThrottleTest`(4): 실패 한도 뒤 학교 미호출과 429, 창 종료와 성공 시 카운터 처리, `concurrentAttemptsReserveTheBudgetBeforeTheSchoolAnswers`(진행 중 시도의 예산 예약).
- `AuthFlowHttpTest.concurrentWrongPasswordsCannotExceedTheStudentBudget`: 가짜 학교가 응답을 붙잡은 동안 같은 학번으로 8건 동시 요청 → 5건만 학교에 도달, 3건은 429.
- `LogoutConcurrencyTest.concurrentLogoutsNeverLoseAnIncrement`: 8스레드 동시 로그아웃 후 버전이 정확히 8.
- `AuthFlowHttpTest`: 활성 계정의 학교 로그인, refresh/typ 분리, 일관된 401·헤더, 405/415, 이메일 가입 제거, opt-in 암호문 저장·연결 해제.
- 실제 앱 + 로컬 학교 HTTP 서버 스모크에서 로그아웃 후 access/refresh 거부, 기억한 비밀번호로 학교 세션 갱신, 미기억 연결의 재인증 요구, 탈퇴 후 새 앱 id 재진입과 예전 토큰 무효를 확인했다. 실제 학교 계정 로그인은 미검증이다.

## 결과와 트레이드오프
- **로그아웃은 모든 기기에서 로그아웃된다.** 버전이 회원당 하나이기 때문이다. 기기별 로그아웃은 [부채 원장](../tech-debt-ledger.md)의 "나중에 고칠 것"이다.
- **인증된 요청마다 DB 조회가 1회 생긴다.** `findAuthSnapshot`은 `select new ...AuthSnapshot(m.id, m.school.id, m.tokenVersion)` DTO 프로젝션 한 번이라 엔티티·프록시 초기화가 없다. 순수 stateless 대비 비용이며, 즉시 무효화의 대가로 받아들인다.
- **refresh 재사용 탐지는 없다.** 유효 기간 안의 refresh가 탈취되면 로그아웃 전까지 쓸 수 있다. Redis 또는 테이블 기반 탐지는 부채 원장에 있다.
- refresh는 회전 시 이전 토큰을 따로 폐기하지 않는다(버전이 같으면 계속 유효).

## 2026-11-08 후속 결정

학교 장애 시 탈퇴는 `ECLASS_UNAVAILABLE`에만 허용한다. access JWT의 `auth_time`이 `campus.withdrawal.offline-recent-auth`(기본 10분) 이내이고 미래가 아니면 기존 삭제 트랜잭션을 실행한다. 오래되거나 클레임이 없으면 503 `WITHDRAWAL_REAUTH_UNAVAILABLE`이며 데이터는 보존한다. 잘못된 비밀번호는 최근 인증과 무관하게 401 `INVALID_PASSWORD`다. 새 비밀번호 해시를 저장하지 않는다. 상세한 DB 예약·claim 경계는 [ADR-0009](0009-distributed-auth-sync.md)에 기록한다.
