package com.campuson.api.global.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SensitivePathsTest {

    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd";

    @Test
    void icsTokenIsMaskedEvenWhenPrefixIsPercentEncodedOrUppercase() {
        assertEquals("/ics/***.ics", SensitivePaths.mask("/ics/" + TOKEN + ".ics"));
        assertEquals("/ics/***.ics", SensitivePaths.mask("/%69cs/" + TOKEN + ".ics"));
        assertEquals("/ics/***.ics", SensitivePaths.mask("/ICS/" + TOKEN + ".ics"));
    }

    @Test
    void undecodablePathIsMaskedEntirely() {
        assertEquals("/***", SensitivePaths.mask("/ics%ZZ/" + TOKEN));
    }

    @Test
    void ordinaryPathIsKeptAsIs() {
        assertEquals("/api/events/1", SensitivePaths.mask("/api/events/1"));
    }
}
