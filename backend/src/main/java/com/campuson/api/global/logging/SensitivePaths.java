package com.campuson.api.global.logging;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.web.util.UriUtils;

/**
 * 경로에 자격 증명이 들어가는 요청(ICS 구독 토큰)을 로그와 오류 본문에 남기지 않도록 가린다 (ADR-0008).
 * 원문 접두사만 보면 {@code /%69cs/<token>.ics}처럼 percent-encoding한 요청이 빠져나가므로,
 * 디코딩하고 소문자로 바꾼 경로로 판단한다. 디코딩할 수 없는 경로는 통째로 가린다.
 */
public final class SensitivePaths {

    static final String MASKED_ICS = "/ics/***.ics";
    static final String MASKED_UNDECODABLE = "/***";

    private SensitivePaths() {
    }

    public static String mask(String requestUri) {
        String decoded;
        try {
            decoded = UriUtils.decode(requestUri, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return MASKED_UNDECODABLE;
        }
        return decoded.toLowerCase(Locale.ROOT).startsWith("/ics/") ? MASKED_ICS : requestUri;
    }
}
