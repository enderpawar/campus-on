package com.campuson.api.auth;

import static com.campuson.api.support.ApiTestClient.bearer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import com.campuson.api.eclass.EclassConnectionRepository;
import com.campuson.api.eclass.EclassSecrets;
import com.campuson.api.member.MemberRepository;
import com.campuson.api.support.ApiTestClient;
import com.campuson.api.support.EclassTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class AuthFlowHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MemberRepository members;
    @Autowired EclassConnectionRepository connections;
    @Autowired EclassSecrets secrets;
    @Autowired EclassTestConfiguration.FakeEclass school;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    /** 학번당 실패 한도(기본 5)에 닿으면 올바른 비밀번호여도 학교에 요청하지 않고 429를 낸다. */
    @Test void repeatedWrongPasswordsStopReachingTheSchool() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            login(studentId, "wrong", "10.20.30." + i).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login(studentId, ApiTestClient.PASSWORD, "10.20.31.1").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"));
        assertEquals(5, school.authCalls(studentId));
    }
    /**
     * 학교 응답을 붙잡아 둔 채 같은 학번 요청 8개를 동시에 보낸다. 진행 중 시도도 예산을 쓰므로
     * 학교에는 5개만 도달하고 나머지 3개는 학교 응답을 기다리지 않고 429다.
     */
    @Test void concurrentWrongPasswordsCannotExceedTheStudentBudget() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(5);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        school.hold(studentId, entered, release);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var completed = new java.util.concurrent.ExecutorCompletionService<Integer>(pool);
            for (int i = 0; i < 8; i++) {
                String client = "10.40.0." + i;
                completed.submit(() -> login(studentId, "wrong", client).andReturn().getResponse().getStatus());
            }
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS), "five attempts must reach the school");
            for (int i = 0; i < 3; i++) {
                assertEquals(429, completed.poll(10, java.util.concurrent.TimeUnit.SECONDS).get(), "rejected while five are in flight");
            }
            release.countDown();
            for (int i = 0; i < 5; i++) {
                assertEquals(401, completed.poll(10, java.util.concurrent.TimeUnit.SECONDS).get());
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertEquals(5, school.authCalls(studentId));
    }
    /**
     * 로그인이 사용자 행을 읽는 시점에 다른 트랜잭션(로그아웃)이 token_version을 올리고 아직 커밋하지 않았다.
     * 로그인은 그 커밋을 기다린 뒤의 최신 버전으로 토큰을 발급해야 한다(오래된 버전이면 받자마자 401).
     * 200ms는 로그인이 사용자 행에 도달할 시간이다. 짧으면 이 테스트가 경합을 덜 재현할 뿐 거짓 실패는 아니다.
     */
    @Test void loginRacingAnUncommittedLogoutIssuesTokensForTheNewVersion() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        long memberId = me(ApiTestClient.login(mvc, om, studentId).get("accessToken").asText()).get("id").asLong();
        java.util.concurrent.CountDownLatch schoolEntered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch schoolRelease = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch bumped = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch commit = new java.util.concurrent.CountDownLatch(1);
        school.hold(studentId, schoolEntered, schoolRelease);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var login = pool.submit(() -> login(studentId, ApiTestClient.PASSWORD, false).andReturn().getResponse().getContentAsString());
            assertTrue(schoolEntered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var logout = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertEquals(1, members.increaseTokenVersion(memberId));
                bumped.countDown();
                try { commit.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }));
            assertTrue(bumped.await(10, java.util.concurrent.TimeUnit.SECONDS));
            schoolRelease.countDown();
            Thread.sleep(200);
            commit.countDown();
            logout.get(10, java.util.concurrent.TimeUnit.SECONDS);
            String access = om.readTree(login.get(10, java.util.concurrent.TimeUnit.SECONDS)).get("accessToken").asText();
            mvc.perform(get("/api/me").header("Authorization", bearer(access))).andExpect(status().isOk());
        } finally {
            schoolRelease.countDown();
            commit.countDown();
            pool.shutdownNow();
        }
    }
    @Test void reloginWithoutRememberForgetsSavedPassword() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        login(studentId, ApiTestClient.PASSWORD, true).andExpect(status().isOk());
        login(studentId, ApiTestClient.PASSWORD, false).andExpect(status().isOk());
        long id = members.findByExternalRefAndSchoolId(studentId, ApiTestClient.seoultechId(mvc, om)).orElseThrow().getId();
        assertNull(connections.findById(id).orElseThrow().getEncryptedPassword());
    }
    @Test void firstSchoolLoginProvisionsOneIdentityAndDoesNotRequireRegistration() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        JsonNode first = ApiTestClient.login(mvc, om, studentId);
        long firstId = me(first.get("accessToken").asText()).get("id").asLong();
        JsonNode second = ApiTestClient.login(mvc, om, studentId);
        assertEquals(firstId, me(second.get("accessToken").asText()).get("id").asLong());
        assertEquals(studentId, me(first.get("accessToken").asText()).get("studentId").asText());
        assertEquals(ApiTestClient.SEOULTECH, me(first.get("accessToken").asText()).get("schoolName").asText());
        assertNull(connections.findById(firstId).orElseThrow().getEncryptedPassword(), "password persistence must be opt-in");
    }
    @Test void failedSchoolAuthenticationNeverCreatesALocalIdentity() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("studentId", studentId, "password", "wrong"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertTrue(members.findByExternalRefAndSchoolId(studentId, ApiTestClient.seoultechId(mvc, om)).isEmpty());
    }
    @Test void explicitRememberEncryptsCredentialsAndCanBeRevoked() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        JsonNode tokens = om.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("studentId", studentId, "password", ApiTestClient.PASSWORD, "rememberCredentials", true))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String access = tokens.get("accessToken").asText(); long id = me(access).get("id").asLong();
        var stored = connections.findById(id).orElseThrow();
        assertNotEquals(ApiTestClient.PASSWORD, stored.getEncryptedPassword());
        assertEquals(ApiTestClient.PASSWORD, secrets.decrypt(stored.getEncryptedPassword()));
        assertTrue(stored.getEncryptedSessionCookie().startsWith("v1."));
        assertTrue(secrets.decrypt(stored.getEncryptedSessionCookie()).startsWith("SESSION=" + studentId + "#"));
        mvc.perform(delete("/api/eclass/connection").header("Authorization", bearer(access))).andExpect(status().isNoContent());
        assertTrue(connections.findById(id).isEmpty());
    }
    @Test void logoutInvalidatesExistingTokensAndForgetsSchoolConnection() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        JsonNode tokens = om.readTree(login(studentId, ApiTestClient.PASSWORD, true).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String access = tokens.get("accessToken").asText();
        long id = me(access).get("id").asLong();
        assertTrue(connections.findById(id).isPresent());
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(access))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", bearer(access))).andExpect(status().isUnauthorized());
        refresh(tokens.get("refreshToken").asText()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        assertTrue(connections.findById(id).isEmpty(), "cookie and remembered password must not outlive logout");
    }
    @Test void refreshIssuesAccessButTypesCannotBeConfused() throws Exception {
        JsonNode tokens = ApiTestClient.login(mvc, om);
        String access = om.readTree(refresh(tokens.get("refreshToken").asText()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        assertTrue(me(access).has("studentId"));
        refresh(access).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(get("/api/me").header("Authorization", bearer(tokens.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }
    @Test void missingOrMalformedTokensHaveConsistentProblems() throws Exception {
        var response = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED")).andReturn().getResponse();
        assertEquals(response.getHeader("X-Request-Id"), om.readTree(response.getContentAsString()).get("requestId").asText());
        mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }
    @Test void signupAndEmailLoginAreNoLongerAccepted() throws Exception {
        String access = ApiTestClient.loginAccessToken(mvc, om);
        mvc.perform(post("/api/auth/signup").header("Authorization", bearer(access)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@example.invalid\",\"password\":\"p\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    @Test void unsupportedMethodReturns405WithAllowHeader() throws Exception {
        String access = ApiTestClient.loginAccessToken(mvc, om);
        mvc.perform(put("/api/me").header("Authorization", bearer(access))).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET"))).andExpect(header().string("Allow", containsString("DELETE")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
    @Test void unsupportedContentTypeAndBlankInputAreClientErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("studentId=a"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    private JsonNode me(String access) throws Exception {
        return om.readTree(mvc.perform(get("/api/me").header("Authorization", bearer(access))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("refreshToken", token))));
    }
    private org.springframework.test.web.servlet.ResultActions login(String studentId, String password, String client) throws Exception {
        return mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr(client); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("studentId", studentId, "password", password))));
    }
    private org.springframework.test.web.servlet.ResultActions login(String studentId, String password, boolean remember) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("studentId", studentId, "password", password, "rememberCredentials", remember))));
    }
}
