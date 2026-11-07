# ADR-0001 기능 우선 패키지

## 상태
채택

## 맥락
스터디에서는 도메인이 하나(예약)였고, `controller`/`service`/`repository`처럼 계층 기준으로 패키지를 나눠도 불편하지 않았다. 이 프로젝트는 명세상 기능이 여섯 개(F1~F6)이고, 3차 확장에서 학교별 LMS·공지 어댑터가 추가된다. 한 기능을 고칠 때 계층 패키지 여러 곳을 오가야 하는 구조는 변경 단위와 맞지 않는다.

## 결정
루트 패키지 `com.campuson.api` 아래를 기능 단위로 나눈다.

|패키지|내용|
|---|---|
|`auth`|`AuthController`, `AuthService`, `WithdrawalController/Service/Store`, 요청·응답 record|
|`member`|`Member`, `MemberRepository`, `AuthSnapshot`, `MemberController`, `MemberService`|
|`school`|`School`, `SchoolRepository`, `SchoolController`, `SchoolService`|
|`schedule`|`Event`, `CalendarFeed`, `EventService`, `CalendarFeedService`, `EventController`, `CalendarFeedController`, `IcsController`, `schedule/ics/IcsWriter`|
|`notice`|`Notice`, `NoticeRepository`, `NoticeService`, `NoticeController`|
|`eclass`|HTTP 어댑터·파서, 암호화 연결 저장, 자동 등록·동기화·연결 상태 서비스|
|`catalog`|`Course`, 수강 소속, 내 과목 조회|
|`global`|여러 기능이 함께 쓰는 것만: `config`(`ClockConfig`), `error`, `logging`, `security`, `time`(`CampusTime`)|

기능 패키지 안에서는 계층을 하위 패키지로 다시 나누지 않는다. 클래스 이름 접미사(`Controller`, `Service`, `Repository`)로 계층을 구분한다. 계층 규칙(컨트롤러는 서비스만 호출, 트랜잭션은 서비스에)은 그대로 유지한다.

## 스터디 근거
- [Day 4](https://velog.io/@snowmile1224/Spring-Study-Day-4-ServiceRepository-Separation-of-Concerns-Identifier-%EB%8F%84%EC%9E%85%EA%B3%BC-Wrapper-%EA%B0%92-%EB%B9%84%EA%B5%90): Service/Repository를 분리하며 계층의 책임을 나눴다. 도메인 하나에서는 계층 패키지로 충분했다.
- [Day 5](https://velog.io/@snowmile1224/Spring-Study-Day-5-IoC%EC%99%80-DI-Constructor-Injection%EA%B3%BC-Singleton-Bean): 생성자 주입. 이 프로젝트의 모든 빈도 생성자 주입이고 Lombok은 쓰지 않는다.

## 결과와 트레이드오프
- 일정 기능을 고치는 변경은 대부분 `schedule` 패키지 안에서 끝난다. Phase 2에서 인증·일정·공지 슬라이스를 서로 다른 작업자가 병렬로 구현할 때 파일 충돌이 없었던 것도 이 구조 덕이다.
- 기능 사이 의존은 명시적으로 허용한다. `auth`는 `eclass` 인증을 호출하고, `eclass` 저장 서비스는 `schedule.EclassImportService`로 수집 결과를 반영한다. `schedule`은 `catalog` 과목과 `eclass` 수집 DTO를 사용한다. 컨트롤러가 다른 기능의 저장소에 직접 접근하지는 않는다.
- 같은 계층 클래스를 한눈에 모아 보기는 어려워졌다.
