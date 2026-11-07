package com.campuson.api.schedule.ics;

import java.time.Instant;

/** dateOnly uses start as a KST calendar date carrier, not a midnight deadline. */
public record IcsEvent(String uid, String summary, String location, Instant start, Instant end, boolean dateOnly) {
}
