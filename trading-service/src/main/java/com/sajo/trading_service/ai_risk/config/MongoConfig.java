package com.sajo.trading_service.ai_risk.config;

import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer uuidCustomizer(){
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}
