package com.sajo.common.kafka.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * sajo.kafka.type-mappings로 서비스별 이벤트 alias → DTO 클래스 매핑을 받는다:
 *
 * sajo:
 *   kafka:
 *     type-mappings:
 *       account.linked: com.sajo.f.dto.AccountLinkedConsumerDto
 *       order.executed: com.sajo.f.dto.OrderExecutedConsumerDto
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sajo.kafka")
public class KafkaProperties {

    private Map<String, String> typeMappings = new HashMap<>();
}
