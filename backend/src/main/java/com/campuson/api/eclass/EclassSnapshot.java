package com.campuson.api.eclass;

public record EclassSnapshot(java.util.List<EclassCourse> courses, java.util.List<EclassTask> tasks) {
    public EclassSnapshot {
        courses = java.util.List.copyOf(courses);
        tasks = java.util.List.copyOf(tasks);
    }
}
