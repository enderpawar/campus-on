package com.campuson.api.auth;

import java.text.Normalizer;
import java.util.Locale;
import com.campuson.api.global.error.*;

public final class StudentIds {
    private StudentIds() {}
    public static String normalize(String value) {
        if (value == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        String id = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        if (id.isEmpty() || id.length() > 64 || id.codePoints().anyMatch(c -> Character.isWhitespace(c)
                || Character.isSpaceChar(c) || Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        return id;
    }
    public static String bucket(String value) { return normalize(value).toLowerCase(Locale.ROOT); }
}
