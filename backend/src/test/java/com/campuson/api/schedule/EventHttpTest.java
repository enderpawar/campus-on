package com.campuson.api.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campuson.api.support.ApiTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import com.campuson.api.support.EclassTestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 일정 API 통합 테스트. 테스트끼리 DB를 공유하므로 {@code @Transactional} 대신
 * 매번 새 계정을 만들고 내가 만든 id로만 단언한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class EventHttpTest {

    /** KST 2026-11-06(금) 12:00. ClockConfig의 {@code clock} 빈과 이름이 겹치지 않게 {@code fixedClock}으로 등록하고 @Primary로 우선한다. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void createdPersonalEventAppearsInWeekWithDday() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long id = createEvent(token, "{\"title\":\"데이터베이스 과제\",\"dueAt\":\"2026-11-08T14:00:00+09:00\"}");

        MvcResult result = mvc.perform(get("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-08"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode found = findById(om.readTree(result.getResponse().getContentAsString()), id);
        assertNotNull(found, "만든 일정이 주간 조회에 있어야 한다");
        assertEquals(2, found.get("dDay").asLong());
        assertEquals("PERSONAL", found.get("source").asText());
    }

    @Test
    void otherMembersEventReturns404() throws Exception {
        String owner = ApiTestClient.loginAccessToken(mvc, om);
        String stranger = ApiTestClient.loginAccessToken(mvc, om);
        long id = createEvent(owner, "{\"title\":\"내 일정\",\"dueAt\":\"2026-11-08T14:00:00+09:00\"}");

        mvc.perform(patch("/api/events/" + id + "/done")
                        .header("Authorization", ApiTestClient.bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void startNotBeforeDueReturns400() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);

        mvc.perform(post("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"회의\",\"startsAt\":\"2026-11-08T14:00:00+09:00\",\"dueAt\":\"2026-11-08T14:00:00+09:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_TIME"));
    }

    @Test
    void reversedRangeReturns400() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);

        mvc.perform(get("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .param("from", "2026-11-08")
                        .param("to", "2026-11-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void rangeAllowsExactly62InclusiveDays() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);

        // 2026-11-01 ~ 2027-01-01: 양 끝 포함 62일 → 허용, 하루 늘리면(63일) 거부
        mvc.perform(get("/api/events").header("Authorization", ApiTestClient.bearer(token))
                        .param("from", "2026-11-01").param("to", "2027-01-01"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/events").header("Authorization", ApiTestClient.bearer(token))
                        .param("from", "2026-11-01").param("to", "2027-01-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void unicodeWhitespaceOnlyTitleReturns400NotConflict() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);

        mvc.perform(post("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\u3000\",\"dueAt\":\"2026-11-08T14:00:00+09:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void markDonePersistsWithoutExplicitSave() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long id = createEvent(token, "{\"title\":\"완료할 일정\",\"dueAt\":\"2026-11-07T09:00:00+09:00\"}");

        mvc.perform(patch("/api/events/" + id + "/done")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));

        MvcResult result = mvc.perform(get("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-08"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode found = findById(om.readTree(result.getResponse().getContentAsString()), id);
        assertNotNull(found);
        assertEquals(true, found.get("done").asBoolean());
    }

    @Test
    void rangeUsesKstDayBoundaries() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long startOfDay = createEvent(token, "{\"title\":\"자정\",\"dueAt\":\"2026-11-10T00:00:00+09:00\"}");
        long endOfDay = createEvent(token, "{\"title\":\"막차\",\"dueAt\":\"2026-11-10T23:59:59+09:00\"}");
        long dayBefore = createEvent(token, "{\"title\":\"전날\",\"dueAt\":\"2026-11-09T23:59:59+09:00\"}");
        long nextDay = createEvent(token, "{\"title\":\"다음날\",\"dueAt\":\"2026-11-11T00:00:00+09:00\"}");

        MvcResult result = mvc.perform(get("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .param("from", "2026-11-10")
                        .param("to", "2026-11-10"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = om.readTree(result.getResponse().getContentAsString());
        // UTC 날짜로 자르면 KST 00:00~08:59 마감이 전날로 밀린다: KST 하루 [00:00, 다음날 00:00)만 들어와야 한다
        assertNotNull(findById(list, startOfDay));
        assertNotNull(findById(list, endOfDay));
        assertNull(findById(list, dayBefore));
        assertNull(findById(list, nextDay));
        assertEquals(startOfDay, list.get(0).get("id").asLong(), "마감 시각 오름차순이어야 한다");
    }

    @Test
    void dDayFollowsKstCalendarDateNotUtc() throws Exception {
        // 고정 시각 KST 2026-11-06 12:00
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long todayEarly = createEvent(token, "{\"title\":\"오늘 새벽\",\"dueAt\":\"2026-11-06T00:30:00+09:00\"}");
        long yesterday = createEvent(token, "{\"title\":\"어제\",\"dueAt\":\"2026-11-05T23:59:00+09:00\"}");
        long tomorrowMidnight = createEvent(token, "{\"title\":\"내일 자정\",\"dueAt\":\"2026-11-07T00:00:00+09:00\"}");

        MvcResult result = mvc.perform(get("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .param("from", "2026-11-05")
                        .param("to", "2026-11-07"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = om.readTree(result.getResponse().getContentAsString());
        assertEquals(0, findById(list, todayEarly).get("dDay").asLong());
        assertEquals(-1, findById(list, yesterday).get("dDay").asLong());
        assertEquals(1, findById(list, tomorrowMidnight).get("dDay").asLong());
    }

    private long createEvent(String token, String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/events")
                        .header("Authorization", ApiTestClient.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private static JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }
}
