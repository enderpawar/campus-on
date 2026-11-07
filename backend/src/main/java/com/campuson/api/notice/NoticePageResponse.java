package com.campuson.api.notice;

import java.util.List;
import org.springframework.data.domain.Page;

/** Page를 그대로 직렬화하지 않는다. Page의 JSON 형식은 버전마다 바뀔 수 있어 응답 계약으로 쓰기 불안정하다. */
public record NoticePageResponse(List<NoticeResponse> items, int page, int size, long totalElements, boolean hasNext) {

    static NoticePageResponse from(Page<Notice> p) {
        return new NoticePageResponse(p.map(NoticeResponse::from).getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.hasNext());
    }
}
