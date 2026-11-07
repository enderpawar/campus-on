package com.campuson.api.schedule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Fits external e-class values into column lengths before they reach JPA. */
final class ImportLimits {
    static final int TITLE = 200;
    static final int COURSE_NAME = 300;
    static final int EXTERNAL_UID = 200;
    static final int SOURCE_URL = 2000;

    private ImportLimits() {}

    /** Truncates by code point so surrogate pairs are never split. */
    static String truncate(String value, int max) {
        if (value == null || value.codePointCount(0, value.length()) <= max) return value;
        return value.substring(0, value.offsetByCodePoints(0, max));
    }

    /** A cut URL points somewhere else; drop it instead. */
    static String url(String value) {
        return value == null || value.length() <= SOURCE_URL ? value : null;
    }

    /** Over-long ids are hashed so the identity stays stable and distinct across syncs. */
    static String externalUid(String value) {
        if (value == null || value.length() <= EXTERNAL_UID) return value;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "h:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
