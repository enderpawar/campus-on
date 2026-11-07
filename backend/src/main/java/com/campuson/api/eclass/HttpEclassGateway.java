package com.campuson.api.eclass;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.global.time.CampusTime;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Real SeoulTech form/session gateway. Every invocation owns its cookie jar; the client never shares credentials. */
@Component
public final class HttpEclassGateway implements EclassGateway {
    private static final String MAIN = "/ilos/main/main_form.acl";
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_COOKIE_BYTES = 16384;
    private static final int MAX_REQUESTS = 180;
    private static final int MAX_COURSES = 30;
    private static final int MAX_TASKS = 2000;
    private static final List<String> CANDIDATE_PATHS = List.of(
            "/ilos/index.acl", "/ilos/mp/todo_list_form.acl", "/ilos/mp/todo_list.acl",
            "/ilos/mp/todo_list_view.acl", "/ilos/main/todo_list_form.acl", "/ilos/main/todo_list.acl",
            "/ilos/st/course/submain_form.acl", "/ilos/st/course/eclass_list_form.acl", "/my/", "/course/");
    private final URI base;
    private final Duration timeout;
    private final HttpClient client;
    private final EclassHtmlParser parser;

    @Autowired
    public HttpEclassGateway(@Value("${campus.eclass.base-url:https://eclass.seoultech.ac.kr}") String baseUrl,
                             @Value("${campus.eclass.request-timeout:PT10S}") Duration timeout,
                             Clock clock) {
        this(URI.create(baseUrl), clock, timeout);
    }

    public HttpEclassGateway(URI base, Clock clock, Duration timeout) {
        boolean loopback = "127.0.0.1".equals(base.getHost()) || "localhost".equals(base.getHost()) || "[::1]".equals(base.getHost());
        if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme()) && loopback)
                || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
            throw new IllegalArgumentException("E-class origin must be HTTPS (HTTP is only permitted for loopback fixtures)");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("E-class timeout must be between zero and one minute");
        }
        this.base = base.resolve("/");
        this.timeout = timeout;
        this.parser = new EclassHtmlParser(clock);
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public EclassSession authenticate(String studentId, String password) {
        if (studentId == null || studentId.isBlank() || studentId.length() > 64 || password == null || password.isEmpty() || password.length() > 1024) {
            throw failure(ErrorCode.INVALID_CREDENTIALS);
        }
        Session session = new Session(null, true);
        Page login = session.request(resolve(base, "/ilos/index.acl"), "GET", null, false, base);
        Element form = parser.authenticationForm(login.document());
        if (form == null) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
        String action = form.attr("action");
        if (action.isBlank()) action = "/ilos/lo/login.acl";
        URI target = resolve(login.url(), action);
        if (!target.getPath().startsWith("/ilos/lo/login")) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
        Map<String, String> fields = new LinkedHashMap<>();
        for (Element input : form.select("input[name]")) {
            if (!input.hasAttr("disabled") && !input.attr("type").equalsIgnoreCase("submit")) fields.put(input.attr("name"), input.val());
        }
        fields.put("usr_id", studentId);
        fields.put("usr_pwd", password);
        String encoded = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
        Page check = session.request(target, "POST", encoded, false, login.url());
        if (parser.loginForm(check.document())) throw failure(ErrorCode.INVALID_CREDENTIALS);
        // Some templates return a script/empty 200 after POST. Verify the canonical main page,
        // never infer success from the missing login form or the redirect status alone.
        if (!parser.authenticated(check.document()) || loginPath(check.url())) check = session.request(resolve(base, MAIN), "GET", null, false, login.url());
        if (parser.loginForm(check.document()) || loginPath(check.url())) throw failure(ErrorCode.INVALID_CREDENTIALS);
        if (!parser.authenticated(check.document())) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
        String cookie = session.cookieHeader();
        if (cookie.isBlank()) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
        // Planary's observed pages do not establish a reliable profile selector. Never manufacture profile data.
        return new EclassSession(cookie, new EclassProfile(studentId, null, null, null));
    }

    @Override
    public EclassSnapshot fetch(String sessionCookie) {
        if (sessionCookie == null || sessionCookie.isBlank()) throw failure(ErrorCode.ECLASS_REAUTH_REQUIRED);
        Session session = new Session(sessionCookie, false);
        List<Page> pages = new ArrayList<>();
        Page main = session.request(resolve(base, MAIN), "GET", null, false, base);
        session.checkAuthenticated(main, true);
        pages.add(main);
        for (String path : CANDIDATE_PATHS) addIfAvailable(pages, session, resolve(base, path), "GET", false, main.url());
        Set<URI> discoveredTodo = new LinkedHashSet<>();
        for (Page page : List.copyOf(pages)) {
            for (String path : parser.todoPaths(page.document())) discoveredTodo.add(resolve(page.url(), path));
        }
        if (discoveredTodo.size() > 20) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
        for (URI path : discoveredTodo) {
            addIfAvailable(pages, session, path, "GET", false, main.url());
            addIfAvailable(pages, session, path, "GET", true, main.url());
            addIfAvailable(pages, session, path, "POST", true, main.url());
        }
        Map<String, EclassCourse> courses = new LinkedHashMap<>();
        Map<String, EclassTask> tasks = new LinkedHashMap<>();
        for (Page page : pages) {
            for (EclassCourse course : parser.courses(page.document())) {
                EclassCourse existing = courses.get(course.externalId());
                if (existing == null || existing.name() == null && course.name() != null) courses.put(course.externalId(), course);
            }
            collect(tasks, parser.tasks(page.document(), page.url(), null));
        }
        if (courses.size() > MAX_COURSES) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
        for (EclassCourse course : courses.values()) {
            for (String name : List.of("reportList_form", "quizList_form", "submain_form", "lecture_plan_form")) {
                URI url = resolve(base, "/ilos/st/course/" + name + ".acl?KJKEY=" + encode(course.externalId()));
                Page page = session.optional(url, "GET", false, main.url());
                if (page == null) continue;
                collect(tasks, parser.tasks(page.document(), page.url(), course));
                Set<URI> syllabus = new LinkedHashSet<>();
                for (String path : parser.syllabusPaths(page.document())) syllabus.add(resolve(page.url(), path));
                if (syllabus.size() > 6) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
                for (URI path : syllabus) {
                    if (path.equals(page.url())) continue;
                    Page plan = session.optional(path, "GET", false, page.url());
                    if (plan != null) collect(tasks, parser.tasks(plan.document(), plan.url(), course));
                }
            }
        }
        // Resolve names through real KJ identifiers, not a fabricated catch-all course.
        List<EclassTask> resolved = dedupe(List.copyOf(tasks.values())).stream().map(task -> {
            EclassCourse course = courses.get(task.courseExternalId());
            return task.courseName() != null || course == null ? task : new EclassTask(task.externalId(), task.courseExternalId(), course.name(), task.title(), task.type(), task.startsAt(), task.dueAt(), task.dateOnly(), task.durationSeconds(), task.sourceUrl(), task.originalItemId(), task.mergedExternalIds());
        }).toList();
        return new EclassSnapshot(new ArrayList<>(courses.values()), resolved);
    }

    private void collect(Map<String, EclassTask> tasks, List<EclassTask> extra) {
        for (EclassTask task : extra) tasks.putIfAbsent(task.externalId(), task);
        if (tasks.size() > MAX_TASKS) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
    }

    private static final java.util.regex.Pattern HASHED_ID = java.util.regex.Pattern.compile("[a-z]+:[0-9a-f]{64}");

    /** Prefer explicit item identities; title/day matching is permitted only when at least one id is absent. */
    static List<EclassTask> dedupe(List<EclassTask> tasks) {
        List<EclassTask> todos = tasks.stream().filter(t -> !HASHED_ID.matcher(t.externalId()).matches()).toList();
        List<EclassTask> lists = tasks.stream().filter(t -> HASHED_ID.matcher(t.externalId()).matches()).toList();
        Map<EclassTask, EclassTask> replacements = new java.util.IdentityHashMap<>();
        Set<EclassTask> dropped = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (EclassTask todo : todos) {
            List<EclassTask> matches = lists.stream().filter(row -> matches(todo, row)).toList();
            if (matches.size() != 1) continue;
            EclassTask row = matches.getFirst();
            if (todos.stream().filter(t -> matches(t, row)).count() != 1) continue;
            dropped.add(row);
            List<String> aliases = new ArrayList<>(todo.mergedExternalIds());
            aliases.add(row.externalId()); aliases.addAll(row.mergedExternalIds());
            boolean donate = todo.dueAt() == null && row.dueAt() != null;
            replacements.put(todo, new EclassTask(todo.externalId(), todo.courseExternalId(), todo.courseName(), todo.title(), todo.type(),
                    todo.startsAt(), donate ? row.dueAt() : todo.dueAt(), donate ? row.dateOnly() : todo.dateOnly(),
                    todo.durationSeconds(), todo.sourceUrl(), todo.originalItemId(), List.copyOf(aliases)));
        }
        return tasks.stream().filter(t -> !dropped.contains(t)).map(t -> replacements.getOrDefault(t, t)).toList();
    }
    private static boolean matches(EclassTask todo, EclassTask row) {
        if (!java.util.Objects.equals(todo.courseExternalId(), row.courseExternalId()) || !todo.type().equals(row.type())) return false;
        if (todo.originalItemId() != null && row.originalItemId() != null) return todo.originalItemId().equals(row.originalItemId());
        return dedupeKey(todo).equals(dedupeKey(row)) && sameDeadlineDay(todo, row);
    }

    private static boolean sameDeadlineDay(EclassTask a, EclassTask b) {
        if (a.dueAt() == null || b.dueAt() == null) return true;
        return a.dueAt().atZone(CampusTime.ZONE).toLocalDate().equals(b.dueAt().atZone(CampusTime.ZONE).toLocalDate());
    }

    private static String dedupeKey(EclassTask task) {
        String title = task.title() == null ? "" : task.title().replaceFirst("^\\s*\\[(영상|과제|퀴즈)]", "");
        return task.courseExternalId() + "|" + task.type() + "|" + title.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private void addIfAvailable(List<Page> pages, Session session, URI url, String method, boolean ajax, URI referer) {
        Page page = session.optional(url, method, ajax, referer);
        if (page != null) pages.add(page);
    }

    private URI resolve(URI from, String path) {
        try {
            if (path.length() > 2048 || path.contains("\\") || path.chars().anyMatch(c -> c < 32)) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
            URI uri = from.resolve(path.replace("&amp;", "&"));
            if (!base.getScheme().equalsIgnoreCase(uri.getScheme()) || !base.getHost().equalsIgnoreCase(uri.getHost())
                    || port(base) != port(uri) || uri.getUserInfo() != null || uri.getFragment() != null
                    || !uri.normalize().equals(uri) || uri.getRawPath().contains("%")
                    || !(uri.getPath().startsWith("/ilos/") || uri.getPath().startsWith("/my/") || uri.getPath().startsWith("/course/"))) {
                throw failure(ErrorCode.ECLASS_UNAVAILABLE);
            }
            return uri;
        } catch (IllegalArgumentException ex) { throw failure(ErrorCode.ECLASS_UNAVAILABLE); }
    }

    private static int port(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static boolean loginPath(URI uri) { return uri.getPath().matches("(?i).*/(?:lo/)?login[^/]*"); }
    private static BusinessException failure(ErrorCode code) { return new BusinessException(code); }
    private record Page(URI url, Document document) {}

    private final class Session {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        private final boolean authenticating;
        private int requests;
        private final long started = System.nanoTime();

        Session(String initialCookie, boolean authenticating) {
            this.authenticating = authenticating;
            if (initialCookie != null) {
                if (initialCookie.length() > MAX_COOKIE_BYTES || initialCookie.contains("\r") || initialCookie.contains("\n")) throw failure(ErrorCode.ECLASS_REAUTH_REQUIRED);
                for (String pair : initialCookie.split(";")) {
                    int equals = pair.indexOf('=');
                    if (equals < 1) throw failure(ErrorCode.ECLASS_REAUTH_REQUIRED);
                    try {
                        HttpCookie cookie = new HttpCookie(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
                        cookie.setVersion(0);
                        cookie.setPath("/");
                        cookies.getCookieStore().add(base, cookie);
                    } catch (IllegalArgumentException ex) { throw failure(ErrorCode.ECLASS_REAUTH_REQUIRED); }
                }
            }
        }

        String cookieHeader() {
            String header = cookies.getCookieStore().getCookies().stream().filter(c -> !c.hasExpired())
                    .map(c -> c.getName() + "=" + c.getValue()).distinct().collect(java.util.stream.Collectors.joining("; "));
            if (header.length() > MAX_COOKIE_BYTES) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
            return header;
        }

        Page optional(URI url, String method, boolean ajax, URI referer) {
            return request(url, method, "POST".equals(method) ? "" : null, ajax, referer, true);
        }

        Page request(URI url, String method, String body, boolean ajax, URI referer) {
            return request(url, method, body, ajax, referer, false);
        }

        Page request(URI url, String method, String body, boolean ajax, URI referer, boolean optional) {
            try {
                for (int redirects = 0; redirects <= 5; redirects++) {
                    if (++requests > MAX_REQUESTS) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
                    if (!authenticating && loginPath(url)) throw failure(ErrorCode.ECLASS_REAUTH_REQUIRED);
                    Duration remaining = (authenticating ? timeout : Duration.ofMinutes(2)).minusNanos(System.nanoTime() - started);
                    if (!remaining.isPositive()) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
                    HttpRequest.Builder builder = HttpRequest.newBuilder(url).timeout(remaining.compareTo(timeout) < 0 ? remaining : timeout)
                            .header("User-Agent", "CampusOnEclassSync/1.0")
                            .header("Accept", "text/html,application/xhtml+xml")
                            .header("Referer", referer.toString());
                    for (Map.Entry<String, List<String>> entry : cookies.get(url, Map.of()).entrySet()) {
                        for (String value : entry.getValue()) builder.header(entry.getKey(), value);
                    }
                    if (ajax) builder.header("X-Requested-With", "XMLHttpRequest");
                    if ("POST".equals(method)) builder.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                    else builder.GET();
                    HttpResponse<byte[]> response = client.send(builder.build(), ignored -> new LimitedBodySubscriber());
                    cookies.put(url, response.headers().map());
                    cookieHeader();
                    int status = response.statusCode();
                    if (status == 401 || status == 403) throw failure(authenticating ? ErrorCode.INVALID_CREDENTIALS : ErrorCode.ECLASS_REAUTH_REQUIRED);
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        if (redirects == 5) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
                        String location = response.headers().firstValue("Location").orElseThrow(() -> failure(ErrorCode.ECLASS_UNAVAILABLE));
                        if (location.isBlank()) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
                        URI next = resolve(url, location);
                        if (status == 303 || (status == 301 || status == 302) && method.equals("POST")) { method = "GET"; body = null; }
                        referer = url;
                        url = next;
                        continue;
                    }
                    if (optional && (status == 404 || status == 405)) return null;
                    if (status < 200 || status >= 300) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
                    String html = new String(response.body(), charset(response));
                    Page page = new Page(url, parser.document(html, url));
                    if (!authenticating) checkAuthenticated(page, false);
                    return page;
                }
                throw failure(ErrorCode.ECLASS_UNAVAILABLE);
            } catch (IOException | IllegalArgumentException ex) {
                throw failure(ErrorCode.ECLASS_UNAVAILABLE);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw failure(ErrorCode.ECLASS_UNAVAILABLE);
            }
        }

        void checkAuthenticated(Page page, boolean requireMarker) {
            if (parser.loginForm(page.document()) || loginPath(page.url())) throw failure(ErrorCode.ECLASS_REAUTH_REQUIRED);
            if (requireMarker && !parser.authenticated(page.document())) throw failure(ErrorCode.ECLASS_UNAVAILABLE);
        }
    }

    private static java.nio.charset.Charset charset(HttpResponse<?> response) {
        String type = response.headers().firstValue("Content-Type").orElse("");
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("charset\\s*=\\s*[\"']?([^;\\s\"']+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(type);
        if (match.find()) {
            try { return java.nio.charset.Charset.forName(match.group(1)); }
            catch (IllegalArgumentException ignored) { throw failure(ErrorCode.ECLASS_UNAVAILABLE); }
        }
        return StandardCharsets.UTF_8;
    }

    /** Bounded subscriber preserves HttpRequest timeout coverage while refusing oversized bodies before allocation. */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long size;
        private boolean failed;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; delegate.onSubscribe(subscription); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (ByteBuffer buffer : buffers) size += buffer.remaining();
            if (size > MAX_BODY_BYTES) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IOException("E-class response exceeds permitted size"));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        @Override public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
