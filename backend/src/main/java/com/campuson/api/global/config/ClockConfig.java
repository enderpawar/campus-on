package com.campuson.api.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간 의존 코드를 테스트에서 고정 Clock으로 바꿀 수 있게 한다 (Day 34: Thread.sleep 의존 제거). */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
