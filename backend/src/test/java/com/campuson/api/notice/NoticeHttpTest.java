package com.campuson.api.notice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.campuson.api.school.School;
import com.campuson.api.school.SchoolRepository;
import com.campuson.api.support.ApiTestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.campuson.api.support.EclassTestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** 트랜잭션 롤백 대신 고유 url과 상대 단언으로 격리한다 (HTTP 요청은 다른 스레드·트랜잭션에서 돈다). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EclassTestConfiguration.class)
class NoticeHttpTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired SchoolRepository schoolRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired BoardRepository boardRepository;

    private School seoultech;
    private School otherSchool;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        seoultech = schoolRepository.findAllByOrderByNameAsc().stream()
                .filter(s -> s.getName().equals("서울과학기술대학교")).findFirst().orElseThrow();
        otherSchool = schoolRepository.save(new School("다른학교-" + UUID.randomUUID(), "https://other.invalid"));
        bearer = ApiTestClient.bearer(ApiTestClient.loginAccessToken(mvc, om));
    }

    private Notice save(School school, NoticeCategory category, Instant postedAt) {
        String url = "https://example.invalid/test/" + UUID.randomUUID();
        return noticeRepository.save(Notice.collected(board(school, "테스트부서"), category, "테스트 공지", url,
                null, null, null, List.of(), postedAt));
    }

    private Board board(School school, String source) {
        return boardRepository.findBySchoolIdAndName(school.getId(), source)
                .orElseGet(() -> boardRepository.save(Board.collected(school, source)));
    }

    @Test
    void missingBodyAndUnrestrictedDepartmentsRemainNullAndEmpty() throws Exception {
        Notice notice = save(seoultech, NoticeCategory.ACADEMIC, Instant.parse("2100-01-01T00:00:00Z"));
        mvc.perform(get("/api/notices/{id}", notice.getId()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").isEmpty())
                .andExpect(jsonPath("$.summary").isEmpty())
                .andExpect(jsonPath("$.targetDepartments").isArray())
                .andExpect(jsonPath("$.targetDepartments.length()").value(0));
    }

    @Test
    void otherSchoolsNoticesAreNotListed() throws Exception {
        Notice other = save(otherSchool, NoticeCategory.ACADEMIC, Instant.parse("2100-01-01T00:00:00Z"));

        mvc.perform(get("/api/notices").param("size", "50").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].url", not(hasItem(other.getUrl()))));
    }

    @Test
    void categoryFilterReturnsOnlyThatCategory() throws Exception {
        Instant far = Instant.parse("2100-01-02T00:00:00Z");
        Notice scholarship = save(seoultech, NoticeCategory.SCHOLARSHIP, far);
        Notice career = save(seoultech, NoticeCategory.CAREER, far);

        mvc.perform(get("/api/notices").param("category", "SCHOLARSHIP").param("size", "50")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].category", everyItem(is("SCHOLARSHIP"))))
                .andExpect(jsonPath("$.items[*].url", hasItem(scholarship.getUrl())))
                .andExpect(jsonPath("$.items[*].url", not(hasItem(career.getUrl()))));
    }

    @Test
    void pagingReportsHasNext() throws Exception {
        save(seoultech, NoticeCategory.EVENT, Instant.parse("2100-01-03T00:00:00Z"));
        save(seoultech, NoticeCategory.EVENT, Instant.parse("2100-01-03T00:00:01Z"));

        mvc.perform(get("/api/notices").param("size", "1").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void otherSchoolsNoticeDetailReturns404() throws Exception {
        Notice other = save(otherSchool, NoticeCategory.CAREER, Instant.parse("2100-01-04T00:00:00Z"));

        mvc.perform(get("/api/notices/{id}", other.getId()).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
    }

    @Test
    void sizeAbove50Returns400() throws Exception {
        mvc.perform(get("/api/notices").param("size", "51").header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void pageBeyondOffsetRangeReturns400NotServerError() throws Exception {
        // size=50, page=50,000,000이면 offset이 int를 넘어 JPA가 예외를 던진다: 요청 검증에서 400으로 막아야 한다
        mvc.perform(get("/api/notices").param("page", "50000000").param("size", "50").header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void ownSchoolNoticeDetailReturnsAllFieldsInKst() throws Exception {
        String body = "지원 안내\n\n- 재학생 신청 가능\n- 서류 제출\n\n문의: 장학복지팀";
        Notice notice = noticeRepository.save(Notice.collected(board(seoultech, "장학복지팀"), NoticeCategory.SCHOLARSHIP,
                "상세 테스트 공지", "https://example.invalid/test/" + UUID.randomUUID(), body, "한 줄 요약", 3,
                List.of("컴퓨터공학과", "기계공학과"), Instant.parse("2100-01-05T00:00:00Z")));

        mvc.perform(get("/api/notices/{id}", notice.getId()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notice.getId()))
                .andExpect(jsonPath("$.category").value("SCHOLARSHIP"))
                .andExpect(jsonPath("$.source").value("장학복지팀"))
                .andExpect(jsonPath("$.url").value(notice.getUrl()))
                .andExpect(jsonPath("$.summary").value("한 줄 요약"))
                .andExpect(jsonPath("$.body").value(body))
                .andExpect(jsonPath("$.targetDepartments[0]").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.targetDepartments[1]").value("기계공학과"))
                .andExpect(jsonPath("$.targetGrade").value(3))
                .andExpect(jsonPath("$.postedAt").value("2100-01-05T09:00:00+09:00"));
    }

    @Test
    void listIsNewestFirstWithNewerIdBreakingTies() throws Exception {
        Notice tieOlderId = save(seoultech, NoticeCategory.ACADEMIC, Instant.parse("2100-11-01T00:00:00Z"));
        Notice tieNewerId = save(seoultech, NoticeCategory.ACADEMIC, Instant.parse("2100-11-01T00:00:00Z"));
        Notice newest = save(seoultech, NoticeCategory.ACADEMIC, Instant.parse("2100-12-01T00:00:00Z"));

        mvc.perform(get("/api/notices").param("size", "3").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.items[1].id").value(tieNewerId.getId()))
                .andExpect(jsonPath("$.items[2].id").value(tieOlderId.getId()));
    }

    @Test
    void lastPageReportsNoNext() throws Exception {
        save(seoultech, NoticeCategory.EVENT, Instant.parse("2100-01-06T00:00:00Z"));
        long total = om.readTree(mvc.perform(get("/api/notices").param("size", "1").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("totalElements").asLong();

        mvc.perform(get("/api/notices").param("size", "1").param("page", String.valueOf(total - 1))
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void unknownNoticeIdReturns404() throws Exception {
        mvc.perform(get("/api/notices/{id}", Long.MAX_VALUE).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
    }

    @Test
    void unknownCategoryReturns400() throws Exception {
        mvc.perform(get("/api/notices").param("category", "SPORTS").header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("'category' 파라미터 형식이 올바르지 않습니다."));
    }

    @Test
    void noticesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/notices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
