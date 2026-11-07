package com.campuson.api.eclass;

import static com.campuson.api.support.ApiTestClient.bearer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.support.ApiTestClient;
import com.campuson.api.support.EclassTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 로그인 → 연결 저장 → 수동·배경 동기화 → 가져오기까지 실제 서비스 경로를 돈다. 학교만 가짜다.
 * 배경 스케줄러는 test 프로필에서 꺼져 있어 {@code syncInBackground}와 {@code findDueMemberIds}를 직접 부른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class EclassSyncHttpTest {
    private static final Instant FAR_FUTURE = Instant.parse("2999-01-01T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired EclassTestConfiguration.FakeEclass school;
    @Autowired EclassSyncService sync;
    @Autowired EclassAccountService accounts;
    @Autowired EclassConnectionRepository connections;
    @Autowired JdbcTemplate jdbc;

    @Test void manualSyncImportsTheSchoolSnapshotIntoTheCalendar() throws Exception {
        Account account = login(false);
        String key = "K" + UUID.randomUUID().toString().substring(0, 8);
        school.snapshot(account.studentId, new EclassSnapshot(List.of(new EclassCourse(key, "자료구조")), List.of(
                new EclassTask(key + ":report:1", key, "자료구조", "연결 리스트 과제", "assignment", null,
                        Instant.parse("2099-06-10T05:00:00Z"), false, null, null, null, java.util.List.of()))));

        sync(account).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("READY"));

        JsonNode day = om.readTree(mvc.perform(get("/api/events").header("Authorization", bearer(account.access))
                        .param("from", "2099-06-10").param("to", "2099-06-10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, day.size());
        assertEquals("연결 리스트 과제", day.get(0).get("title").asText());
        assertEquals("자료구조", day.get(0).get("courseName").asText());
    }

    @Test void expiredSessionWithRememberedPasswordReauthenticatesOnce() throws Exception {
        Account account = login(true);
        school.expireSessions(account.studentId);

        sync(account).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("READY"));

        assertEquals(2, school.authCalls(account.studentId), "login + one background re-login");
    }

    @Test void expiredSessionWithoutPasswordAsksForReloginAndLeavesTheSchedule() throws Exception {
        Account account = login(false);
        school.expireSessions(account.studentId);

        sync(account).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ECLASS_REAUTH_REQUIRED"));

        assertEquals("REAUTH_REQUIRED", connections.findById(account.memberId).orElseThrow().getSyncState());
        assertFalse(connections.findDueMemberIds(FAR_FUTURE, FAR_FUTURE).contains(account.memberId));
    }

    /**
     * 학교 비밀번호가 바뀐 뒤: 저장 비밀번호로 한 번만 시도하고 지운다. 이후 배경 동기화는 학교 로그인을 하지 않는다.
     * 실패 로그는 warn 한 줄에 회원 id와 코드만: 비밀번호·쿠키는 남지 않는다.
     */
    @Test void rejectedSavedPasswordIsForgottenAndNeverRetried(CapturedOutput output) {
        Account account = login(true);
        school.expireSessions(account.studentId);
        school.changePassword(account.studentId, "changed-at-school!");

        sync.syncInBackground(account.memberId);
        EclassConnection connection = connections.findById(account.memberId).orElseThrow();
        assertEquals("REAUTH_REQUIRED", connection.getSyncState());
        assertEquals("INVALID_CREDENTIALS", connection.getLastErrorCode());
        assertNull(connection.getEncryptedPassword());
        assertEquals(2, school.authCalls(account.studentId));

        sync.syncInBackground(account.memberId);
        assertEquals(2, school.authCalls(account.studentId), "no further school login attempts");

        String line = logLine(output, "e-class 동기화 실패 memberId=" + account.memberId + " code=INVALID_CREDENTIALS");
        assertTrue(line.contains("WARN"), line);
        for (String secret : List.of(ApiTestClient.PASSWORD, "changed-at-school!", "SESSION=" + account.studentId)) {
            assertFalse(output.getOut().contains(secret), "secret leaked to the log: " + secret);
        }
        assertFalse(connections.findDueMemberIds(FAR_FUTURE, FAR_FUTURE).contains(account.memberId));
    }

    @Test void transientFailureIsRetriedOnlyAfterTheBackoff() {
        Account account = login(false);
        accounts.failed(account.memberId, accounts.context(account.memberId).generation(), ErrorCode.ECLASS_UNAVAILABLE);
        Instant failedAt = connections.findById(account.memberId).orElseThrow().getUpdatedAt();

        assertFalse(connections.findDueMemberIds(FAR_FUTURE, failedAt.minus(Duration.ofSeconds(1))).contains(account.memberId));
        assertTrue(connections.findDueMemberIds(FAR_FUTURE, failedAt).contains(account.memberId));
    }

    @Test void undecryptableStoredSecretsRequireReloginInsteadOfEndlessRetry() throws Exception {
        Account account = login(true);
        jdbc.update("update eclass_connections set encrypted_session_cookie = 'v1.AAAAAAAAAAAAAAAA.AAAA' where user_id = ?", account.memberId);

        sync(account).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ECLASS_REAUTH_REQUIRED"));

        EclassConnection connection = connections.findById(account.memberId).orElseThrow();
        assertEquals("REAUTH_REQUIRED", connection.getSyncState());
        assertNull(connection.getEncryptedPassword());
        assertEquals(1, school.authCalls(account.studentId));
    }

    /** 수집 응답을 기다리는 동안 로그아웃→재로그인: 끝난 옛 작업은 새 연결에 옛 쿠키·READY·일정을 쓰지 않는다. */
    @Test void staleSyncFinishingAfterReloginIsDiscarded() throws Exception {
        Account first = login(false);
        String key = "S" + UUID.randomUUID().toString().substring(0, 8);
        school.snapshot(first.studentId, new EclassSnapshot(List.of(new EclassCourse(key, "운영체제")), List.of(
                new EclassTask(key + ":report:1", key, "운영체제", "옛 세대 과제", "assignment", null,
                        Instant.parse("2099-07-01T05:00:00Z"), false, null, null, null, java.util.List.of()))));
        Account second = whileFetchIsHeld(first, true, () -> login(first.studentId, false));

        EclassConnection connection = connections.findById(second.memberId).orElseThrow();
        assertEquals("PENDING", connection.getSyncState(), "the new connection has not synced yet");
        assertNull(connection.getLastSyncedAt());
        JsonNode day = om.readTree(mvc.perform(get("/api/events").header("Authorization", bearer(second.access))
                        .param("from", "2099-07-01").param("to", "2099-07-01"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, day.size());
    }

    /** 옛 작업의 세션이 만료돼도, 그 사이 재로그인했다면 옛 저장 비밀번호로 학교에 다시 로그인하지 않는다. */
    @Test void staleSyncNeverReauthenticatesWithAReplacedConnectionsPassword() throws Exception {
        Account first = login(true);
        school.expireSessions(first.studentId);
        Account second = whileFetchIsHeld(first, true, () -> login(first.studentId, true));

        assertEquals(2, school.authCalls(first.studentId), "two user logins, no background re-login");
        EclassConnection connection = connections.findById(second.memberId).orElseThrow();
        assertEquals("PENDING", connection.getSyncState());
        assertNotNull(connection.getEncryptedPassword(), "the new connection's password must survive");
    }

    /** 로그아웃 없이 재로그인한 동안 옛 작업이 실패로 끝나도 새 연결은 그대로이고, 경고 대신 info로 버렸다고만 남긴다. */
    @Test void staleFailureAfterReloginWithoutLogoutIsDiscardedQuietly(CapturedOutput output) throws Exception {
        Account first = login(false);
        school.expireSessions(first.studentId);
        Account second = whileFetchIsHeld(first, false, () -> login(first.studentId, false));

        assertEquals(first.memberId, second.memberId);
        EclassConnection connection = connections.findById(second.memberId).orElseThrow();
        assertEquals("PENDING", connection.getSyncState());
        assertNull(connection.getLastErrorCode());
        String line = logLine(output, "e-class 동기화 결과를 버림: 도중에 연결이 바뀜 memberId=" + first.memberId);
        assertTrue(line.contains("INFO"), line);
        assertFalse(output.getOut().contains("memberId=" + first.memberId + " code="), "a discarded failure is not a sync failure");
    }

    /** 옛 세대의 실패·복호화 실패 보고는 새 연결의 비밀번호·상태를 건드리지 않는다. 현재 세대의 보고만 반영된다. */
    @Test void staleFailureReportsNeverTouchTheReplacedConnection() {
        Account first = login(true);
        String stale = accounts.context(first.memberId).generation();
        login(first.studentId, true);

        assertFalse(accounts.failed(first.memberId, stale, ErrorCode.INVALID_CREDENTIALS));
        assertFalse(accounts.secretsUnreadable(first.memberId, stale));
        EclassConnection connection = connections.findById(first.memberId).orElseThrow();
        assertEquals("PENDING", connection.getSyncState());
        assertNull(connection.getLastErrorCode());
        assertNotNull(connection.getEncryptedPassword());
        assertNotNull(connection.getEncryptedSessionCookie());

        assertTrue(accounts.failed(first.memberId, accounts.context(first.memberId).generation(), ErrorCode.INVALID_CREDENTIALS));
        assertNull(connections.findById(first.memberId).orElseThrow().getEncryptedPassword());
    }

    private Account whileFetchIsHeld(Account account, boolean logoutFirst, Callable<Account> meanwhile) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        school.holdFetch(account.studentId, entered, release);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> stale = pool.submit(() -> sync.syncInBackground(account.memberId));
            assertTrue(entered.await(10, TimeUnit.SECONDS), "old sync must be waiting on the school");
            if (logoutFirst) {
                mvc.perform(post("/api/auth/logout").header("Authorization", bearer(account.access))).andExpect(status().isNoContent());
            }
            Account next = meanwhile.call();
            release.countDown();
            stale.get(10, TimeUnit.SECONDS);
            return next;
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private ResultActions sync(Account account) throws Exception {
        return mvc.perform(post("/api/eclass/sync").header("Authorization", bearer(account.access)));
    }

    private static String logLine(CapturedOutput output, String message) {
        return output.getOut().lines().filter(line -> line.contains(message)).findFirst()
                .orElseThrow(() -> new AssertionError("missing log line: " + message));
    }

    private Account login(boolean remember) {
        return login("student-" + UUID.randomUUID(), remember);
    }

    private Account login(String studentId, boolean remember) {
        try {
            JsonNode tokens = om.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(Map.of("studentId", studentId, "password", ApiTestClient.PASSWORD,
                                    "rememberCredentials", remember))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String access = tokens.get("accessToken").asText();
            long memberId = om.readTree(mvc.perform(get("/api/me").header("Authorization", bearer(access)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            return new Account(studentId, access, memberId);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private record Account(String studentId, String access, long memberId) {}
}
