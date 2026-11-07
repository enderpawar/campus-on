package com.campuson.api.eclass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eclass_connections")
public class EclassConnection {
    @Id @Column(name = "user_id") private Long memberId;
    @Column(name = "encrypted_session_cookie", nullable = false, length = 24000) private String encryptedSessionCookie;
    @Column(name = "encrypted_password", length = 8000) private String encryptedPassword;
    @Column(name = "sync_state", nullable = false, length = 20) private String syncState;
    @Column(name = "last_synced_at") private Instant lastSyncedAt;
    @Column(name = "last_error_code", length = 50) private String lastErrorCode;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "connection_generation", nullable = false, length = 36) private String generation;
    protected EclassConnection() {}
    public EclassConnection(long memberId) { this.memberId = memberId; }
    /** 로그인마다 새 세대를 시작한다. 이전 세대로 시작된 동기화의 결과는 반영되지 않는다. */
    public void authenticate(String cookie, String password, Instant now) {
        encryptedSessionCookie = cookie; encryptedPassword = password;
        syncState = "PENDING"; lastErrorCode = null; updatedAt = now;
        generation = UUID.randomUUID().toString();
    }
    public boolean isGeneration(String expected) { return generation.equals(expected); }
    public void synced(String cookie, Instant now) {
        encryptedSessionCookie = cookie; syncState = "READY"; lastSyncedAt = now; lastErrorCode = null; updatedAt = now;
    }
    /**
     * 학교가 저장 비밀번호를 거절했으면(INVALID_CREDENTIALS) 그 비밀번호는 지운다. 남겨 두면 배경 동기화가
     * 같은 틀린 비밀번호로 계속 로그인해 학교 계정 잠금을 유발한다. 두 경우 모두 사용자 재로그인이 필요하다.
     */
    public void failed(String errorCode, Instant now) {
        boolean rejected = "INVALID_CREDENTIALS".equals(errorCode);
        if (rejected) encryptedPassword = null;
        syncState = rejected || "ECLASS_REAUTH_REQUIRED".equals(errorCode) ? "REAUTH_REQUIRED" : "FAILED";
        lastErrorCode = errorCode; updatedAt = now;
    }
    /** 저장 값을 복호화할 수 없다(키 교체·손상). 비밀번호는 쓸모가 없으니 지우고 재로그인을 요구한다. */
    public void secretsUnreadable(Instant now) {
        encryptedPassword = null;
        syncState = "REAUTH_REQUIRED"; lastErrorCode = "ECLASS_REAUTH_REQUIRED"; updatedAt = now;
    }
    public Long getMemberId() { return memberId; }
    public String getEncryptedSessionCookie() { return encryptedSessionCookie; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public String getSyncState() { return syncState; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getGeneration() { return generation; }
}
