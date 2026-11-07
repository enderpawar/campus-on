package com.campuson.api.notice;

import com.campuson.api.global.time.CampusTime;
import java.time.OffsetDateTime;
import java.util.List;

public record NoticeResponse(Long id, NoticeCategory category, String source, String title, String url,
                             String summary, String body, Integer targetGrade, List<String> targetDepartments,
                             OffsetDateTime postedAt) {

    static NoticeResponse from(Notice n) {
        return new NoticeResponse(n.getId(), n.getCategory(), n.getSource(), n.getTitle(), n.getUrl(),
                n.getSummary(), n.getBody(), n.getTargetGrade(), n.getTargetDepartments(), CampusTime.toOffset(n.getPostedAt()));
    }
}
