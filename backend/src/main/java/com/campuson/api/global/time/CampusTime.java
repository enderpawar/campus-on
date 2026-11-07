package com.campuson.api.global.time;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 응답 시각의 기준 시간대. 학교별 시간대는 3차 확장(다른 학교 어댑터) 때 도입한다. */
public final class CampusTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private CampusTime() {
    }

    public static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE).toOffsetDateTime();
    }
}
