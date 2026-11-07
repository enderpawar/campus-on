package com.campuson.api.school;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolService {

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Transactional(readOnly = true)
    public List<SchoolResponse> list() {
        return schoolRepository.findAllByOrderByNameAsc().stream().map(SchoolResponse::from).toList();
    }
}
