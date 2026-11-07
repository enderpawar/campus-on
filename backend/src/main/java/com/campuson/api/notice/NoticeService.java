package com.campuson.api.notice;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    /** 내 학교 공지만 최신순으로 돌려준다. 같은 시각이면 id 역순으로 순서를 고정한다. */
    @Transactional(readOnly = true)
    public NoticePageResponse list(long schoolId, NoticeCategory category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("id")));
        var result = category == null
                ? noticeRepository.findAllBySchoolId(schoolId, pageable)
                : noticeRepository.findAllBySchoolIdAndCategory(schoolId, category, pageable);
        return NoticePageResponse.from(result);
    }

    /** 다른 학교 공지는 존재 여부를 숨기기 위해 404로 응답한다. */
    @Transactional(readOnly = true)
    public NoticeResponse get(long schoolId, long id) {
        return noticeRepository.findByIdAndSchoolId(id, schoolId)
                .map(NoticeResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
    }
}
