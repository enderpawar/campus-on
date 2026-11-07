package com.campuson.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.campuson.api.member.MemberRepository;
import com.campuson.api.school.SchoolRepository;
import com.campuson.api.support.ApiTestClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 동시 로그아웃이 token_version 증가를 잃지 않는지 확인한다 (Codex 리뷰 #1).
 * 읽고 {@code ++}한 뒤 dirty checking으로 쓰면 늦게 커밋한 트랜잭션이 옛 값을 덮어써 버전이 내려가고,
 * 이미 폐기된 토큰이 다시 유효해진다. 원자적 UPDATE라면 N번 로그아웃 후 버전은 정확히 N이다.
 */
@SpringBootTest
@org.springframework.context.annotation.Import(com.campuson.api.support.EclassTestConfiguration.class)
class LogoutConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired AuthService authService;
    @Autowired MemberRepository memberRepository;
    @Autowired SchoolRepository schoolRepository;

    @Test
    void concurrentLogoutsNeverLoseAnIncrement() throws Exception {
        long schoolId = schoolRepository.findAllByOrderByNameAsc().stream()
                .filter(s -> s.getName().equals(ApiTestClient.SEOULTECH)).findFirst().orElseThrow().getId();
        String studentId = "student-" + UUID.randomUUID();
        authService.login(new LoginRequest(studentId, ApiTestClient.PASSWORD, false), null);
        long memberId = memberRepository.findByExternalRefAndSchoolId(studentId, schoolId).orElseThrow().getId();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    authService.logout(memberId);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(THREADS, memberRepository.findAuthSnapshot(memberId).orElseThrow().tokenVersion());
    }
}
