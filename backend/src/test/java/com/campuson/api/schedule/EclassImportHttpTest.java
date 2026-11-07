package com.campuson.api.schedule;

import static com.campuson.api.support.ApiTestClient.bearer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campuson.api.eclass.EclassCourse;
import com.campuson.api.eclass.EclassSnapshot;
import com.campuson.api.eclass.EclassTask;
import com.campuson.api.member.MemberRepository;
import com.campuson.api.support.ApiTestClient;
import com.campuson.api.support.EclassTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class EclassImportHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired EclassImportService importer;
    @Autowired MemberRepository members;
    @Autowired EventRepository events;
    @Autowired PlatformTransactionManager transactions;

    @Test void mergedAliasesRemoveOnlyOwnersDuplicateAndPreserveCanonicalIdAndDone() throws Exception {
        String token=ApiTestClient.loginAccessToken(mvc,om), other=ApiTestClient.loginAccessToken(mvc,om);
        long owner=memberId(token), stranger=memberId(other);
        String canonical="canonical-"+UUID.randomUUID(), alias="alias-"+UUID.randomUUID();
        Event main=events.save(Event.imported(members.findById(owner).orElseThrow(),EventType.ASSIGNMENT,"과제",null,canonical,Instant.now()));
        Event duplicate=Event.imported(members.findById(owner).orElseThrow(),EventType.ASSIGNMENT,"과제",null,alias,Instant.now());
        duplicate.markDone(true); duplicate=events.save(duplicate);
        Event unrelated=events.save(Event.imported(members.findById(stranger).orElseThrow(),EventType.ASSIGNMENT,"과제",null,alias,Instant.now()));
        Event personal=events.save(Event.personal(members.findById(owner).orElseThrow(),"과제",null,null,Instant.parse("2099-05-03T00:00:00Z"),Instant.now()));
        apply(owner,new EclassSnapshot(List.of(),List.of(new EclassTask(canonical,null,null,"과제","assignment",null,null,false,null,null,"item",List.of(alias)))));
        assertTrue(events.findById(main.getId()).orElseThrow().isDone());
        assertFalse(events.existsById(duplicate.getId()));
        assertTrue(events.existsById(unrelated.getId())); assertTrue(events.existsById(personal.getId()));
        apply(owner,new EclassSnapshot(List.of(),List.of(new EclassTask(canonical,null,null,"과제","assignment",null,null,false,null,null,"item",List.of(alias)))));
        assertTrue(events.findById(main.getId()).orElseThrow().isDone());
    }

    @Test
    void repeatedAndPartialImportsKeepIdentityDonePersonalEventsAndOwnerIsolation() throws Exception {
        String owner = ApiTestClient.loginAccessToken(mvc, om);
        String stranger = ApiTestClient.loginAccessToken(mvc, om);
        long memberId = memberId(owner);
        String key = "A" + UUID.randomUUID().toString().replace("-", "");
        String external = key + "-task";
        Instant due = Instant.parse("2099-05-02T15:00:00Z"); // May 3 KST, not May 2
        EclassSnapshot initial = new EclassSnapshot(List.of(new EclassCourse(key, "운영체제")), List.of(
                new EclassTask(external, key, "운영체제", "첫 과제", "assignment", null, due, false,
                        1800, "https://eclass.seoultech.ac.kr/ilos/task/1", null, java.util.List.of())));
        apply(memberId, initial);
        JsonNode first = calendar(owner);
        assertEquals(1, first.size());
        long importedId = first.get(0).get("id").asLong();
        long courseId = first.get(0).get("courseId").asLong();
        assertEquals("운영체제", first.get(0).get("courseName").asText());
        assertEquals(1800, first.get(0).get("durationSeconds").asInt());
        assertEquals("https://eclass.seoultech.ac.kr/ilos/task/1", first.get(0).get("sourceUrl").asText());
        assertEquals("2099-05-03T00:00:00+09:00", first.get(0).get("dueAt").asText());
        assertFalse(first.get(0).get("dateOnly").asBoolean());

        mvc.perform(patch("/api/events/{id}/done", importedId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isOk());
        long personal = om.readTree(mvc.perform(post("/api/events").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"개인 약속\",\"dueAt\":\"2099-05-03T12:00:00+09:00\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        apply(memberId, initial);
        apply(memberId, new EclassSnapshot(List.of(new EclassCourse(key, "운영체제 개정")), List.of(
                new EclassTask(external, key, "운영체제 개정", "변경된 과제", "quiz", null, due, false,
                        null, null, null, java.util.List.of()))));
        apply(memberId, new EclassSnapshot(List.of(), List.of()));

        JsonNode stored = calendar(owner);
        assertEquals(2, stored.size());
        JsonNode updated = find(stored, importedId);
        assertEquals("변경된 과제", updated.get("title").asText());
        assertEquals("QUIZ", updated.get("type").asText());
        assertEquals(courseId, updated.get("courseId").asLong());
        assertEquals("운영체제 개정", updated.get("courseName").asText());
        assertTrue(updated.get("done").asBoolean());
        assertTrue(updated.get("durationSeconds").isNull());
        assertTrue(updated.get("sourceUrl").isNull());
        assertNotNull(find(stored, personal));
        assertTrue(events.findById(importedId).orElseThrow().isDone());
        mvc.perform(patch("/api/events/{id}/done", importedId).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"done\":false}"))
                .andExpect(status().isNotFound());
        assertEquals(0, calendar(stranger).size());
        mvc.perform(get("/api/courses").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(courseId)).andExpect(jsonPath("$[0].externalId").value(key));
        mvc.perform(get("/api/courses").header("Authorization", bearer(stranger)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        apply(memberId(stranger), initial);
        JsonNode other = calendar(stranger);
        assertEquals(1, other.size());
        assertNotEquals(importedId, other.get(0).get("id").asLong());
        assertEquals(courseId, other.get(0).get("courseId").asLong());
        assertFalse(other.get(0).get("done").asBoolean());
    }

    @Test
    void undatedTasksStayVisibleAndDateOnlyTasksProduceAllDayIcsWithStableUid() throws Exception {
        String owner = ApiTestClient.loginAccessToken(mvc, om);
        String other = ApiTestClient.loginAccessToken(mvc, om);
        long memberId = memberId(owner);
        String key = "A" + UUID.randomUUID().toString().replace("-", "");
        EclassSnapshot snapshot = new EclassSnapshot(List.of(new EclassCourse(key, "데이터베이스")), List.of(
                new EclassTask(key + "-undated", key, "데이터베이스", "마감 미정", "lecture", null, null, false, null, null, null, java.util.List.of()),
                new EclassTask(key + "-dated", key, "데이터베이스", "날짜만 있는 시험", "exam", null,
                        Instant.parse("2099-05-02T15:00:00Z"), true, null, null, null, java.util.List.of())));
        apply(memberId, snapshot);
        apply(memberId, snapshot);
        JsonNode undated = om.readTree(mvc.perform(get("/api/events/undated").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, undated.size());
        JsonNode task = undated.get(0);
        assertEquals("마감 미정", task.get("title").asText());
        assertTrue(task.get("dueAt").isNull());
        assertTrue(task.get("dDay").isNull());
        assertTrue(task.get("durationSeconds").isNull());
        assertTrue(task.get("sourceUrl").isNull());
        mvc.perform(get("/api/events/undated").header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        JsonNode calendar = calendar(owner);
        assertEquals(1, calendar.size());
        long datedId = calendar.get(0).get("id").asLong();
        assertTrue(calendar.get(0).get("dateOnly").asBoolean());
        String feedUrl = om.readTree(mvc.perform(post("/api/calendar-feed").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("url").asText();
        String ics = mvc.perform(get(URI.create(feedUrl).getPath()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(ics.contains("UID:event-" + datedId + "@campus-on\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20990503\r\n"));
        assertFalse(ics.contains("UID:event-" + task.get("id").asLong() + "@campus-on"));
        assertFalse(ics.contains("DTSTART:"));
    }

    @Test
    void courseNameWithoutStableCourseKeyDoesNotInventCatalogIdentity() throws Exception {
        String owner = ApiTestClient.loginAccessToken(mvc, om);
        apply(memberId(owner), new EclassSnapshot(List.of(), List.of(
                new EclassTask(UUID.randomUUID().toString(), null, "과목명만 제공", "일정", "assignment",
                        null, Instant.parse("2099-05-03T03:00:00Z"), false, null, null, null, java.util.List.of()))));
        JsonNode stored = calendar(owner).get(0);
        assertTrue(stored.get("courseId").isNull());
        assertEquals("과목명만 제공", stored.get("courseName").asText());
        mvc.perform(get("/api/courses").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unnamedNewCourseIsNotCreatedButItsTaskIsStillImported() throws Exception {
        String owner = ApiTestClient.loginAccessToken(mvc, om);
        String key = "A" + UUID.randomUUID().toString().replace("-", "");
        apply(memberId(owner), new EclassSnapshot(List.of(new EclassCourse(key, null)), List.of(
                new EclassTask(key + "-task", key, null, "이름 없는 과목 과제", "assignment",
                        null, Instant.parse("2099-05-03T03:00:00Z"), false, null, null, null, java.util.List.of()))));
        JsonNode stored = calendar(owner);
        assertEquals(1, stored.size());
        assertTrue(stored.get(0).get("courseId").isNull());
        assertTrue(stored.get(0).get("courseName").isNull());
        mvc.perform(get("/api/courses").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unnamedSnapshotKeepsExistingCourseName() throws Exception {
        String owner = ApiTestClient.loginAccessToken(mvc, om);
        long memberId = memberId(owner);
        String key = "A" + UUID.randomUUID().toString().replace("-", "");
        apply(memberId, new EclassSnapshot(List.of(new EclassCourse(key, "알고리즘")), List.of()));
        apply(memberId, new EclassSnapshot(List.of(new EclassCourse(key, null)), List.of(
                new EclassTask(key + "-task", key, null, "과제", "assignment",
                        null, Instant.parse("2099-05-03T03:00:00Z"), false, null, null, null, java.util.List.of()))));
        mvc.perform(get("/api/courses").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("알고리즘"));
        assertFalse(calendar(owner).get(0).get("courseId").isNull());
    }

    @Test
    void overlongTitleIsTruncatedAndOverlongSourceUrlIsDropped() throws Exception {
        String owner = ApiTestClient.loginAccessToken(mvc, om);
        String longUrl = "https://eclass.seoultech.ac.kr/ilos/task?x=" + "a".repeat(2100);
        apply(memberId(owner), new EclassSnapshot(List.of(), List.of(
                new EclassTask(UUID.randomUUID().toString(), null, null, "가".repeat(250), "assignment",
                        null, Instant.parse("2099-05-03T03:00:00Z"), false, null, longUrl, null, java.util.List.of()))));
        JsonNode stored = calendar(owner).get(0);
        assertEquals("가".repeat(200), stored.get("title").asText());
        assertTrue(stored.get("sourceUrl").isNull());
    }

    private void apply(long memberId, EclassSnapshot snapshot) {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                importer.apply(members.findById(memberId).orElseThrow(), snapshot));
    }

    private long memberId(String token) throws Exception {
        return om.readTree(mvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode calendar(String token) throws Exception {
        return om.readTree(mvc.perform(get("/api/events").header("Authorization", bearer(token))
                        .param("from", "2099-05-03").param("to", "2099-05-03"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode find(JsonNode list, long id) {
        for (JsonNode item : list) {
            if (item.get("id").asLong() == id) return item;
        }
        return null;
    }
}
