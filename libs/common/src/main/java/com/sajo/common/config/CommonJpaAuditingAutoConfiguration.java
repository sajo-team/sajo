package com.sajo.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@AutoConfiguration
@ConditionalOnClass(EnableJpaAuditing.class)
@ConditionalOnMissingBean(name = "jpaAuditingHandler")
@EnableJpaAuditing
public class CommonJpaAuditingAutoConfiguration {
}
