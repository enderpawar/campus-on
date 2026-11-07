package com.campuson.api;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

abstract class SchemaUpgradeScenarios {
    abstract String url();
    abstract String username();
    abstract String password();
    @Test void existingLocalIdentityIsQuarantinedWithoutLosingEventFeedOrNoticeIds() throws Exception {
        String url = url();
        Flyway.configure().dataSource(url, username(), password()).target("2").load().migrate();
        try (var connection = DriverManager.getConnection(url, username(), password()); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO member(id, school_id, email, password_hash, name, department, grade, token_version, created_at)
                    VALUES (42, (SELECT id FROM school WHERE name='서울과학기술대학교'), 'old@example.invalid', 'old-hash', '기존 사용자', '기계공학과', 3, 5, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO event(id, member_id, type, source, title, due_at, created_at)
                    VALUES (101, 42, 'PERSONAL', 'PERSONAL', '기존 일정', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("INSERT INTO calendar_feed(id, member_id, token, created_at) VALUES(201, 42, 'old-calendar-secret', CURRENT_TIMESTAMP)");
            statement.executeUpdate("""
                    INSERT INTO notice(id, school_id, source, category, title, url, summary, posted_at)
                    VALUES(301, (SELECT id FROM school WHERE name='서울과학기술대학교'), '학사지원과', 'ACADEMIC', '기존 공지', 'https://example.invalid/old', '기존 요약', CURRENT_TIMESTAMP)
                    """);
        }
        Flyway.configure().dataSource(url, username(), password()).load().migrate();
        try (var connection = DriverManager.getConnection(url, username(), password()); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("SELECT external_ref, account_status, token_version, user_name, department_name, grade FROM users WHERE user_id=42")) {
                assertTrue(row.next());
                assertEquals("legacy:42", row.getString("external_ref"));
                assertEquals("SUSPENDED", row.getString("account_status"));
                assertEquals(6, row.getInt("token_version"));
                assertEquals("기존 사용자", row.getString("user_name"));
                assertEquals("기계공학과", row.getString("department_name"));
                assertEquals(3, row.getInt("grade"));
            }
            try (var row = statement.executeQuery("SELECT e.id, f.id FROM event e JOIN calendar_feed f ON e.member_id=f.member_id WHERE e.member_id=42")) {
                assertTrue(row.next()); assertEquals(101, row.getLong(1)); assertEquals(201, row.getLong(2));
            }
            try (var row = statement.executeQuery("SELECT p.post_id, p.url, p.excerpt, b.board_name, p.author_id FROM posts p JOIN boards b ON p.board_id=b.board_id WHERE p.post_id=301")) {
                assertTrue(row.next()); assertEquals(301, row.getLong(1));
                assertEquals("https://example.invalid/old", row.getString(2));
                assertEquals("기존 요약", row.getString(3)); assertEquals("학사지원과", row.getString(4));
                assertNull(row.getObject(5), "수집 공지의 작성자를 임의의 사용자로 지정하지 않는다");
            }

        }
    }

    /** V4까지 쓰던 DB의 기존 e-class 연결도 V5 뒤에 세대 값을 받는다. 값이 없으면 기존 행이 세대 확인(F3)에서 빠진다. */
    @Test void existingEclassConnectionsReceiveALegacyGenerationAndNewRowsMustCarryOne() throws Exception {
        String url = url();
        Flyway.configure().dataSource(url, username(), password()).target("2").load().migrate();
        try (var connection = DriverManager.getConnection(url, username(), password()); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO member(id, school_id, email, password_hash, name, token_version, created_at)
                    VALUES (42, (SELECT id FROM school WHERE name='서울과학기술대학교'), 'old@example.invalid', 'old-hash', '기존 사용자', 0, CURRENT_TIMESTAMP)
                    """);
        }
        Flyway.configure().dataSource(url, username(), password()).target("4").load().migrate();
        try (var connection = DriverManager.getConnection(url, username(), password()); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO eclass_connections(user_id, encrypted_session_cookie, encrypted_password, sync_state, updated_at)
                    VALUES (42, 'v1.cookie', 'v1.password', 'READY', CURRENT_TIMESTAMP)
                    """);
        }
        Flyway.configure().dataSource(url, username(), password()).load().migrate();
        try (var connection = DriverManager.getConnection(url, username(), password()); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("SELECT connection_generation, encrypted_password, sync_state, sync_lease_until, reauth_claim_until FROM eclass_connections WHERE user_id=42")) {
                assertTrue(row.next());
                assertEquals("legacy-42", row.getString(1));
                assertEquals("v1.password", row.getString(2));
                assertEquals("READY", row.getString(3));
                assertNull(row.getObject(4)); assertNull(row.getObject(5));
            }
            statement.executeUpdate("INSERT INTO auth_throttle_failure(bucket_key,failures,ends_at) VALUES('s:42',1,CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO auth_throttle_reservation(attempt_id,bucket_key,expires_at) VALUES('upgrade-attempt','s:42',CURRENT_TIMESTAMP)");
            statement.executeUpdate("UPDATE eclass_connections SET sync_lease_owner='upgrade-owner', sync_lease_until=CURRENT_TIMESTAMP, reauth_claim_owner='upgrade-claim', reauth_claim_until=CURRENT_TIMESTAMP WHERE user_id=42");
            try (var row=statement.executeQuery("SELECT sync_lease_owner,reauth_claim_owner FROM eclass_connections WHERE user_id=42")) {
                assertTrue(row.next());assertEquals("upgrade-owner",row.getString(1));assertEquals("upgrade-claim",row.getString(2));
            }
            statement.executeUpdate("DELETE FROM eclass_connections WHERE user_id=42");
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO eclass_connections(user_id, encrypted_session_cookie, sync_state, updated_at)
                    VALUES (42, 'v1.cookie', 'PENDING', CURRENT_TIMESTAMP)
                    """), "connection_generation is NOT NULL after V5");

        }
    }
}
