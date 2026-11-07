package com.campuson.api.eclass;

/**
 * 저장된 암호문을 현재 키로 풀 수 없다. 일시 장애가 아니라 재로그인이 필요한 상태다.
 * {@link #generation()}은 어떤 연결 세대의 값이었는지다(세대가 바뀐 뒤에는 그 연결을 건드리지 않는다).
 */
public class UnreadableSecretException extends IllegalStateException {
    private final String generation;
    UnreadableSecretException() { this(null); }
    UnreadableSecretException(String generation) {
        super("Stored e-class credentials could not be decrypted");
        this.generation = generation;
    }
    public String generation() { return generation; }
}
