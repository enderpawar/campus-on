package com.campuson.api.school;

public record SchoolResponse(Long id, String name) {

    static SchoolResponse from(School s) {
        return new SchoolResponse(s.getId(), s.getName());
    }
}
