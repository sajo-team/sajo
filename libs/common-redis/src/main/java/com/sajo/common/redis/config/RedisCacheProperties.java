package com.sajo.common.redis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "sajo.redis.cache")
@Getter
@Setter
public class RedisCacheProperties {
    private Map<String, Duration> ttl = new HashMap<>();
}
