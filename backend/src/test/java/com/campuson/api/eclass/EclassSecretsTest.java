package com.campuson.api.eclass;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EclassSecretsTest {
    private final EclassSecrets secrets = new EclassSecrets("test-only-dedicated-encryption-key-long-enough");
    @Test void encryptedSecretsRoundTripWithDistinctNoncesAndRejectTamperingOrAnotherKey() {
        String plaintext = "비밀 학교 비밀번호";
        String first = secrets.encrypt(plaintext), second = secrets.encrypt(plaintext);
        assertNotEquals(first, second);
        assertEquals(plaintext, secrets.decrypt(first));
        assertEquals(plaintext, secrets.decrypt(second));
        String[] parts = first.split("\\.");
        byte[] bytes = Base64.getUrlDecoder().decode(parts[2]); bytes[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        assertThrows(IllegalStateException.class, () -> secrets.decrypt(tampered));
        assertThrows(IllegalStateException.class, () -> new EclassSecrets("another-test-only-separate-encryption-key").decrypt(first));
    }
}
