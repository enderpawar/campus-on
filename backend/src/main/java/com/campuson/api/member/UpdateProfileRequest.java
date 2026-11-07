package com.campuson.api.member;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 프로필 부분 수정(PATCH). 필드마다 "생략 = 유지 / null = 비우기 / 값 = 변경"을 구분한다.
 * record + {@code Optional}로는 구분되지 않는다: Jackson이 생략된 생성자 인자에도 {@code Optional.empty()}를 넣어
 * 생략이 비우기로 바뀐다(테스트로 확인). 그래서 Jackson이 본문에 있는 필드에만 setter를 호출한다는 점을 이용해
 * (존재 여부를 따로 기록한다.) 학교·학번·이름·e-class 비밀번호는 이 요청으로 바꾸지 않는다.
 */
public final class UpdateProfileRequest {

    @Size(max = 200)
    private String department;
    private boolean departmentPresent;

    @Min(1)
    @Max(6)
    private Integer grade;
    private boolean gradePresent;

    @JsonSetter("department")
    void setDepartment(String department) {
        this.department = department;
        this.departmentPresent = true;
    }

    @JsonSetter("grade")
    void setGrade(Integer grade) {
        this.grade = grade;
        this.gradePresent = true;
    }

    boolean hasDepartment() {
        return departmentPresent;
    }

    String department() {
        return department;
    }

    boolean hasGrade() {
        return gradePresent;
    }

    Integer grade() {
        return grade;
    }
}
