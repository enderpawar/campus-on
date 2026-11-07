package com.campuson.api.eclass;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EclassHtmlParserTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T15:30:00Z"), ZoneOffset.UTC);
    private final EclassHtmlParser parser = new EclassHtmlParser(CLOCK);
    private final URI base = URI.create("https://eclass.seoultech.ac.kr");

    static String fixture(String name) throws IOException {
        try (InputStream input = EclassHtmlParserTest.class.getResourceAsStream("/eclass/" + name)) {
            if (input == null) throw new IOException("Missing fixture: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void exactPlanaryReportRowSuppliesKstDateAndRealCourse() throws Exception {
        URI page = base.resolve("/ilos/st/course/reportList_form.acl?KJKEY=DESIGN101");
        EclassTask task = parser.tasks(parser.document(fixture("planary-report-list.html"), page), page,
                new EclassCourse("DESIGN101", "인터랙션 디자인")).getFirst();
        assertEquals("인터랙션 과제 1", task.title());
        assertEquals("assignment", task.type());
        assertEquals("DESIGN101", task.courseExternalId());
        assertEquals("인터랙션 디자인", task.courseName());
        assertEquals(Instant.parse("2026-09-22T14:30:00Z"), task.dueAt());
        assertFalse(task.dateOnly());
        assertNull(task.startsAt());
        assertNull(task.durationSeconds());
        assertEquals(base + "/ilos/st/course/reportView.acl?KJKEY=DESIGN101", task.sourceUrl());
    }

    @Test
    void todoWrapperUsesStableKjCategoryAndItemAndNotCategoryAsCourseName() throws Exception {
        URI page = base.resolve("/ilos/main/main_form.acl");
        String html = fixture("planary-todo.html");
        EclassTask task = parser.tasks(parser.document(html, page), page, null).getFirst();
        assertEquals("DESIGN101:report:TASK42", task.externalId());
        assertEquals("인터랙션 디자인", task.courseName());
        assertEquals(Instant.parse("2026-09-22T14:30:00Z"), task.dueAt());
        assertEquals(List.of(new EclassCourse("DESIGN101", "인터랙션 디자인")), parser.courses(parser.document(html, page)));
        String changed = html.replace("9월 22일", "9월 25일");
        assertEquals(task.externalId(), parser.tasks(parser.document(changed, page), page, null).getFirst().externalId());
    }

    @Test
    void kstClockDrivesYearAndDdayAcrossUtcMidnight() {
        assertEquals(Instant.parse("2026-09-22T15:00:00Z"), parser.dateTime("D-2", true));
        assertEquals(Instant.parse("2026-09-19T15:00:00Z"), parser.dateTime("D+1", true));
        assertEquals(Instant.parse("2026-09-21T15:00:00Z"), parser.dateTime("9월 22일", false));
        assertEquals(Instant.parse("2026-09-21T15:00:00Z"), parser.dateTime("2026년 09월 22일 오전 12:00", false));
        assertNull(parser.dateTime("2026-02-30", false));
        assertNull(parser.dateTime("D-999999999999999", true));
        assertNull(parser.dateTime("2026-09-22 오후 25:00", false));
    }

    @Test
    void unknownDeadlineAndCourseNameRemainNullAndDateOnlyIsExplicit() {
        URI page = base.resolve("/ilos/st/course/quizList_form.acl?KJKEY=QUIZ101");
        String html = "<table><tr><td><a href='quizView.acl?quiz_id=1'>첫 번째 퀴즈</a></td><td>추후 공지</td></tr>"
                + "<tr><td><a href='quizView.acl?quiz_id=2'>두 번째 퀴즈</a></td><td>2026-09-23</td></tr></table>";
        List<EclassTask> tasks = parser.tasks(parser.document(html, page), page, new EclassCourse("QUIZ101", null));
        assertNull(tasks.getFirst().courseName());
        assertNull(tasks.getFirst().dueAt());
        assertFalse(tasks.getFirst().dateOnly());
        assertTrue(tasks.get(1).dateOnly());
        assertEquals(Instant.parse("2026-09-22T15:00:00Z"), tasks.get(1).dueAt());
        assertNull(tasks.get(1).durationSeconds());
    }

    @Test
    void durationOnlyComesFromExplicitPageData() {
        URI page = base.resolve("/ilos/st/course/quizList_form.acl?KJKEY=QUIZ101");
        String html = "<table><tr><td>연습용 퀴즈</td><td>제한 시간: 30분</td></tr></table>";
        EclassTask task = parser.tasks(parser.document(html, page), page, new EclassCourse("QUIZ101", null)).getFirst();
        assertEquals(1800, task.durationSeconds());
        assertNull(task.dueAt());
    }

    @Test
    void stableExternalIdDoesNotChangeWhenDeadlineChanges() throws Exception {
        URI page = base.resolve("/ilos/st/course/reportList_form.acl?KJKEY=DESIGN101");
        String html = fixture("planary-report-list.html");
        EclassCourse course = new EclassCourse("DESIGN101", "인터랙션 디자인");
        EclassTask original = parser.tasks(parser.document(html, page), page, course).getFirst();
        EclassTask changed = parser.tasks(parser.document(html.replace("22일", "25일"), page), page, course).getFirst();
        assertEquals(original.externalId(), changed.externalId());
        assertNotEquals(original.dueAt(), changed.dueAt());
    }

    @Test
    void distinctAssignmentRowsSharingOnlyACourseLinkDoNotOverwriteEachOther() throws Exception {
        URI page = base.resolve("/ilos/st/course/reportList_form.acl?KJKEY=DESIGN101");
        String first = fixture("planary-report-list.html");
        String second = first.replace("인터랙션 과제 1", "인터랙션 과제 2");
        List<EclassTask> tasks = parser.tasks(parser.document(first + second, page), page,
                new EclassCourse("DESIGN101", "인터랙션 디자인"));
        assertEquals(List.of("인터랙션 과제 1", "인터랙션 과제 2"), tasks.stream().map(EclassTask::title).toList());
        assertNotEquals(tasks.getFirst().externalId(), tasks.getLast().externalId());
    }

    @Test
    void syllabusImportsOnlyExplicitExamDatesNeverWeekDerivedDates() throws Exception {
        URI page = base.resolve("/ilos/st/course/lecture_plan_form.acl?KJKEY=DESIGN101");
        List<EclassTask> tasks = parser.tasks(parser.document(fixture("planary-syllabus.html"), page), page,
                new EclassCourse("DESIGN101", "인터랙션 디자인"));
        assertEquals(1, tasks.size());
        assertEquals("exam", tasks.getFirst().type());
        assertEquals(Instant.parse("2026-10-20T05:00:00Z"), tasks.getFirst().dueAt());
    }

    @Test
    void noMarkerOrLoginFormCannotProveAuthentication() throws Exception {
        URI page = base.resolve("/ilos/main/main_form.acl");
        assertFalse(parser.authenticated(parser.document("<p>강의</p>", page)));
        assertFalse(parser.authenticated(parser.document(fixture("planary-login.html") + "<a href='logout.acl'>logout</a>", page)));
        assertTrue(parser.authenticated(parser.document("<a href='/ilos/lo/logout.acl'>로그아웃</a>", page)));
    }

    @Test
    void hangulNonRegularKjKeyIsAcceptedFromDataAttributeAndEncodedQuery() {
        URI page = base.resolve("/ilos/main/main_form.acl");
        assertEquals("비2026B64101", parser.courses(parser.document(
                "<div data-kj='비2026B64101'>비정규 특강</div>", page)).getFirst().externalId());
        assertEquals("비2026B64101", parser.courses(parser.document(
                "<a href='/ilos/st/course/submain_form.acl?KJKEY=%EB%B9%842026B64101'>비정규 특강</a>", page)).getFirst().externalId());
    }

    @Test
    void kjKeyLongerThanColumnIsRejected() {
        URI page = base.resolve("/ilos/main/main_form.acl");
        assertTrue(parser.courses(parser.document("<div data-kj='" + "A".repeat(41) + "'>과목</div>", page)).isEmpty());
        assertEquals(1, parser.courses(parser.document("<div data-kj='" + "A".repeat(40) + "'>과목</div>", page)).size());
    }

    @Test
    void yearlessDatesRollOverToTheClosestYear() {
        EclassHtmlParser december = new EclassHtmlParser(Clock.fixed(Instant.parse("2026-12-28T03:00:00Z"), ZoneOffset.UTC));
        assertEquals(Instant.parse("2027-01-04T15:00:00Z"), december.dateTime("1월 5일", false));
        EclassHtmlParser january = new EclassHtmlParser(Clock.fixed(Instant.parse("2027-01-03T03:00:00Z"), ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-12-19T15:00:00Z"), january.dateTime("12월 20일", false));
    }

    @Test
    void midnightWrittenAs2400IsTheEndOfThatDay() {
        assertEquals(Instant.parse("2026-09-22T15:00:00Z"), parser.dateTime("2026-09-22 24:00", false));
    }

    @Test
    void syllabusRowWithExamKeywordSplitAcrossCellsDoesNotThrow() {
        URI page = base.resolve("/ilos/st/course/lecture_plan_form.acl?KJKEY=PLAN101");
        String html = "<form id='plan_view_form'><table><tr><td>중간</td><td>고사 2026-10-20</td></tr></table></form>";
        List<EclassTask> tasks = assertDoesNotThrow(() ->
                parser.tasks(parser.document(html, page), page, new EclassCourse("PLAN101", "계획")));
        assertEquals(1, tasks.size());
        assertEquals("exam", tasks.getFirst().type());
        assertEquals("중간 고사 2026-10-20", tasks.getFirst().title());
    }

    @Test
    void sameAssignmentOnTodoAndReportListKeepsOnlyTodoIdentity() {
        URI main = base.resolve("/ilos/main/main_form.acl");
        String todoHtml = "<div class='todo_wrap' onclick=\"goLecture('K1','report','T9')\">"
                + "<div class='todo_subjt'>운영체제</div><div class='todo_title'>[과제]  보고서 1</div></div>";
        URI list = base.resolve("/ilos/st/course/reportList_form.acl?KJKEY=K1");
        String listHtml = "<table><tr><td><a href='reportView.acl?id=T9'>보고서 1</a></td><td>2026-09-23 23:59</td></tr></table>";
        List<EclassTask> merged = new java.util.ArrayList<>(parser.tasks(parser.document(todoHtml, main), main, null));
        merged.addAll(parser.tasks(parser.document(listHtml, list), list, new EclassCourse("K1", "운영체제")));
        assertEquals(2, merged.size());
        List<EclassTask> deduped = HttpEclassGateway.dedupe(merged);
        assertEquals(1, deduped.size());
        assertEquals("K1:report:T9", deduped.getFirst().externalId());
        assertEquals(Instant.parse("2026-09-23T14:59:00Z"), deduped.getFirst().dueAt());
    }

    /** 매주 같은 제목의 과제: todo 하나와 마감이 다른 목록 행은 다른 과제다. 하나라도 빠지면 안 된다. */
    @Test
    void sameTitleWithDifferentDeadlineIsNotMerged() {
        EclassTask todo = task("K1:report:A", "[과제] 주간 보고서", Instant.parse("2026-09-10T14:59:00Z"));
        EclassTask otherWeek = task("assignment:" + "b".repeat(64), "주간 보고서", Instant.parse("2026-09-17T14:59:00Z"));
        assertEquals(List.of(todo, otherWeek), HttpEclassGateway.dedupe(List.of(todo, otherWeek)));
    }

    /** 같은 키의 후보가 여러 개면 어느 쌍이 같은 과제인지 알 수 없다. 날짜 보충도 하지 않고 모두 남긴다. */
    @Test
    void ambiguousSameTitleCandidatesAreAllKept() {
        EclassTask todoA = task("K1:report:A", "[과제] 주간 보고서", null);
        EclassTask todoB = task("K1:report:B", "[과제] 주간 보고서", null);
        EclassTask listed = task("assignment:" + "c".repeat(64), "주간 보고서", Instant.parse("2026-09-17T14:59:00Z"));
        assertEquals(List.of(todoA, todoB, listed), HttpEclassGateway.dedupe(List.of(todoA, todoB, listed)));
    }

    @Test void differentOriginalIdsWithSameTitleAndDayAreNeverMerged() {
        URI main=base.resolve("/ilos/main/main_form.acl"), list=base.resolve("/ilos/st/course/reportList_form.acl?KJKEY=K1");
        String todo="<a class='todo_wrap' onclick=\"goLecture('K1','report','A')\"><span class='todo_title'>동명 과제</span></a>";
        String row="<table><tr><td><a href='reportView.acl?seq=B'>동명 과제</a></td><td>2026-09-23</td></tr></table>";
        var all=new java.util.ArrayList<>(parser.tasks(parser.document(todo,main),main,null));
        all.addAll(parser.tasks(parser.document(row,list),list,new EclassCourse("K1","과목")));
        assertEquals("A",all.getFirst().originalItemId()); assertEquals("B",all.getLast().originalItemId());
        assertEquals(2,HttpEclassGateway.dedupe(all).size());
    }
    @Test void quizListExplicitItemIdMatchesTodoDespiteChangedTitle() {
        URI main=base.resolve("/ilos/main/main_form.acl"), list=base.resolve("/ilos/st/course/quizList_form.acl?KJKEY=K1");
        String todo="<a class='todo_wrap' onclick=\"goLecture('K1','quiz','Q1')\"><span class='todo_title'>[퀴즈] 원래 제목</span></a>";
        String row="<table><tr><td><a href='quizView.acl?item=Q1'>새 제목</a></td><td>2026-09-23</td></tr></table>";
        var all=new java.util.ArrayList<>(parser.tasks(parser.document(todo,main),main,null));
        all.addAll(parser.tasks(parser.document(row,list),list,new EclassCourse("K1","과목")));
        var merged=HttpEclassGateway.dedupe(all);
        assertEquals(1,merged.size()); assertEquals(List.of(all.getLast().externalId()), merged.getFirst().mergedExternalIds());
    }

    private static EclassTask task(String id, String title, Instant due) {
        return new EclassTask(id, "K1", "운영체제", title, "assignment", null, due, false, null, null, null, java.util.List.of());
    }
}
