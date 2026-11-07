package com.campuson.api.schedule.ics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsWriterTest {

    private static final Instant STAMP = Instant.parse("2026-11-06T03:00:00Z");
    private static final Instant DUE = Instant.parse("2026-11-08T05:00:00Z");

    @Test
    void escapesCommaSemicolonBackslashAndNewline() {
        IcsEvent event = new IcsEvent("event-1@campus-on", "a,b;c\\d\ne", null, DUE, null, false);

        String ics = IcsWriter.write("캘린더", List.of(event), STAMP);

        assertTrue(ics.contains("SUMMARY:a\\,b\\;c\\\\d\\ne\r\n"), ics);
    }

    @Test
    void foldsLongUtf8LinesWithoutSplittingCharacters() {
        String title = "가".repeat(60);
        IcsEvent event = new IcsEvent("event-1@campus-on", title, null, DUE, null, false);

        String ics = IcsWriter.write("캘린더", List.of(event), STAMP);

        assertTrue(ics.endsWith("\r\n"));
        for (String physical : ics.split("\r\n")) {
            int octets = physical.getBytes(StandardCharsets.UTF_8).length;
            assertTrue(octets <= 75, "75옥텟 초과: " + octets + " " + physical);
            assertFalse(physical.contains("\uFFFD"));
        }
        String unfolded = ics.replace("\r\n ", "");
        assertTrue(unfolded.contains("\r\nSUMMARY:" + title + "\r\n"));
    }

    @Test
    void deadlineWithoutStartHasNoDtend() {
        IcsEvent deadline = new IcsEvent("event-1@campus-on", "마감", null, DUE, null, false);
        IcsEvent meeting = new IcsEvent("event-2@campus-on", "회의", "미래관 101", DUE.minusSeconds(3600), DUE, false);

        String ics = IcsWriter.write("캘린더", List.of(deadline), STAMP);
        String withRange = IcsWriter.write("캘린더", List.of(meeting), STAMP);

        assertTrue(ics.contains("DTSTART:20261108T050000Z\r\n"));
        assertFalse(ics.contains("DTEND"));
        assertFalse(ics.contains("LOCATION"));
        assertTrue(withRange.contains("DTSTART:20261108T040000Z\r\nDTEND:20261108T050000Z\r\n"));
        assertTrue(withRange.contains("LOCATION:미래관 101\r\n"));
        assertEquals(1, withRange.split("BEGIN:VEVENT", -1).length - 1);
    }

    @Test
    void dateOnlyUsesKstDateWithoutInventedTimeOrEnd() {
        IcsEvent event = new IcsEvent("event-3@campus-on", "날짜만 제공", null,
                Instant.parse("2026-11-07T15:00:00Z"), null, true);
        String ics = IcsWriter.write("캘린더", List.of(event), STAMP);
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261108\r\n"), ics);
        assertFalse(ics.contains("DTSTART:"));
        assertFalse(ics.contains("DTEND"));
    }
}
