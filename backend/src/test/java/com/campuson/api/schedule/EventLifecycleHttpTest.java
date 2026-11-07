package com.campuson.api.schedule;

import static com.campuson.api.support.ApiTestClient.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campuson.api.member.MemberRepository;
import com.campuson.api.support.ApiTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.campuson.api.support.EclassTestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 일정 수정·삭제·소유권과 요청 형식 오류를 HTTP로 검증한다.
 * 매 테스트 새 계정을 만들고 2099년 날짜를 써서 다른 테스트 데이터와 섞이지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class EventLifecycleHttpTest {

    private static final String WEEK_FROM = "2099-03-01";
    private static final String WEEK_TO = "2099-03-07";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MemberRepository memberRepository;
    @Autowired EventRepository eventRepository;

    @Test
    void updateReplacesEveryFieldIncludingClearingOptionalOnes() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long id = create(token, """
                {"title":"원래 제목","place":"미래관 101","startsAt":"2099-03-02T09:00:00+09:00","dueAt":"2099-03-02T10:00:00+09:00"}""");

        // PUT은 전체 교체: 보내지 않은 place·startsAt은 비워져야 한다
        mvc.perform(put("/api/events/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"바뀐 제목\",\"dueAt\":\"2099-03-05T18:00:00+09:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바뀐 제목"));

        JsonNode stored = findInWeek(token, id);
        assertNotNull(stored, "수정한 일정이 조회되어야 한다");
        assertEquals("바뀐 제목", stored.get("title").asText());
        assertEquals("2099-03-05T18:00:00+09:00", stored.get("dueAt").asText());
        assertTrue(stored.get("place").isNull());
        assertTrue(stored.get("startsAt").isNull());
    }

    @Test
    void deleteRemovesEventAndSecondDeleteReturns404() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long id = create(token, "{\"title\":\"지울 일정\",\"dueAt\":\"2099-03-03T12:00:00+09:00\"}");

        mvc.perform(delete("/api/events/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertNull(findInWeek(token, id));
        mvc.perform(delete("/api/events/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void otherMemberCannotUpdateOrDeleteAndOriginalStaysIntact() throws Exception {
        String owner = ApiTestClient.loginAccessToken(mvc, om);
        String stranger = ApiTestClient.loginAccessToken(mvc, om);
        long id = create(owner, "{\"title\":\"내 일정\",\"dueAt\":\"2099-03-04T12:00:00+09:00\"}");

        mvc.perform(put("/api/events/{id}", id).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"탈취\",\"dueAt\":\"2099-03-04T12:00:00+09:00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mvc.perform(delete("/api/events/{id}", id).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

        JsonNode stored = findInWeek(owner, id);
        assertNotNull(stored, "남의 DELETE 뒤에도 원본이 남아 있어야 한다");
        assertEquals("내 일정", stored.get("title").asText());
        assertNull(findInWeek(stranger, id), "남의 일정은 내 목록에 보이지 않아야 한다");
    }

    @Test
    void importedEclassEventCannotBeUpdatedOrDeletedButCanBeMarkedDone() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long memberId = me(token).get("id").asLong();
        Event imported = eventRepository.save(Event.imported(memberRepository.findById(memberId).orElseThrow(),
                EventType.ASSIGNMENT, "e-class 과제", Instant.parse("2099-03-05T14:59:00Z"),
                "eclass-" + UUID.randomUUID(), Instant.now()));

        mvc.perform(put("/api/events/{id}", imported.getId()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"바꾸기\",\"dueAt\":\"2099-03-06T12:00:00+09:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_EDITABLE"));
        mvc.perform(delete("/api/events/{id}", imported.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_EDITABLE"));
        // 완료 표시는 사용자 상태라 e-class 일정에도 허용된다
        mvc.perform(patch("/api/events/{id}/done", imported.getId()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));

        JsonNode stored = findInWeek(token, imported.getId());
        assertNotNull(stored);
        assertEquals("e-class 과제", stored.get("title").asText());
        assertEquals("ECLASS", stored.get("source").asText());
        assertEquals("ASSIGNMENT", stored.get("type").asText());
    }

    @Test
    void personalTitleIsLimitedTo80WhileImportedTitleMayBeLonger() throws Exception {
        // 사용자 결정 C12: 화면07의 80자는 개인 일정(POST·PUT)에만 적용, e-class 일정·DB는 200자
        String token = ApiTestClient.loginAccessToken(mvc, om);
        long id = create(token, "{\"title\":\"" + "가".repeat(80) + "\",\"dueAt\":\"2099-03-02T09:00:00+09:00\"}");

        mvc.perform(post("/api/events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "가".repeat(81) + "\",\"dueAt\":\"2099-03-02T09:00:00+09:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        mvc.perform(put("/api/events/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "가".repeat(81) + "\",\"dueAt\":\"2099-03-02T09:00:00+09:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        long memberId = me(token).get("id").asLong();
        String longTitle = "e".repeat(150);
        Event imported = eventRepository.save(Event.imported(memberRepository.findById(memberId).orElseThrow(),
                EventType.ASSIGNMENT, longTitle, Instant.parse("2099-03-03T00:00:00Z"),
                "eclass-" + UUID.randomUUID(), Instant.now()));
        assertEquals(longTitle, findInWeek(token, imported.getId()).get("title").asText());
        assertEquals("가".repeat(80), findInWeek(token, id).get("title").asText());
    }

    @Test
    void nonPositiveIdReturns400WithFieldName() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);

        mvc.perform(delete("/api/events/0").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void missingOrMalformedDateParamReturns400() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);

        mvc.perform(get("/api/events").header("Authorization", bearer(token)).param("from", WEEK_FROM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("필수 파라미터 'to'가 없습니다."));
        mvc.perform(get("/api/events").header("Authorization", bearer(token))
                        .param("from", "2099/03/01").param("to", WEEK_TO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("'from' 파라미터 형식이 올바르지 않습니다."));
    }

    @Test
    void malformedJsonBodyReturns400() throws Exception {
        String token = ApiTestClient.loginAccessToken(mvc, om);

        mvc.perform(post("/api/events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("요청 본문을 읽을 수 없습니다."));
    }

    @Test
    void eventsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/events").param("from", WEEK_FROM).param("to", WEEK_TO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private long create(String token, String body) throws Exception {
        return json(mvc.perform(post("/api/events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode me(String token) throws Exception {
        return json(mvc.perform(get("/api/me").header("Authorization", bearer(token))).andExpect(status().isOk()));
    }

    private JsonNode findInWeek(String token, long id) throws Exception {
        JsonNode list = json(mvc.perform(get("/api/events").header("Authorization", bearer(token))
                        .param("from", WEEK_FROM).param("to", WEEK_TO))
                .andExpect(status().isOk()));
        for (JsonNode node : list) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return om.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
