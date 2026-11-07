package com.campuson.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 인증은 JWT 필터만 쓴다: 기본 in-memory 사용자(랜덤 비밀번호 로그)를 만들지 않는다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CampusOnApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusOnApiApplication.class, args);
    }
}
