package com.campuson.api.eclass;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Dedicated-key AES-256-GCM. Neither JWT nor cron secrets may substitute for this key. */
@Component
public final class EclassSecrets {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public EclassSecrets(@Value("${campus.eclass.encryption-key}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalArgumentException("ECLASS_ENCRYPTION_KEY must contain at least 32 UTF-8 bytes");
        try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(bytes), "AES"); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException("AES key initialization failed", ex); }
    }
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        byte[] nonce = new byte[12]; random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return "v1." + encoder.encodeToString(nonce) + "." + encoder.encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Credential encryption failed", ex); }
    }
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        try {
            String[] parts = encrypted.split("\\.", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("Invalid envelope");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] nonce = decoder.decode(parts[1]);
            if (nonce.length != 12) throw new IllegalArgumentException("Invalid nonce");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(decoder.decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new UnreadableSecretException();
        }
    }
}
