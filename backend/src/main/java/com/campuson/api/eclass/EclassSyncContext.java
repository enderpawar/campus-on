package com.campuson.api.eclass;

/** Sensitive values are restricted to service memory and deliberately excluded from toString. */
public final class EclassSyncContext {
    private final String studentId;
    private final String sessionCookie;
    private final String password;
    private final String generation;
    EclassSyncContext(String studentId, String sessionCookie, String password, String generation) {
        this.studentId = studentId; this.sessionCookie = sessionCookie; this.password = password; this.generation = generation;
    }
    public String studentId() { return studentId; }
    public String sessionCookie() { return sessionCookie; }
    public String password() { return password; }
    /** 이 값을 읽은 연결 세대. 결과 반영은 이 세대가 여전히 현재일 때만 한다. */
    public String generation() { return generation; }
    @Override public String toString() { return "EclassSyncContext[redacted]"; }
}
