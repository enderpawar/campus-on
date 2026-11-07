package com.campuson.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public final class ApiTestClient {
    public static final String PASSWORD = "password123!";
    public static final String SEOULTECH = "서울과학기술대학교";
    private ApiTestClient() {}
    public static String loginAccessToken(MockMvc mvc, ObjectMapper om) {
        return login(mvc, om).get("accessToken").asText();
    }
    public static JsonNode login(MockMvc mvc, ObjectMapper om) {
        return login(mvc, om, "student-" + UUID.randomUUID());
    }
    public static JsonNode login(MockMvc mvc, ObjectMapper om, String studentId) {
        try {
            String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("studentId", studentId, "password", PASSWORD))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            return om.readTree(body);
        } catch (Exception ex) { throw new IllegalStateException("테스트 e-class 로그인 실패", ex); }
    }
    public static long seoultechId(MockMvc mvc, ObjectMapper om) throws Exception {
        JsonNode schools = om.readTree(mvc.perform(get("/api/schools")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode school : schools) if (SEOULTECH.equals(school.get("name").asText())) return school.get("id").asLong();
        throw new IllegalStateException(SEOULTECH + " 시드가 없습니다.");
    }
    public static String bearer(String accessToken) { return "Bearer " + accessToken; }
}
