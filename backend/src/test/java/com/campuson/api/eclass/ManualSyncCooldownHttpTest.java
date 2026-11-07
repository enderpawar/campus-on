package com.campuson.api.eclass;

import static com.campuson.api.support.ApiTestClient.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campuson.api.support.ApiTestClient;
import com.campuson.api.support.EclassTestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 수동 동기화는 마지막 시도(로그인 포함) 후 쿨다운 동안 학교에 요청하지 않는다. */
@SpringBootTest(properties = "campus.eclass.manual-sync-cooldown=PT1M")
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class ManualSyncCooldownHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired EclassTestConfiguration.FakeEclass school;

    @Test void manualSyncRightAfterLoginIsRejectedWithRetryAfter() throws Exception {
        String studentId = "student-" + UUID.randomUUID();
        String access = ApiTestClient.login(mvc, om, studentId).get("accessToken").asText();
        school.expireSessions(studentId); // would force a school login if the request got through

        mvc.perform(post("/api/eclass/sync").header("Authorization", bearer(access)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]?")));
        assertEquals(1, school.authCalls(studentId));
    }
}
