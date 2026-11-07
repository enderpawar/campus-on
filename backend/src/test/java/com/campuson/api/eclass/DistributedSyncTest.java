package com.campuson.api.eclass;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyLong;
import com.campuson.api.auth.*;
import com.campuson.api.global.error.*;
import com.campuson.api.support.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(EclassTestConfiguration.class)
class DistributedSyncTest {
    @Autowired AuthService auth;
    @Autowired EclassAccountService accounts;
    @Autowired EclassConnectionRepository connections;
    @Autowired EclassTestConfiguration.FakeEclass school;
    @Autowired Clock clock;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean ConnectionCoordination coordination;
    private record Account(long id, String student) {}
    private Account account() {
        String student="distributed-"+UUID.randomUUID();
        EclassSession session=school.authenticate(student,ApiTestClient.PASSWORD);
        return new Account(accounts.authenticated(session,ApiTestClient.PASSWORD,true).memberId(),student);
    }
    private EclassSyncService service(ConnectionCoordination coord) {
        return new EclassSyncService(school,accounts,connections,clock,coord,false,Duration.ofMinutes(5),Duration.ofMinutes(30),Duration.ZERO);
    }
    @Test void twoIndependentServicesShareSingleFlightAndBackgroundSkips()throws Exception {
        Account a=account(); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        // A disabled cooldown stays disabled even if another server's timestamp is slightly ahead.
        db.update("UPDATE eclass_connections SET updated_at=? WHERE user_id=?",clock.instant().plusSeconds(1).atOffset(ZoneOffset.UTC),a.id);
        school.holdFetch(a.student,entered,release);
        var independent=new ConnectionCoordination(db,manager,clock,Duration.ofMinutes(5),Duration.ofSeconds(10));
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> first=pool.submit(()->service(coordination).sync(a.id));
            try {
                if (!entered.await(10,TimeUnit.SECONDS)) { first.get(1,TimeUnit.SECONDS); fail("first sync never entered school"); }
                BusinessException ex=assertThrows(BusinessException.class,()->service(independent).sync(a.id));
                assertEquals(ErrorCode.ECLASS_SYNC_IN_PROGRESS,ex.code());
                service(independent).syncInBackground(a.id);
            } finally {release.countDown();}
            first.get(5,TimeUnit.SECONDS);
        }
        assertEquals("READY",service(independent).sync(a.id).state());
    }
    @Test void expiredOwnersCannotReleaseNewSyncLeaseOrClaimAndExpiredClaimAllowsMutation() {
        Account a=account();
        class Time extends Clock {
            Instant now=clock.instant();
            public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;}
        }
        Time time=new Time();
        var first=new ConnectionCoordination(db,manager,time,Duration.ofSeconds(20),Duration.ofSeconds(10));
        var second=new ConnectionCoordination(db,manager,time,Duration.ofSeconds(20),Duration.ofSeconds(10));
        String old=first.acquireSync(a.id), generation=accounts.context(a.id).generation();
        assertNotNull(first.claim(a.id,generation,old)); assertNull(second.acquireSync(a.id));
        time.now=time.now.plusSeconds(301);
        String replacement=second.acquireSync(a.id); assertNotNull(replacement);
        first.releaseSync(a.id,old); assertNull(first.acquireSync(a.id));
        assertNull(first.claim(a.id,"wrong-generation",replacement));
        second.mutate(()->{second.assertMutable(a.id);db.update("DELETE FROM eclass_connections WHERE user_id=?",a.id);return null;});
        assertFalse(connections.existsById(a.id));
    }
    @Test void reloginWaitsForClaimAndOldSyncNeverOverwritesNewConnection()throws Exception {
        Account a=account();school.expireSessions(a.student);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),mutationAttempted=new CountDownLatch(1);
        school.hold(a.student,entered,release);
        doAnswer(invocation->{try {return invocation.callRealMethod();} catch (RuntimeException ex) {mutationAttempted.countDown();throw ex;}}).when(coordination).assertMutable(a.id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> running=pool.submit(()->service(coordination).sync(a.id));
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                Future<?> replacement=pool.submit(()->accounts.authenticated(new EclassSession("SESSION=new-generation#999999",new EclassProfile(a.student,null,null,null)),"new-password",true));
                assertTrue(mutationAttempted.await(5,TimeUnit.SECONDS));assertFalse(replacement.isDone());
                release.countDown();replacement.get(5,TimeUnit.SECONDS);running.get(5,TimeUnit.SECONDS);
            } finally {release.countDown();}
        }
        assertEquals("new-password",accounts.context(a.id).password());
        assertEquals("SESSION=new-generation#999999",accounts.context(a.id).sessionCookie());
    }

    @Test void logoutWaitsWithoutHoldingConnectionUntilSavedPasswordRequestCompletes()throws Exception {
        Account a=account();school.expireSessions(a.student);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),mutationAttempted=new CountDownLatch(1);
        school.hold(a.student,entered,release);
        doAnswer(invocation->{try {return invocation.callRealMethod();} catch (RuntimeException ex) {mutationAttempted.countDown();throw ex;}}).when(coordination).assertMutable(a.id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> sync=pool.submit(()->service(coordination).sync(a.id));
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                Future<?> logout=pool.submit(()->auth.logout(a.id));
                assertTrue(mutationAttempted.await(5,TimeUnit.SECONDS));
                assertFalse(logout.isDone());
                assertTrue(connections.existsById(a.id));
                // A separate short transaction can still use the database while the mutation waits.
                assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM eclass_connections WHERE user_id=?",Integer.class,a.id));
                release.countDown();logout.get(5,TimeUnit.SECONDS);
                try {sync.get(5,TimeUnit.SECONDS);} catch(ExecutionException ex) {assertInstanceOf(BusinessException.class,ex.getCause());}
            } finally {release.countDown();}
        }
        assertFalse(connections.existsById(a.id));
        school.changePassword(a.student,"new-password");
        auth.login(new LoginRequest(a.student,"new-password",true),null);
        assertNotNull(accounts.context(a.id).password());
        assertEquals("new-password",accounts.context(a.id).password());
    }
}
