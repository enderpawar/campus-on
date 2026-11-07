package com.campuson.api.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
