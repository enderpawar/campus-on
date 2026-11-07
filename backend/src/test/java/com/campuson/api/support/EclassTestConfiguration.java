package com.campuson.api.support;

import com.campuson.api.eclass.*;
import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test-only upstream boundary; production always uses the HTTP adapter, tested separately. */
@TestConfiguration(proxyBeanMethods = false)
public class EclassTestConfiguration {
    @Bean @Primary
    public FakeEclass testEclassGateway() {
        return new FakeEclass();
    }

    /**
     * 학번별로 비밀번호·스냅샷·세션 만료를 바꿀 수 있는 가짜 학교. 쿠키는 {@code SESSION=<학번>#<발급 순번>}이고,
     * {@link #expireSessions}는 그때까지 발급된 쿠키를 모두 만료시킨다. 기본값은 비밀번호 {@link ApiTestClient#PASSWORD}, 빈 스냅샷이다.
     */
    public static final class FakeEclass implements EclassGateway {
        private final java.util.Set<String> unavailable = ConcurrentHashMap.newKeySet();
        public void unavailable(String id) { unavailable.add(id); }
        private final AtomicInteger issued = new AtomicInteger();
        private final Map<String, String> passwords = new ConcurrentHashMap<>();
        private final Map<String, EclassSnapshot> snapshots = new ConcurrentHashMap<>();
        private final Map<String, Integer> expiredThrough = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> authCalls = new ConcurrentHashMap<>();

        private final Map<String, Hold> fetchHolds = new ConcurrentHashMap<>();
        private final Map<String, Hold> holds = new ConcurrentHashMap<>();

        @Override public EclassSession authenticate(String studentId, String password) {
            authCalls.computeIfAbsent(studentId, k -> new AtomicInteger()).incrementAndGet();
            Hold hold = holds.get(studentId);
            if (hold != null) hold.await();
            if (unavailable.contains(studentId)) throw new BusinessException(ErrorCode.ECLASS_UNAVAILABLE);
            if (!passwords.getOrDefault(studentId, ApiTestClient.PASSWORD).equals(password)) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            return new EclassSession("SESSION=" + studentId + "#" + issued.incrementAndGet(),
                    new EclassProfile(studentId, "테스트", null, null));
        }

        @Override public EclassSnapshot fetch(String cookie) {
            if (cookie == null || !cookie.startsWith("SESSION=")) throw new BusinessException(ErrorCode.ECLASS_REAUTH_REQUIRED);
            String value = cookie.substring("SESSION=".length());
            int hash = value.lastIndexOf('#');
            String studentId = hash < 0 ? value : value.substring(0, hash);
            Hold hold = fetchHolds.get(studentId);
            if (hold != null) hold.await();
            int serial = hash < 0 ? 0 : Integer.parseInt(value.substring(hash + 1));
            if (serial <= expiredThrough.getOrDefault(studentId, -1)) throw new BusinessException(ErrorCode.ECLASS_REAUTH_REQUIRED);
            return snapshots.getOrDefault(studentId, new EclassSnapshot(List.of(), List.of()));
        }

        public void changePassword(String studentId, String password) { passwords.put(studentId, password); }
        public void snapshot(String studentId, EclassSnapshot snapshot) { snapshots.put(studentId, snapshot); }
        public void expireSessions(String studentId) { expiredThrough.put(studentId, issued.get()); }
        /** 이 학번의 학교 로그인 응답을 {@code release}가 열릴 때까지 붙잡는다. 들어온 호출마다 {@code entered}를 센다. */
        public void hold(String studentId, CountDownLatch entered, CountDownLatch release) { holds.put(studentId, new Hold(entered, release)); }
        /** 이 학번의 수집 응답을 붙잡는다. 만료 판정은 풀린 뒤에 한다(그 사이 일어난 일까지 반영). */
        public void holdFetch(String studentId, CountDownLatch entered, CountDownLatch release) { fetchHolds.put(studentId, new Hold(entered, release)); }

        private record Hold(CountDownLatch entered, CountDownLatch release) {
            void await() {
                entered.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("hold was never released");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }
        }
        public int authCalls(String studentId) { AtomicInteger calls = authCalls.get(studentId); return calls == null ? 0 : calls.get(); }
    }
}
