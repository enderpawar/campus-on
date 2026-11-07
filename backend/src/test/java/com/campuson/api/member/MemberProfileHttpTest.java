package com.campuson.api.member;

import static com.campuson.api.support.ApiTestClient.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campuson.api.support.ApiTestClient;
import com.campuson.api.support.EclassTestConfiguration;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 프로필(학과·학년) 수정 PATCH /api/me (사용자 결정 C3, 명세 F1:43 프로필은 F5 추천의 입력).
 * 생략=유지, null=비우기, 값=변경. 저장 결과는 별도 GET으로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class MemberProfileHttpTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void patchChangesOnlySentFieldsAndPersists() throws Exception {
        String access = account("컴퓨터공학과", 2);

        patchMe(access, "{\"grade\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(3))
                .andExpect(jsonPath("$.department").value("컴퓨터공학과"));

        mvc.perform(get("/api/me").header("Authorization", bearer(access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(3))
                .andExpect(jsonPath("$.department").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.schoolName").value(ApiTestClient.SEOULTECH));
    }

    @Test
    void explicitNullClearsFieldWhileOmittedFieldIsKept() throws Exception {
        String access = account("전자공학과", 4);

        patchMe(access, "{\"department\":null}").andExpect(status().isOk());

        mvc.perform(get("/api/me").header("Authorization", bearer(access)))
                .andExpect(jsonPath("$.department").doesNotExist())
                .andExpect(jsonPath("$.grade").value(4));
    }

    @Test
    void emptyBodyChangesNothing() throws Exception {
        String access = account("기계공학과", 1);

        patchMe(access, "{}").andExpect(status().isOk());

        mvc.perform(get("/api/me").header("Authorization", bearer(access)))
                .andExpect(jsonPath("$.department").value("기계공학과"))
                .andExpect(jsonPath("$.grade").value(1));
    }

    @Test
    void invalidValuesReturn400AndKeepProfile() throws Exception {
        String access = account("산업공학과", 2);

        patchMe(access, "{\"grade\":7}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patchMe(access, om.writeValueAsString(Map.of("department", "가".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get("/api/me").header("Authorization", bearer(access)))
                .andExpect(jsonPath("$.department").value("산업공학과"))
                .andExpect(jsonPath("$.grade").value(2));
    }

    @Test
    void patchTouchesOnlyMyProfile() throws Exception {
        String mine = account("화학공학과", 1);
        String other = account("화학공학과", 1);

        patchMe(mine, "{\"grade\":2}").andExpect(status().isOk());

        mvc.perform(get("/api/me").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.grade").value(1));
    }

    @Test
    void patchRequiresAuthentication() throws Exception {
        mvc.perform(patch("/api/me").contentType(MediaType.APPLICATION_JSON).content("{\"grade\":3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private String account(String department, int grade) throws Exception {
        String access = ApiTestClient.loginAccessToken(mvc, om);
        patchMe(access, om.writeValueAsString(Map.of("department", department, "grade", grade)))
                .andExpect(status().isOk());
        return access;
    }

    private ResultActions patchMe(String access, String body) throws Exception {
        return mvc.perform(patch("/api/me").header("Authorization", bearer(access))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
