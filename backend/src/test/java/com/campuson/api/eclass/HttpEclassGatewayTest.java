package com.campuson.api.eclass;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpEclassGatewayTest {
    private static final String STUDENT = "20261234";
    private static final String PASSWORD = "fixture-password&+한글";
    private static final String MAIN = "/ilos/main/main_form.acl";

    @Test
    void realClientPreservesBootstrapCsrfSessionAndFetchesObservedPaths() throws Exception {
        try (Fixture server = new Fixture()) {
            EclassSession session = server.gateway.authenticate(STUDENT, PASSWORD);
            assertTrue(session.sessionCookie().contains("sid=" + STUDENT));
            assertEquals(STUDENT, session.profile().studentId());
            assertNull(session.profile().name());
            assertNull(session.profile().department());
            assertNull(session.profile().grade());
            assertEquals("token", server.loginFields.get("_csrf"));
            assertEquals(STUDENT, server.loginFields.get("usr_id"));
            assertEquals(PASSWORD, server.loginFields.get("usr_pwd"));
            assertTrue(server.postCookie.contains("bootstrap=before"));
            assertTrue(server.mainCookie.contains("sid=" + STUDENT));
            EclassSnapshot snapshot = server.gateway.fetch(session.sessionCookie());
            assertTrue(snapshot.courses().contains(new EclassCourse("DESIGN101", "인터랙션 디자인")));
            EclassTask task = snapshot.tasks().stream().filter(t -> t.title().equals("인터랙션 과제 1")).findFirst().orElseThrow();
            assertEquals("DESIGN101", task.courseExternalId());
            assertEquals(Instant.parse("2026-09-22T14:30:00Z"), task.dueAt());
            assertNull(task.durationSeconds());
            assertTrue(server.requests.contains("GET /ilos/st/course/reportList_form.acl?KJKEY=DESIGN101"));
            assertTrue(server.requests.contains("GET /ilos/st/course/quizList_form.acl?KJKEY=DESIGN101"));
            assertTrue(server.requests.contains("GET /ilos/mp/todo_list_form.acl"));
            assertTrue(server.requests.contains("POST /ilos/mp/todo_dynamic.acl"));
            assertTrue(server.ajaxSeen);
        }
    }

    @Test
    void post200WithoutLoginFormStillRequiresVerifiedMainPage() throws Exception {
        try (Fixture server = new Fixture()) {
            server.loginMode = "direct";
            EclassSession session = server.gateway.authenticate(STUDENT, PASSWORD);
            assertEquals(STUDENT, session.profile().studentId());
            assertTrue(server.requests.contains("GET " + MAIN));
            server.mainHtml = server.loginHtml;
            assertCode(ErrorCode.INVALID_CREDENTIALS, () -> server.gateway.authenticate(STUDENT, PASSWORD));
        }
    }

    @Test
    void wrongCredentialsReturning200LoginFormAreRejected() throws Exception {
        try (Fixture server = new Fixture()) {
            server.loginMode = "wrong";
            assertCode(ErrorCode.INVALID_CREDENTIALS, () -> server.gateway.authenticate(STUDENT, PASSWORD));
        }
    }

    @Test
    void redirectBackToLoginDoesNotAuthenticate() throws Exception {
        try (Fixture server = new Fixture()) {
            server.redirect = "/ilos/index.acl";
            assertCode(ErrorCode.INVALID_CREDENTIALS, () -> server.gateway.authenticate(STUDENT, PASSWORD));
        }
    }

    @Test
    void blankSuccessBodyAndMalformedRedirectCannotAuthenticate() throws Exception {
        try (Fixture server = new Fixture()) {
            server.mainHtml = "<html><body>generic page</body></html>";
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.authenticate(STUDENT, PASSWORD));
            server.loginMode = "missing-location";
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.authenticate(STUDENT, PASSWORD));
        }
    }

    @Test
    void redirectLoopsAndEncodedDiscoveredTraversalFailClosed() throws Exception {
        try (Fixture server = new Fixture()) {
            server.redirect = "/ilos/lo/login.acl";
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.authenticate(STUDENT, PASSWORD));
            server.redirect = MAIN;
            EclassSession session = server.gateway.authenticate(STUDENT, PASSWORD);
            server.mainHtml += "<script>var todo='/ilos/%2e%2e/todo.acl';</script>";
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.fetch(session.sessionCookie()));
        }
    }

    @Test
    void malformedInitialLoginFormDoesNotPostCredentials() throws Exception {
        try (Fixture server = new Fixture()) {
            server.loginHtml = "<form action='/ilos/lo/login.acl'><input name='usr_id'></form>";
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.authenticate(STUDENT, PASSWORD));
            assertTrue(server.loginFields.isEmpty());
        }
    }

    @Test
    void crossOriginActionAndRedirectAreRefusedBeforeSendingCredentialsOrCookies() throws Exception {
        try (Fixture server = new Fixture(); Fixture other = new Fixture()) {
            server.loginHtml = server.loginHtml.replace("/ilos/lo/login.acl", other.base + "/ilos/lo/login.acl");
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.authenticate(STUDENT, PASSWORD));
            assertTrue(other.requests.isEmpty());
            server.loginHtml = EclassHtmlParserTest.fixture("planary-login.html");
            server.redirect = other.base + MAIN;
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.authenticate(STUDENT, PASSWORD));
            assertTrue(other.requests.isEmpty());
        }
    }

    @Test
    void sameHostDifferentPortIsStillCrossOriginAndDiscoveredUrlsAreChecked() throws Exception {
        try (Fixture server = new Fixture(); Fixture other = new Fixture()) {
            EclassSession session = server.gateway.authenticate(STUDENT, PASSWORD);
            server.mainHtml += "<script>var todo='" + other.base + "/ilos/mp/todo_list.acl';</script>";
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.fetch(session.sessionCookie()));
            assertTrue(other.requests.isEmpty());
        }
    }

    @Test
    void expiredSessionInFetchOrAnyProbeRequiresReauthentication() throws Exception {
        try (Fixture server = new Fixture()) {
            EclassSession session = server.gateway.authenticate(STUDENT, PASSWORD);
            server.mainHtml = server.loginHtml;
            assertCode(ErrorCode.ECLASS_REAUTH_REQUIRED, () -> server.gateway.fetch(session.sessionCookie()));
            server.mainHtml = Fixture.DEFAULT_MAIN;
            server.expireProbe = true;
            assertCode(ErrorCode.ECLASS_REAUTH_REQUIRED, () -> server.gateway.fetch(session.sessionCookie()));
            assertCode(ErrorCode.ECLASS_REAUTH_REQUIRED, () -> server.gateway.fetch(""));
        }
    }

    @Test
    void redirectToLoginDuringFetchRequiresReauthentication() throws Exception {
        try (Fixture server = new Fixture()) {
            EclassSession session = server.gateway.authenticate(STUDENT, PASSWORD);
            server.redirectFetch = true;
            assertCode(ErrorCode.ECLASS_REAUTH_REQUIRED, () -> server.gateway.fetch(session.sessionCookie()));
        }
    }

    @Test
    void upstreamFailureAndOversizedBodyAreUnavailableNotEmptySnapshots() throws Exception {
        try (Fixture server = new Fixture()) {
            EclassSession session = server.gateway.authenticate(STUDENT, PASSWORD);
            server.failProbe = true;
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.fetch(session.sessionCookie()));
            server.failProbe = false;
            server.mainHtml = "x".repeat(2 * 1024 * 1024 + 1);
            assertCode(ErrorCode.ECLASS_UNAVAILABLE, () -> server.gateway.fetch(session.sessionCookie()));
        }
    }

    @Test
    void cookieJarsRemainPerUserAndNoPreviousLoginCookieLeaks() throws Exception {
        try (Fixture server = new Fixture()) {
            EclassSession first = server.gateway.authenticate(STUDENT, PASSWORD);
            EclassSession second = server.gateway.authenticate("20265678", PASSWORD);
            assertFalse(server.postCookie.contains("sid="));
            server.gateway.fetch(first.sessionCookie());
            assertTrue(server.mainCookie.contains("sid=" + STUDENT));
            assertFalse(server.mainCookie.contains("sid=20265678"));
            server.gateway.fetch(second.sessionCookie());
            assertTrue(server.mainCookie.contains("sid=20265678"));
        }
    }

    @Test
    void rejectsNonHttpsNonLoopbackAndTraversalConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new HttpEclassGateway(URI.create("http://example.com"), EclassHtmlParserTest.CLOCK, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new HttpEclassGateway(URI.create("https://example.com/path"), EclassHtmlParserTest.CLOCK, Duration.ofSeconds(1)));
    }

    private static void assertCode(ErrorCode code, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(code, error.code());
        assertEquals(code.message(), error.getMessage());
    }

    private static final class Fixture implements AutoCloseable {
        static final String DEFAULT_MAIN = "<a href='/ilos/lo/logout.acl'>로그아웃</a>"
                + "<div class='my_lecture_container'><div class='container_body'><div>"
                + "<a href='/ilos/st/course/submain_form.acl?KJKEY=DESIGN101'>인터랙션 디자인</a></div></div></div>"
                + "<script>var todo='/ilos/mp/todo_dynamic.acl';</script>";
        final HttpServer server;
        final URI base;
        final HttpEclassGateway gateway;
        final List<String> requests = Collections.synchronizedList(new ArrayList<>());
        volatile String loginHtml = EclassHtmlParserTest.fixture("planary-login.html");
        volatile String mainHtml = DEFAULT_MAIN;
        volatile String redirect = MAIN;
        volatile String loginMode = "success";
        volatile String postCookie = "";
        volatile String mainCookie = "";
        volatile Map<String, String> loginFields = Map.of();
        volatile boolean ajaxSeen;
        volatile boolean expireProbe;
        volatile boolean failProbe;
        volatile boolean redirectFetch;

        Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
            base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            gateway = new HttpEclassGateway(base, EclassHtmlParserTest.CLOCK, Duration.ofSeconds(2));
        }

        void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                if (path.equals("/ilos/index.acl")) {
                    exchange.getResponseHeaders().add("Set-Cookie", "bootstrap=before; Path=/; HttpOnly");
                    send(exchange, 200, cookie(exchange).contains("sid=") && !redirect.equals("/ilos/index.acl") ? mainHtml : loginHtml);
                } else if (path.equals("/ilos/lo/login.acl") && exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Location", redirect);
                    send(exchange, 302, "");
                } else if (path.equals("/ilos/lo/login.acl")) {
                    postCookie = cookie(exchange);
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> fields = new LinkedHashMap<>();
                    for (String pair : body.split("&")) {
                        String[] parts = pair.split("=", 2);
                        fields.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
                    }
                    loginFields = fields;
                    if (loginMode.equals("wrong") || !postCookie.contains("bootstrap=before")
                            || !"token".equals(fields.get("_csrf")) || !PASSWORD.equals(fields.get("usr_pwd"))) send(exchange, 200, loginHtml);
                    else {
                        exchange.getResponseHeaders().add("Set-Cookie", "sid=" + fields.get("usr_id") + "; Path=/; HttpOnly");
                        if (loginMode.equals("direct")) send(exchange, 200, "<script>location.href='/ilos/main/main_form.acl';</script>");
                        else {
                            if (!loginMode.equals("missing-location")) exchange.getResponseHeaders().set("Location", redirect);
                            send(exchange, 302, "");
                        }
                    }
                } else if (path.equals(MAIN)) {
                    mainCookie = cookie(exchange);
                    if (redirectFetch) {
                        exchange.getResponseHeaders().set("Location", "/ilos/lo/login.acl");
                        send(exchange, 302, "");
                    } else send(exchange, 200, mainHtml);
                } else if (path.equals("/ilos/mp/todo_list_form.acl") && expireProbe) send(exchange, 200, loginHtml);
                else if (path.equals("/ilos/mp/todo_list_form.acl") && failProbe) send(exchange, 503, "upstream private diagnostics");
                else if (path.equals("/ilos/st/course/reportList_form.acl")) send(exchange, 200, EclassHtmlParserTest.fixture("planary-report-list.html"));
                else if (path.equals("/ilos/mp/todo_dynamic.acl")) {
                    if ("XMLHttpRequest".equals(exchange.getRequestHeaders().getFirst("X-Requested-With"))) ajaxSeen = true;
                    send(exchange, 200, EclassHtmlParserTest.fixture("planary-todo.html"));
                } else send(exchange, 404, "not installed on this template");
            }
        }

        static String cookie(HttpExchange exchange) { return String.join("; ", exchange.getRequestHeaders().getOrDefault("Cookie", List.of())); }
        static void send(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        @Override public void close() { server.stop(0); }
    }
}
