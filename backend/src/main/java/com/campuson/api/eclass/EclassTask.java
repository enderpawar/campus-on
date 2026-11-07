package com.campuson.api.eclass;

public record EclassTask(String externalId, String courseExternalId, String courseName,
                         String title, String type, java.time.Instant startsAt, java.time.Instant dueAt,
                         boolean dateOnly, Integer durationSeconds, String sourceUrl, String originalItemId, java.util.List<String> mergedExternalIds) {}
