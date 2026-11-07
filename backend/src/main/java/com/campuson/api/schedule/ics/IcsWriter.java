package com.campuson.api.schedule.ics;

import com.campuson.api.global.time.CampusTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RFC 5545 iCalendar 문자열을 만드는 순수 함수.
 * 줄바꿈은 CRLF, 75옥텟을 넘는 줄은 UTF-8 코드포인트 경계에서 접는다(folding).
 */
public final class IcsWriter {

    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;
    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private IcsWriter() {
    }

    public static String write(String calendarName, List<IcsEvent> events, Instant dtstamp) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//CampusOn//Planary//KO");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "X-WR-CALNAME:" + escape(calendarName));
        String stamp = UTC_FORMAT.format(dtstamp);
        for (IcsEvent event : events) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + event.uid());
            line(out, "DTSTAMP:" + stamp);
            if (event.dateOnly()) {
                line(out, "DTSTART;VALUE=DATE:" + DateTimeFormatter.BASIC_ISO_DATE.format(
                        event.start().atZone(CampusTime.ZONE).toLocalDate()));
            } else {
                line(out, "DTSTART:" + UTC_FORMAT.format(event.start()));
                if (event.end() != null) {
                    line(out, "DTEND:" + UTC_FORMAT.format(event.end()));
                }
            }
            line(out, "SUMMARY:" + escape(event.summary()));
            if (event.location() != null && !event.location().isBlank()) {
                line(out, "LOCATION:" + escape(event.location()));
            }
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** TEXT 값 이스케이프. 백슬래시를 가장 먼저 바꿔야 뒤에서 넣은 백슬래시가 두 번 바뀌지 않는다. */
    static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replaceAll("\r\n|\r|\n", "\\\\n");
    }

    /** 첫 줄은 75옥텟, 이어지는 줄은 앞의 공백 1칸을 포함해 75옥텟 이하로 자른다. */
    private static void line(StringBuilder out, String content) {
        int octets = 0;
        int i = 0;
        while (i < content.length()) {
            int codePoint = content.codePointAt(i);
            int size = utf8Length(codePoint);
            if (octets + size > MAX_LINE_OCTETS) {
                out.append(CRLF).append(' ');
                octets = 1;
            }
            out.appendCodePoint(codePoint);
            octets += size;
            i += Character.charCount(codePoint);
        }
        out.append(CRLF);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
