package com.campuson.api.eclass;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SeoulTech ilos structures ported from Planary's _seoultech.js and explicit-date syllabus parser. */
final class EclassHtmlParser {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern FULL_DATE = Pattern.compile("(20\\d{2})[.\\-/년\\s]+(\\d{1,2})[.\\-/월\\s]+(\\d{1,2})");
    private static final Pattern KOREAN_DATE = Pattern.compile("(?:(20\\d{2})[.\\-/년\\s]+)?(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern DDAY = Pattern.compile("D\\s*([-+])\\s*(\\d+)|D\\s*day\\s*[:：]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("(?:(오전|오후)\\s*)?(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern GO_LECTURE = Pattern.compile("goLecture\\s*\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]*)['\"]");
    private static final Pattern KJ_QUERY = Pattern.compile("[?&]kjkey=([^&#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TODO_PATH = Pattern.compile("['\"]([^'\"]*todo[^'\"]*\\.acl[^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);
    private final Clock clock;

    EclassHtmlParser(Clock clock) { this.clock = clock; }

    Document document(String html, URI uri) { return Jsoup.parse(html, uri.toString()); }

    boolean loginForm(Document doc) {
        return !doc.select("input[name=usr_id], input[name=usr_pwd], form[action*=/ilos/lo/login]").isEmpty();
    }

    Element authenticationForm(Document doc) {
        for (Element form : doc.select("form")) {
            if (form.selectFirst("input[name=usr_id]") != null && form.selectFirst("input[name=usr_pwd]") != null) return form;
        }
        return null;
    }

    boolean authenticated(Document doc) {
        if (loginForm(doc)) return false;
        if (!doc.select(".my_lecture_container, .todo_wrap").isEmpty()) return true;
        for (Element element : doc.select("a[href], form[action], [onclick]")) {
            String target = element.attr("href") + element.attr("action") + element.attr("onclick");
            if (target.toLowerCase(Locale.ROOT).contains("logout")) return true;
        }
        return false;
    }

    List<String> todoPaths(Document doc) {
        Set<String> paths = new LinkedHashSet<>();
        Matcher matcher = TODO_PATH.matcher(doc.html());
        while (matcher.find()) paths.add(matcher.group(1).replace("&amp;", "&"));
        return List.copyOf(paths);
    }

    List<String> syllabusPaths(Document doc) {
        Set<String> paths = new LinkedHashSet<>();
        for (Element link : doc.select("a[href], #top_notice_container [onclick]")) {
            String href = link.attr("href");
            if ((link.text() + " " + href).matches("(?is).*(강의\\s*계획서|수업계획|syllabus|lecture_?plan).*")) {
                if (!href.isBlank() && !href.startsWith("javascript:")) paths.add(href);
            }
            if (link.closest("#top_notice_container") != null) {
                Matcher quoted = QUOTED.matcher(link.attr("onclick"));
                if (quoted.find()) paths.add(quoted.group(1));
            }
        }
        return List.copyOf(paths);
    }

    List<EclassCourse> courses(Document doc) {
        Map<String, EclassCourse> found = new LinkedHashMap<>();
        for (Element element : doc.select("[data-kj], a[href], [onclick]")) {
            String kj = validKj(element.attr("data-kj"));
            String name = null;
            List<String> args = lectureArgs(element.attr("href") + element.attr("onclick"));
            if (kj == null && !args.isEmpty()) kj = validKj(args.getFirst());
            boolean todo = element.hasClass("todo_wrap") || element.closest(".todo_wrap") != null;
            if (todo) {
                Element wrap = element.hasClass("todo_wrap") ? element : element.closest(".todo_wrap");
                name = text(wrap.selectFirst(".todo_subjt"));
            } else {
                Element container = element.closest(".coursebox, .course-item, .subject-item, .my_lecture_container .container_body > div");
                if (container != null) name = text(container.selectFirst(".course_name, .courseName, .course-name, .name, h4, h3, .title"));
                if (name == null && args.size() > 1 && !isCategory(args.get(1))) name = clean(args.get(1));
            }
            if (kj == null) kj = queryKj(element.attr("href"));
            // Course-list anchors carry names; assignment/quiz view anchors carry task titles, not course names.
            if (name == null && !element.attr("href").matches("(?i).*(report|quiz|lecture.*[Vv]iew).*")) name = clean(element.text());
            if (name != null && (name.length() < 2 || name.length() > 200)) name = null;
            if (kj != null) {
                EclassCourse old = found.get(kj);
                if (old == null || old.name() == null && name != null) found.put(kj, new EclassCourse(kj, name));
            }
        }
        return List.copyOf(found.values());
    }

    List<EclassTask> tasks(Document doc, URI page, EclassCourse course) {
        Map<String, EclassTask> tasks = new LinkedHashMap<>();
        for (Element wrap : doc.select(".todo_wrap")) {
            String href = wrap.attr("href");
            List<String> args = lectureArgs(href + wrap.attr("onclick"));
            String kj = validKj(wrap.attr("data-kj"));
            if (kj == null && !args.isEmpty()) kj = validKj(args.getFirst());
            String kind = clean(wrap.attr("data-id"));
            if (kind == null && args.size() > 1) kind = args.get(1);
            String item = args.size() > 2 ? args.get(2) : null;
            if (item == null) {
                Element hidden = wrap.selectFirst("input[id*=todo_list_gubun]");
                if (hidden != null) item = clean(hidden.val());
            }
            String title = text(wrap.selectFirst(".todo_title"));
            String name = text(wrap.selectFirst(".todo_subjt"));
            String date = text(wrap.selectFirst(".todo_sub_wrap .todo_date, .todo_date"));
            String type = classify(title + " " + kind + " " + href);
            if (type == null || title == null) continue;
            String url = source(page, href);
            String id = item == null ? stable(kj, type, title) : String.join(":", kj == null ? "unknown" : kj, kind == null ? type : kind, item);
            Instant due = dateTime(date, true);
            put(tasks, new EclassTask(id, kj, name, title, type, null, due, due != null && !TIME.matcher(date).find(), duration(wrap), url, item, java.util.List.of()));
        }
        boolean courseList = page.getPath().matches(".*(?:reportList|quizList).*?");
        if (courseList || course != null && doc.selectFirst("#plan_view_form") != null) {
            boolean syllabus = doc.selectFirst("#plan_view_form") != null;
            for (Element row : doc.select(syllabus ? "#plan_view_form table tr" : "tr")) {
                List<Element> cells = row.select("td");
                if (cells.size() < 2) continue;
                Element link = cells.get(0).selectFirst("a");
                if (link == null) link = cells.get(1).selectFirst("a");
                String title = link == null ? clean(cells.getFirst().text()) : clean(link.text());
                if (!syllabus && (title == null || title.length() < 3 || title.equals("Title") || title.equals("제목"))) continue;
                String href = link == null ? "" : link.attr("href");
                String type = classify(title + " " + href);
                if (syllabus) {
                    if (!row.text().matches("(?is).*(중간\\s*(고사|시험)|기말\\s*(고사|시험)|midterm|final\\s*exam).*")) continue;
                    type = "exam";
                    String examCell = cells.stream().map(Element::text)
                            .filter(value -> value.matches("(?is).*(중간\\s*(고사|시험)|기말\\s*(고사|시험)|midterm|final\\s*exam).*"))
                            .findFirst().map(EclassHtmlParser::clean).orElse(null);
                    // Exam keyword can span cells; fall back to the bounded row text rather than failing the sync.
                    title = examCell != null ? examCell : truncate(clean(row.text()), 200);
                    if (title == null) continue;
                }
                if (type == null) type = page.getPath().contains("reportList") ? "assignment" : "quiz";
                Instant due = null;
                boolean dateOnly = false;
                for (Element cell : cells) {
                    Instant candidate = dateTime(cell.text(), false);
                    if (candidate != null) {
                        due = candidate; // Last date in a range is the deadline, not its opening.
                        dateOnly = !TIME.matcher(cell.text()).find();
                    }
                }
                if (syllabus && due == null) continue; // Never infer exam dates from week numbers or semester guesses.
                String kj = course == null ? queryKj(page.toString()) : course.externalId();
                String name = course == null ? null : course.name();
                String url = source(page, href);
                // A course-only URL is shared by multiple rows, not a task identifier.
                boolean courseOnlyLink = href.matches("(?i)[^?]+\\?KJKEY=[^&]+");
                String identity = href.isBlank() || href.equals("#") || href.startsWith("javascript:") || courseOnlyLink ? title : href;
                put(tasks, new EclassTask(stable(kj, type, identity), kj, name, title, type, null, due, dateOnly, duration(row), url, itemId(href), java.util.List.of()));
            }
        } else if (doc.select(".todo_wrap").isEmpty()) {
            for (Element row : doc.select("li, tr, .todo, .card, .activity, .coursebox")) {
                String content = clean(row.text());
                if (content == null || content.length() > 600 || !DDAY.matcher(content).find()) continue;
                Matcher titleMatch = Pattern.compile("\\[(영상|과제|퀴즈)]\\s*(.+?)(?=\\s+D\\s*[-+]|$)").matcher(content);
                if (!titleMatch.find()) continue;
                String title = clean(titleMatch.group());
                Element link = row.selectFirst("a[href]");
                String href = link == null ? "" : link.attr("href");
                String type = classify(title + href);
                String kj = queryKj(href);
                Instant due = dateTime(content, true);
                if (type != null) put(tasks, new EclassTask(stable(kj, type, title), kj, null, title, type, null, due, due != null && !TIME.matcher(content).find(), duration(row), source(page, href), null, java.util.List.of()));
            }
        }
        return List.copyOf(tasks.values());
    }

    Instant dateTime(String text, boolean allowDday) {
        String value = clean(text);
        if (value == null) return null;
        LocalDate date = null;
        Matcher dates = FULL_DATE.matcher(value);
        while (dates.find()) date = validDate(dates.group(1), dates.group(2), dates.group(3));
        if (date == null) {
            dates = KOREAN_DATE.matcher(value);
            while (dates.find()) date = dates.group(1) != null ? validDate(dates.group(1), dates.group(2), dates.group(3)) : nearestYear(dates.group(2), dates.group(3));
        }
        if (date == null && allowDday) {
            Matcher dday = DDAY.matcher(value);
            if (dday.find()) {
                try {
                    int days = Integer.parseInt(dday.group(2) == null ? dday.group(3) : dday.group(2));
                    if (days <= 3660) date = LocalDate.now(clock.withZone(KST)).plusDays("+".equals(dday.group(1)) ? -days : days);
                } catch (NumberFormatException ignored) { return null; }
            }
        }
        if (date == null) return null;
        LocalTime time = LocalTime.MIDNIGHT; // Date-only values retain the supplied KST calendar day, not an invented 23:59 deadline.
        boolean endOfDay = false;
        Matcher times = TIME.matcher(value);
        while (times.find()) {
            try {
                int hour = Integer.parseInt(times.group(2));
                if (times.group(1) != null) {
                    if (hour < 1 || hour > 12) return null;
                    hour = hour % 12 + (times.group(1).equals("오후") ? 12 : 0);
                }
                int minute = Integer.parseInt(times.group(3));
                int second = times.group(4) == null ? 0 : Integer.parseInt(times.group(4));
                endOfDay = hour == 24 && minute == 0 && second == 0; // "24:00" = end of that day.
                time = endOfDay ? LocalTime.MIDNIGHT : LocalTime.of(hour, minute, second);
            } catch (DateTimeException | NumberFormatException ignored) { return null; }
        }
        return (endOfDay ? date.plusDays(1) : date).atTime(time).atZone(KST).toInstant();
    }

    /** Year-less dates pick the candidate year closest to today so December/January boundaries roll over. */
    private LocalDate nearestYear(String month, String day) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalDate best = null;
        for (int offset = -1; offset <= 1; offset++) {
            LocalDate candidate = validDate(Integer.toString(today.getYear() + offset), month, day);
            if (candidate == null) continue;
            if (best == null || Math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, candidate))
                    < Math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, best))) best = candidate;
        }
        return best;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.codePointCount(0, value.length()) <= max) return value;
        return value.substring(0, value.offsetByCodePoints(0, max));
    }

    private LocalDate validDate(String year, String month, String day) {
        try { return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)); }
        catch (DateTimeException | NumberFormatException ignored) { return null; }
    }

    private Integer duration(Element element) {
        String seconds = element.attr("data-duration-seconds");
        if (!seconds.isBlank()) {
            try { int value = Integer.parseInt(seconds); return value > 0 ? value : null; }
            catch (NumberFormatException ignored) { return null; }
        }
        Matcher match = Pattern.compile("(?:재생\\s*시간|동영상\\s*길이|소요\\s*시간|제한\\s*시간)\\s*[:：]?\\s*(\\d{1,5})\\s*분").matcher(element.text());
        return match.find() ? Integer.parseInt(match.group(1)) * 60 : null;
    }

    /** Only explicit item query fields count; a KJKEY-only course link supplies no item identity. */
    private static String itemId(String href) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        Matcher match = Pattern.compile("(?i)[?&](?:item|seq|id|report_id|quiz_id)=([A-Za-z0-9_-]{1,100})(?=&|$)").matcher(href.replace("&amp;", "&"));
        while (match.find()) ids.add(match.group(1));
        return ids.size() == 1 ? ids.iterator().next() : null;
    }

    private static void put(Map<String, EclassTask> tasks, EclassTask task) { tasks.putIfAbsent(task.externalId(), task); }
    private static String text(Element element) { return element == null ? null : clean(element.text()); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.replaceAll("\\s+", " ").trim(); }
    private static boolean isCategory(String value) { return value.matches("(?i)report|assign(?:ment)?|quiz|lecture|vod|video|contents|[0-9]+|과제|퀴즈|영상"); }
    private static String classify(String value) {
        value = value.toLowerCase(Locale.ROOT);
        if (value.matches("(?s).*(report|assign|과제|homework).*")) return "assignment";
        if (value.matches("(?s).*(quiz|퀴즈).*")) return "quiz";
        if (value.matches("(?s).*(lecture|weeks|vod|video|영상|contents|동영상|온라인).*")) return "lecture";
        return null;
    }
    private static List<String> lectureArgs(String value) {
        List<String> result = new ArrayList<>();
        Matcher call = GO_LECTURE.matcher(value);
        if (call.find()) { Matcher args = QUOTED.matcher(call.group(1)); while (args.find()) result.add(args.group(1)); }
        return result;
    }
    private static String queryKj(String href) {
        Matcher match = KJ_QUERY.matcher(href);
        if (!match.find()) return null;
        try { return validKj(URLDecoder.decode(match.group(1), StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private static String validKj(String value) { return value != null && value.matches("[\\p{IsHangul}A-Za-z0-9._-]{1,40}") ? value : null; }
    private static String source(URI page, String href) {
        if (href.isBlank() || href.startsWith("javascript:")) return page.toString();
        try {
            URI uri = page.resolve(href);
            if (!uri.getScheme().equalsIgnoreCase(page.getScheme()) || !java.util.Objects.equals(uri.getAuthority(), page.getAuthority()) || uri.getUserInfo() != null) return page.toString();
            return uri.toString();
        } catch (IllegalArgumentException ignored) { return page.toString(); }
    }
    private static String stable(String kj, String type, String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((kj + ":" + type + ":" + identity).getBytes(StandardCharsets.UTF_8));
            return type + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
