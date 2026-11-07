package com.campuson.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 PostgreSQL에서 Flyway 마이그레이션과 엔티티 매핑(ddl-auto=validate)이 맞는지 확인한다.
 * H2 {@code MODE=PostgreSQL} 통과 ≠ PostgreSQL 통과 (Day 32 MySQL 1064 교훈).
 * Docker가 없으면 건너뛴다(로컬). CI(GitHub Actions)에서 실행된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PostgresSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    EntityManager em;

    @Test
    void flywayMigrationsMatchEntityMappingsOnRealPostgres() {
        Number count = (Number) em.createNativeQuery("select count(*) from school where name = '서울과학기술대학교'")
                .getSingleResult();
        assertEquals(1L, count.longValue());
    }

    @Test
    void blankMemberNameIsRejectedByCheckConstraint() {
        assertThrows(PersistenceException.class, () -> {
            em.createNativeQuery("""
                    insert into users (school_id, external_ref, user_name, token_version, created_at)
                    values ((select id from school where name = '서울과학기술대학교'), 'blank-student', '', 0, current_timestamp)
                    """).executeUpdate();
            em.flush();
        });
    }
}
