package com.campuson.api.school;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "school")
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "lms_base_url", nullable = false, length = 255)
    private String lmsBaseUrl;

    protected School() {
    }

    public School(String name, String lmsBaseUrl) {
        this.name = name;
        this.lmsBaseUrl = lmsBaseUrl;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLmsBaseUrl() {
        return lmsBaseUrl;
    }
}
