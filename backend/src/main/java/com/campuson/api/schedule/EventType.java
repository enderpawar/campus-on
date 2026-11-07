package com.campuson.api.schedule;

/** 일정 종류. DB CHECK {@code ck_event_type}과 값이 같아야 한다. */
public enum EventType {
    ASSIGNMENT, QUIZ, LECTURE, EXAM, PERSONAL
}
