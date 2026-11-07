package com.campuson.api.eclass;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import com.campuson.api.member.Member;
import com.campuson.api.member.MemberRepository;
import com.campuson.api.schedule.EclassImportService;
import com.campuson.api.school.School;
import com.campuson.api.school.SchoolRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database-only boundaries. No upstream HTTP call runs while holding this transaction. */
@Service
public class EclassAccountService {
    private static final String SCHOOL_NAME = "서울과학기술대학교";
    private final MemberRepository members;
    private final SchoolRepository schools;
    private final EclassConnectionRepository connections;
    private final EclassSecrets secrets;
    private final EclassImportService importer;
    private final Clock clock;
    private final ConnectionCoordination coordination;
    public EclassAccountService(MemberRepository members, SchoolRepository schools,
            EclassConnectionRepository connections, EclassSecrets secrets, EclassImportService importer, Clock clock, ConnectionCoordination coordination) {
        this.members = members; this.schools = schools; this.connections = connections;
        this.secrets = secrets; this.importer = importer; this.clock = clock; this.coordination = coordination;
    }
    public record AuthenticatedAccount(long memberId, int tokenVersion) {}
    public AuthenticatedAccount authenticated(EclassSession session, String password, boolean rememberCredentials) {
        return coordination.mutate(() -> {
        EclassProfile profile = session.profile();
        String studentId = com.campuson.api.auth.StudentIds.normalize(profile.studentId());
        // Lock order everywhere: school → user → connection. Logout/withdrawal take user → connection.
        School school = schools.lockByName(SCHOOL_NAME).orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN_SCHOOL));
        Member member = members.lockByExternalRefAndSchoolId(studentId, school.getId()).orElse(null);
        if (member == null) {
            member = members.save(new Member(school, studentId, optional(profile.name()),
                    optional(profile.department()), profile.grade(), Instant.now(clock)));
        }
        if (!member.isActive()) throw new BusinessException(ErrorCode.FORBIDDEN);
        member.refreshProfile(optional(profile.name()), optional(profile.department()), profile.grade(), Instant.now(clock));
        coordination.assertMutable(member.getId());
        EclassConnection connection = connections.lockById(member.getId()).orElse(null);
        if (connection == null) connection = new EclassConnection(member.getId());
        connection.authenticate(secrets.encrypt(session.sessionCookie()),
                rememberCredentials ? secrets.encrypt(password) : null, Instant.now(clock));
        connections.save(connection);
        return new AuthenticatedAccount(member.getId(), member.getTokenVersion());
        });
    }
    @Transactional(readOnly = true)
    public String studentId(long memberId) {
        return members.findById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED)).getExternalRef();
    }
    @Transactional(readOnly = true)
    public EclassSyncContext context(long memberId) {
        Member member = members.findById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        EclassConnection connection = connections.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ECLASS_REAUTH_REQUIRED));
        try {
            return new EclassSyncContext(member.getExternalRef(), secrets.decrypt(connection.getEncryptedSessionCookie()),
                    secrets.decrypt(connection.getEncryptedPassword()), connection.getGeneration());
        } catch (UnreadableSecretException ex) {
            throw new UnreadableSecretException(connection.getGeneration());
        }
    }
    /**
     * 성공 결과 반영. 그 사이 연결이 삭제됐거나(로그아웃) 새로 로그인해 세대가 바뀌었으면 아무것도 쓰지 않고
     * false다: 이전 세대의 쿠키·일정으로 새 연결을 덮지 않는다.
     */
    @Transactional
    public boolean synchronizedSnapshot(long memberId, String generation, EclassSnapshot snapshot, String cookie) {
        schools.lockByName(SCHOOL_NAME).orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN_SCHOOL));
        Member member = members.lockById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        EclassConnection connection = connections.lockById(memberId).orElse(null);
        if (connection == null || !connection.isGeneration(generation)) return false;
        importer.apply(member, snapshot);
        connection.synced(secrets.encrypt(cookie), Instant.now(clock));
        return true;
    }
    /**
     * 실패 반영도 같은 세대일 때만: 이전 세대의 거절된 비밀번호 때문에 새 연결의 비밀번호를 지우지 않는다.
     * 반영했으면 true, 연결이 없거나 세대가 바뀌어 버렸으면 false.
     */
    @Transactional
    public boolean failed(long memberId, String generation, ErrorCode code) {
        return connections.lockById(memberId).filter(connection -> connection.isGeneration(generation))
                .map(connection -> { connection.failed(code.name(), Instant.now(clock)); return true; })
                .orElse(false);
    }
    @Transactional
    public boolean secretsUnreadable(long memberId, String generation) {
        return connections.lockById(memberId).filter(connection -> connection.isGeneration(generation))
                .map(connection -> { connection.secretsUnreadable(Instant.now(clock)); return true; })
                .orElse(false);
    }
    /** 수동 동기화 쿨다운 기준: 마지막 시도(성공·실패·로그인) 시각. */
    @Transactional(readOnly = true)
    public Instant lastAttemptAt(long memberId) {
        return connections.findById(memberId).map(EclassConnection::getUpdatedAt)
                .orElseThrow(() -> new BusinessException(ErrorCode.ECLASS_REAUTH_REQUIRED));
    }
    @Transactional(readOnly = true)
    public EclassConnectionResponse status(long memberId) {
        if (members.findAuthSnapshot(memberId).isEmpty()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return connections.findById(memberId).map(EclassConnectionResponse::from)
                .orElse(new EclassConnectionResponse("DISCONNECTED", null, null, false));
    }
    public void disconnect(long memberId) {
        coordination.mutate(() -> {
        members.lockById(memberId).filter(Member::isActive).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        coordination.assertMutable(memberId);
        connections.deleteById(memberId);
        return null; });
    }
    /** 로그아웃용. 호출자의 트랜잭션에 합류하고, 연결이 없어도 성공한다. */
    @Transactional
    public void forgetConnection(long memberId) {
        coordination.assertMutable(memberId);
        connections.deleteById(memberId);
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
