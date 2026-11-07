package com.campuson.api.auth;

import static com.campuson.api.support.ApiTestClient.bearer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.campuson.api.catalog.CourseEnrollmentRepository;
import com.campuson.api.catalog.CourseRepository;
import com.campuson.api.eclass.*;
import com.campuson.api.member.MemberRepository;
import com.campuson.api.schedule.*;
import com.campuson.api.notice.*;
import com.campuson.api.school.SchoolRepository;
import com.campuson.api.support.ApiTestClient;
import com.campuson.api.support.EclassTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class WithdrawalHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MemberRepository members;
    @Autowired EventRepository events;
    @Autowired CalendarFeedRepository feeds;
    @Autowired EclassConnectionRepository connections;
    @Autowired EclassAccountService accounts;
    @Autowired CourseEnrollmentRepository enrollments;
    @Autowired CourseRepository courses;
    @Autowired SchoolRepository schools;
    @Autowired BoardRepository boards;
    @Autowired NoticeRepository notices;
    @Test void withdrawalRequiresAuthentication() throws Exception {
        withdraw(null, ApiTestClient.PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
    @Test void missingBlankMalformedOrWrongPasswordKeepsEverything() throws Exception {
        Account account = accountWithData();
        for (String body : new String[] {"{}", "{\"password\":\"   \"}", "{\"password\":"}) {
            mvc.perform(delete("/api/me").header("Authorization", bearer(account.access))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        withdraw(account.access, "wrong-password!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        assertTrue(members.existsById(account.memberId));
        assertTrue(events.existsById(account.personalId)); assertTrue(events.existsById(account.importedId));
        assertTrue(connections.existsById(account.memberId));
        assertFalse(enrollments.findCoursesByMemberId(account.memberId).isEmpty());
        mvc.perform(get(account.feedPath)).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", bearer(account.access))).andExpect(status().isOk());
    }
    @Test void withdrawalDeletesPrivateDataAndOldTokensNeverReviveOnReentry() throws Exception {
        Account account = accountWithData();
        withdraw(account.access, ApiTestClient.PASSWORD).andExpect(status().isNoContent());
        assertFalse(members.existsById(account.memberId));
        assertFalse(events.existsById(account.personalId)); assertFalse(events.existsById(account.importedId));
        assertTrue(feeds.findByMemberId(account.memberId).isEmpty());
        assertFalse(connections.existsById(account.memberId));
        assertTrue(enrollments.findCoursesByMemberId(account.memberId).isEmpty());
        assertTrue(courses.existsById(account.courseId), "shared catalog course survives");
        mvc.perform(get("/api/me").header("Authorization", bearer(account.access))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("refreshToken", account.refresh))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(get(account.feedPath)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CALENDAR_FEED_NOT_FOUND"));
        // School identity still exists upstream: a later valid login provisions a fresh app identity.
        String fresh = ApiTestClient.login(mvc, om, account.studentId).get("accessToken").asText();
        long freshId = json(mvc.perform(get("/api/me").header("Authorization", bearer(fresh))).andExpect(status().isOk())).get("id").asLong();
        assertNotEquals(account.memberId, freshId);
        assertTrue(enrollments.findCoursesByMemberId(freshId).isEmpty());
        mvc.perform(get("/api/me").header("Authorization", bearer(account.access))).andExpect(status().isUnauthorized());
    }
    @Test void withdrawalLeavesOtherMembersSharedSchoolAndNoticesIntact() throws Exception {
        Account leaving = accountWithData(); Account staying = accountWithData();
        var school = schools.findByName(ApiTestClient.SEOULTECH).orElseThrow();
        Board board = boards.save(Board.collected(school, "탈퇴검증-" + UUID.randomUUID()));
        Notice notice = notices.save(Notice.collected(board, NoticeCategory.ACADEMIC, "공유 공지",
                "https://example.invalid/withdrawal/" + UUID.randomUUID(), "본문\n\n- 항목", "요약", null, List.of(), Instant.now()));
        withdraw(leaving.access, ApiTestClient.PASSWORD).andExpect(status().isNoContent());
        assertTrue(members.existsById(staying.memberId)); assertTrue(events.existsById(staying.personalId));
        assertTrue(events.existsById(staying.importedId)); assertTrue(connections.existsById(staying.memberId));
        assertFalse(enrollments.findCoursesByMemberId(staying.memberId).isEmpty());
        mvc.perform(get(staying.feedPath)).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", bearer(staying.access))).andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value(ApiTestClient.SEOULTECH));
        mvc.perform(get("/api/notices/{id}", notice.getId()).header("Authorization", bearer(staying.access)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(notice.getId()))
                .andExpect(jsonPath("$.title").value("공유 공지")).andExpect(jsonPath("$.url").value(notice.getUrl()));
    }
    private Account accountWithData() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        JsonNode tokens = ApiTestClient.login(mvc, om, studentId); String access = tokens.get("accessToken").asText();
        long memberId = json(mvc.perform(get("/api/me").header("Authorization", bearer(access))).andExpect(status().isOk())).get("id").asLong();
        accounts.synchronizedSnapshot(memberId, accounts.context(memberId).generation(), new EclassSnapshot(List.of(new EclassCourse("c-" + UUID.randomUUID(), "테스트 과목")), List.of()), "SESSION=" + studentId);
        long courseId = enrollments.findCoursesByMemberId(memberId).getFirst().getId();
        long personal = json(mvc.perform(post("/api/events").header("Authorization", bearer(access))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"개인 일정\",\"dueAt\":\"2099-04-01T09:00:00+09:00\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
        long imported = events.save(Event.imported(members.findById(memberId).orElseThrow(), EventType.ASSIGNMENT,
                "e-class 과제", Instant.parse("2099-04-02T00:00:00Z"), "eclass-" + UUID.randomUUID(), Instant.now())).getId();
        String url = json(mvc.perform(post("/api/calendar-feed").header("Authorization", bearer(access))).andExpect(status().isOk())).get("url").asText();
        return new Account(studentId, memberId, access, tokens.get("refreshToken").asText(), personal, imported, URI.create(url).getPath(), courseId);
    }
    private ResultActions withdraw(String access, String password) throws Exception {
        var request = delete("/api/me").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("password", password)));
        if (access != null) request.header("Authorization", bearer(access));
        return mvc.perform(request);
    }
    private JsonNode json(ResultActions actions) throws Exception { return om.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)); }
    private record Account(String studentId, long memberId, String access, String refresh, long personalId, long importedId, String feedPath, long courseId) {}
}
