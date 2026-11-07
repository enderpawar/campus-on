package com.campuson.api.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campuson.api.support.ApiTestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.campuson.api.support.EclassTestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** ICS 구독 피드 통합 테스트. 피드 GET은 Authorization 없이 경로 토큰만으로 읽는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class IcsHttpTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Autowired
    JdbcTemplate jdbc;

    /** V3가 레거시 계정을 SUSPENDED로 바꿔도 피드 행은 남는다. 그 토큰은 더 이상 일정을 내주면 안 된다. */
    @Test
    void suspendedMembersFeedIsNotServed() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long memberId = om.readTree(mvc.perform(get("/api/me").header("Authorization", ApiTestClient.bearer(token)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String path = issueFeedPath(token);
        mvc.perform(get(path)).andExpect(status().isOk());

        jdbc.update("update users set account_status = 'SUSPENDED' where user_id = ?", memberId);

        mvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CALENDAR_FEED_NOT_FOUND"));
    }

    @Test
    void issuedFeedIsReadableWithoutAuthAndEscapesSummary() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        mvc.perform(post("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"회의, 준비; 1\",\"dueAt\":\"2099-01-01T09:00:00+09:00\"}"))
                .andExpect(status().isCreated());
        String path = issueFeedPath(token);

        MvcResult result = mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertTrue(contentType != null && contentType.startsWith("text/calendar"), contentType);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.startsWith("BEGIN:VCALENDAR\r\n"), body);
        assertTrue(body.contains("SUMMARY:회의\\, 준비\\; 1\r\n"), body);
    }

    @Test
    void rotatedTokenInvalidatesOldUrl() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        String oldPath = issueFeedPath(token);
        String newPath = issueFeedPath(token);
        assertNotEquals(oldPath, newPath);

        mvc.perform(get(oldPath))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CALENDAR_FEED_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/ics/***.ics"));
        mvc.perform(get(newPath))
                .andExpect(status().isOk());
    }

    @Test
    void unknownTokenReturns404() throws Exception {
        // 경로 정규식을 통과하는 형식(43자 base64url)이어야 서비스까지 도달해 CALENDAR_FEED_NOT_FOUND가 된다.
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String unknown = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        mvc.perform(get("/ics/" + unknown + ".ics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CALENDAR_FEED_NOT_FOUND"));
    }

    @Test
    void errorBodyNeverEchoesFeedToken() throws Exception {
        String path = issueFeedPath(ApiTestClient.loginAccessToken(mvc, om));
        String token = path.substring("/ics/".length(), path.length() - ".ics".length());

        // GET만 공개라 인증 없는 POST는 401 EntryPoint를 탄다: instance에 원문 경로(=유효한 토큰)가 남으면 안 된다
        String body = mvc.perform(post(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.instance").value("/ics/***.ics"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains(token), body);
    }

    @Test
    void sameFeedUrlReflectsLaterEditsAndDeletions() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long kept = createEvent(token, "{\"title\":\"수정 전\",\"dueAt\":\"2099-02-01T09:00:00+09:00\"}");
        long removed = createEvent(token, "{\"title\":\"지울 일정\",\"dueAt\":\"2099-02-02T09:00:00+09:00\"}");
        String path = issueFeedPath(token);
        String before = readFeed(path);
        assertTrue(before.contains("SUMMARY:수정 전\r\n"), before);
        assertTrue(before.contains("UID:event-" + removed + "@campus-on\r\n"), before);

        mvc.perform(put("/api/events/{id}", kept).header("Authorization", ApiTestClient.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정 후\",\"dueAt\":\"2099-02-01T09:00:00+09:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/events/{id}", removed).header("Authorization", ApiTestClient.bearer(token)))
                .andExpect(status().isNoContent());

        // 구독 캘린더는 같은 URL을 다시 읽는다: 첫 결과를 고정하면 안 된다
        String after = readFeed(path);
        assertTrue(after.contains("SUMMARY:수정 후\r\n"), after);
        assertFalse(after.contains("수정 전"), after);
        assertFalse(after.contains("UID:event-" + removed + "@campus-on"), after);
    }

    @Test
    void feedContainsOnlyOwnRecentEventsIncludingDoneOnes() throws Exception {
        String mine = ApiTestClient.loginAccessToken(mvc, om);
        String other = ApiTestClient.loginAccessToken(mvc, om);
        long done = createEvent(mine, "{\"title\":\"끝낸 일정\",\"dueAt\":\"2099-02-03T09:00:00+09:00\"}");
        mvc.perform(patch("/api/events/{id}/done", done).header("Authorization", ApiTestClient.bearer(mine))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true}"))
                .andExpect(status().isOk());
        long old = createEvent(mine, "{\"title\":\"오래된 일정\",\"dueAt\":\"2000-01-01T09:00:00+09:00\"}");
        long others = createEvent(other, "{\"title\":\"남의 일정\",\"dueAt\":\"2099-02-03T09:00:00+09:00\"}");

        String feed = readFeed(issueFeedPath(mine));

        assertTrue(feed.contains("UID:event-" + done + "@campus-on\r\n"), "완료한 일정도 피드에 남는다");
        assertFalse(feed.contains("UID:event-" + old + "@campus-on"), "30일보다 오래된 일정은 빠진다");
        assertFalse(feed.contains("UID:event-" + others + "@campus-on"), "다른 회원의 일정은 섞이지 않는다");
        assertTrue(feed.endsWith("END:VCALENDAR\r\n"), feed);
    }

    @Test
    void currentFeedIs404BeforeIssueAndReturnsIssuedUrlAfter() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);

        mvc.perform(get("/api/calendar-feed").header("Authorization", ApiTestClient.bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CALENDAR_FEED_NOT_FOUND"));

        String issued = issueFeedPath(token);
        String current = om.readTree(mvc.perform(get("/api/calendar-feed")
                        .header("Authorization", ApiTestClient.bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("url").asText();
        assertEquals(issued, URI.create(current).getPath());
    }

    private long createEvent(String accessToken, String body) throws Exception {
        return om.readTree(mvc.perform(post("/api/events")
                        .header("Authorization", ApiTestClient.bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String readFeed(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String issueFeedPath(String accessToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/calendar-feed")
                        .header("Authorization", ApiTestClient.bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        String url = om.readTree(result.getResponse().getContentAsString()).get("url").asText();
        return URI.create(url).getPath();
    }
}
