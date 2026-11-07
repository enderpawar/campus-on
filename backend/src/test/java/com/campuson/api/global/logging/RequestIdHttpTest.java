package com.campuson.api.global.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 클라이언트가 보낸 X-Request-Id의 재사용 규칙 (ADR-0008). 401도 Security 체인 앞의 필터를 거친다. */
@SpringBootTest
@AutoConfigureMockMvc
class RequestIdHttpTest {

    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired MockMvc mvc;

    @Test
    void safeIncomingRequestIdIsReusedInHeaderAndErrorBody() throws Exception {
        mvc.perform(get("/api/me").header(RequestIdFilter.HEADER, "client-req-42"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, "client-req-42"))
                .andExpect(jsonPath("$.requestId").value("client-req-42"));
    }

    @Test
    void unsafeIncomingRequestIdIsReplacedToPreventLogInjection() throws Exception {
        for (String unsafe : new String[] {"abc 123", "abc\r\nINFO fake log", "a".repeat(65)}) {
            String issued = mvc.perform(get("/api/schools").header(RequestIdFilter.HEADER, unsafe))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

            assertNotEquals(unsafe, issued);
            assertTrue(issued.matches(UUID_PATTERN), issued);
        }
    }

    @Test
    void everyResponseGetsRequestIdEvenWithoutIncomingHeader() throws Exception {
        String first = mvc.perform(get("/api/schools")).andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        String second = mvc.perform(get("/api/schools")).andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertTrue(first.matches(UUID_PATTERN), first);
        assertNotEquals(first, second);
        assertEquals(36, second.length());
    }
}
