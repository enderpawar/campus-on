package com.campuson.api.schedule;

/** 일정 출처. e-class에서 가져온 일정은 수정·삭제할 수 없다. DB CHECK {@code ck_event_source}와 값이 같아야 한다. */
public enum EventSource {
    ECLASS, PERSONAL
}
