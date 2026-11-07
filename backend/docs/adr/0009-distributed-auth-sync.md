# ADR-0009 여러 서버의 인증 예산·동기화 선점과 연결 교체

## 상태
채택 (2026-11-08)

## 결정

`V6__distributed_coordination.sql`은 실패 창(`auth_throttle_failure`), 만료 예약(`auth_throttle_reservation`), DB mutex 행 및 연결의 sync lease·reauth claim 소유자/만료 컬럼을 추가한다. V1~V5는 변경하지 않는다. H2 PostgreSQL 모드와 PostgreSQL용 공통 SQL만 사용한다.

로그인 제한은 하나의 mutex 행을 `FOR UPDATE`로 잠근 짧은 서비스 트랜잭션에서 두 예산을 함께 판정·예약한다. 학교 호출은 트랜잭션 종료 후 실행한다. 완료 때 예약을 제거하고 실패 창을 갱신한다. 성공은 학번 실패만 초기화한다. 진행 중 예약만으로 막히면 Retry-After 1초다. 만료 예약과 창은 다음 begin/finish에서 정리한다. 기본 예약 lease는 15초이며 실제 값은 인증 전체 제한 + 5초 이상이다. 글로벌 mutex의 처리량 비용은 허용하되 많은 인증 트래픽이 생기면 같은 계약으로 버킷별 잠금으로 분할할 수 있다.

`ConnectionCoordination.acquireSync`의 조건부 UPDATE로 회원당 하나를 선점한다. 수동은 409, 배경은 건너뛴다. UUID 소유자 조건으로 해제하여 만료 후 옛 실행이 새 lease를 지우지 못한다. 기본 5분이며 실제 하한은 수집 전체 제한 2분 × 2회 + 인증 전체 제한 + 5초다. 메모리 inFlight 경로는 삭제했다. lease가 없는 연결 조회는 기존 재인증 오류를 유지한다.

저장 비밀번호로 재인증을 결정하는 단계는 세대·sync 소유자·만료를 조건으로 reauth claim을 원자적으로 설정한다. 이 커밋 뒤 학교 요청을 시작하고 요청 종료의 finally에서 claim을 해제한다. 학교 인증의 전체 제한은 `campus.eclass.request-timeout`(기본 10초), claim의 만료는 이 제한 + 5초(기본 15초)다. HTTP adapter는 리다이렉트·여러 요청을 합친 인증 기한을 남은 시간으로 줄인다. 수집은 호출당 전체 2분 제한이며 개별 요청 제한도 유지한다.

로그인 연결 교체·로그아웃·연결 해제·탈퇴는 사용자 잠금 뒤 연결 행을 잠그고 활성 claim을 검사한다. 활성 claim이면 트랜잭션 전체를 롤백하고 커넥션을 반환한 뒤 20ms 간격으로 다시 시도한다. 최대 대기는 claim 기간(기본 15초), 계속 활성 상태거나 인터럽트되면 409 `ECLASS_SYNC_IN_PROGRESS`다. 만료 claim은 즉시 통과한다. 새 claim을 계속 만드는 부하에서도 무한 대기하지 않는다. 드물게 로그아웃·탈퇴·재로그인이 몇 초 늦어질 수 있다. 성공/실패 반영의 세대 검사도 유지한다.

## 주소·학번

프록시는 기본 불신이다. `campus.security.trusted-proxies`는 쉼표로 구분한 CIDR이며 직접 peer가 신뢰될 때만 XFF를 오른쪽부터 검사한다. 첫 비신뢰 주소가 클라이언트이며 형식 오류는 체인 전체를 무시한다. DNS 조회는 하지 않는다. `server.forward-headers-strategy`는 NONE으로 유지한다.

학번은 NFKC → strip 후 1~64자, 내부 공백·제어·format 문자 없이 처리한다. 계정 식별은 학교 profile의 같은 정규화 값이고 입력과 같아야 한다. throttle 키는 Locale.ROOT 소문자로 묶는다. 선행 0과 계정 식별의 대소문자는 유지한다. 기존 ASCII 데이터와 legacy 격리 계정은 영향을 받지 않는다. 과거 비정규 Unicode 학번의 자동 재작성·계정 병합은 하지 않으며 운영 데이터의 별도 확인이 필요하다.

## 검증과 한계

`LoginAttemptThrottleTest`는 같은 DB를 쓰는 독립 객체의 공유 예산·lease 회수를 검증한다. `DistributedSyncTest`는 독립 sync 객체 간 409/배경 생략, 만료 lease 회수, 가짜 학교 재인증 hold 중 로그아웃/재로그인 대기와 새 연결 보존을 검증한다. 학교 HTTP 중 트랜잭션·커넥션을 잡지 않는다. 시간 기한 테스트는 Clock을 이동하고 sleep을 사용하지 않는다.

`SchemaUpgradeScenarios`의 V2·V4 데이터 업그레이드를 H2와 PostgreSQL 17이 공유한다. PostgreSQL 테스트는 로컬(Docker 없음)에서는 skipped이고 CI(PR #1)에서 실행·통과했다. 실제 여러 프로세스·네트워크 분할·서버 시계 차이의 부하 검증은 하지 않았다. 서버의 UTC 시계 동기화가 필요하며 lease 만료 뒤의 DB 반영은 세대 검사로 보호한다.
