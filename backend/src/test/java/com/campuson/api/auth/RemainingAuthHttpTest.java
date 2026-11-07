package com.campuson.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.campuson.api.support.*;
import com.campuson.api.global.security.*;
import com.campuson.api.eclass.*;
import com.campuson.api.member.*;
import com.campuson.api.schedule.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({EclassTestConfiguration.class, RemainingAuthHttpTest.TimeConfig.class})
class RemainingAuthHttpTest {
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock testTime() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        volatile Instant now=Instant.parse("2030-01-01T00:00:00Z");
        @Override public Instant instant(){return now;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired EclassTestConfiguration.FakeEclass school;
    @Autowired JwtProvider jwt;
    @Autowired MemberRepository members;
    @Autowired EclassConnectionRepository connections;
    @Autowired EventRepository events;
    @BeforeEach void resetTime(){clock.now=Instant.parse("2030-01-01T00:00:00Z");}
    private record Account(String student, String access, String refresh, long id) {}
    private Account account() throws Exception {
        String id="offline-"+UUID.randomUUID(); JsonNode tokens=ApiTestClient.login(mvc,om,id);
        String access=tokens.get("accessToken").asText();
        long member=om.readTree(mvc.perform(get("/api/me").header("Authorization","Bearer "+access)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        return new Account(id,access,tokens.get("refreshToken").asText(),member);
    }
    private ResultActions withdraw(String access,String password)throws Exception {
        return mvc.perform(delete("/api/me").header("Authorization","Bearer "+access).contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("password",password))));
    }
    @Test void outageWithRecentAuthenticationDeletesDataAndRevokesTokenAndIcs() throws Exception {
        Account a=account();
        String url=om.readTree(mvc.perform(post("/api/calendar-feed").header("Authorization","Bearer "+a.access)).andReturn().getResponse().getContentAsString()).get("url").asText();
        long event=events.save(Event.imported(members.findById(a.id).orElseThrow(),EventType.ASSIGNMENT,"과제",null,"item",clock.instant())).getId();
        school.unavailable(a.student);
        withdraw(a.access,ApiTestClient.PASSWORD).andExpect(status().isNoContent());
        assertFalse(members.existsById(a.id)); assertFalse(events.existsById(event)); assertFalse(connections.existsById(a.id));
        mvc.perform(get("/api/me").header("Authorization","Bearer "+a.access)).andExpect(status().isUnauthorized());
        mvc.perform(get(java.net.URI.create(url).getPath())).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("refreshToken",a.refresh)))).andExpect(status().isUnauthorized());
    }
    @Test void outageWithOldAuthenticationPreservesAllPrivateData()throws Exception {
        Account a=account();
        long event=events.save(Event.imported(members.findById(a.id).orElseThrow(),EventType.ASSIGNMENT,"보존 과제",null,"old-auth-item",clock.instant())).getId();
        String url=om.readTree(mvc.perform(post("/api/calendar-feed").header("Authorization","Bearer "+a.access)).andReturn().getResponse().getContentAsString()).get("url").asText();
        school.unavailable(a.student); clock.now=clock.now.plusSeconds(601);
        withdraw(a.access,ApiTestClient.PASSWORD).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("WITHDRAWAL_REAUTH_UNAVAILABLE"));
        assertTrue(members.existsById(a.id)); assertTrue(connections.existsById(a.id)); assertTrue(events.existsById(event));
        mvc.perform(get(java.net.URI.create(url).getPath())).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+a.access)).andExpect(status().isOk());
    }
    @Test void refreshDoesNotRenewAuthenticationTimeOrOfflineEligibility()throws Exception {
        Account a=account(); Long original=jwt.verify(a.access,TokenType.ACCESS).orElseThrow().authTime();
        clock.now=clock.now.plusSeconds(600);
        String body=mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("refreshToken",a.refresh))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access=om.readTree(body).get("accessToken").asText();
        assertEquals(original,jwt.verify(access,TokenType.ACCESS).orElseThrow().authTime());
        clock.now=clock.now.plusSeconds(1); school.unavailable(a.student);
        withdraw(access,ApiTestClient.PASSWORD).andExpect(status().isServiceUnavailable()); assertTrue(members.existsById(a.id));
    }
    @Test void missingAuthTimeCannotUseOfflineWithdrawal()throws Exception {
        Account a=account(); school.unavailable(a.student);
        String access=jwt.issue(a.id,members.findById(a.id).orElseThrow().getTokenVersion(),null).accessToken();
        withdraw(access,ApiTestClient.PASSWORD).andExpect(status().isServiceUnavailable()); assertTrue(members.existsById(a.id));
    }
    @Test void wrongPasswordNeverUsesRecentAuthenticationFallback()throws Exception {
        Account a=account(); withdraw(a.access,"wrong").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        assertTrue(members.existsById(a.id));
    }
    @Test void normalizedStudentUsesSameAccountAndThrottleBudgetAndInvalidInnerCharactersAre400()throws Exception {
        String student="2026"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String full=" ２０２６"+student.substring(4)+" ";
        JsonNode normal=ApiTestClient.login(mvc,om,student), normalized=ApiTestClient.login(mvc,om,full);
        assertEquals(jwt.verify(normal.get("accessToken").asText(),TokenType.ACCESS).orElseThrow().memberId(),jwt.verify(normalized.get("accessToken").asText(),TokenType.ACCESS).orElseThrow().memberId());
        for(int i=0;i<5;i++) login(i%2==0?student:full,"wrong").andExpect(status().isUnauthorized());
        login(student,ApiTestClient.PASSWORD).andExpect(status().isTooManyRequests());
        for(String id:List.of("20 26","20\t26","20\u000026","20\u200b26")) login(id,ApiTestClient.PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    private ResultActions login(String student,String password)throws Exception {
        return mvc.perform(post("/api/auth/login").with(request->{request.setRemoteAddr("192.0.2.123");return request;})
            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("studentId",student,"password",password))));
    }
}
