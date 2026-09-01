package com.sajo.common.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@AutoConfiguration(before = DataRedisAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableCaching
@EnableConfigurationProperties(RedisCacheProperties.class)
public class CommonRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer(); // key는 String 직렬화
        GenericJacksonJsonRedisSerializer valueSerializer = jsonValueSerializer(objectMapper); // value는 JSON 직렬화 (타입 정보 포함)

        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setHashKeySerializer(keySerializer);
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
                                      @Value("${spring.application.name:application}") String applicationName,
                                     RedisCacheProperties properties) {

        Duration defaultTtl = properties.getTtl().getOrDefault("default", Duration.ofMinutes(10));


        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(defaultTtl) // default ttl
                .disableCachingNullValues() // null caching x
                .computePrefixWith(CacheKeyPrefix.prefixed(applicationName + ":")) // 서비스별로 캐시 키 네임스페이스 분리 (같은 Redis 공유해도 캐시 이름 충돌 안 나게)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())) // key 값은 String 직렬화
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonValueSerializer(objectMapper))); // value는 JSON 직렬화 (타입 정보 포함)

        Map<String, RedisCacheConfiguration> cacheConfiguration = properties.getTtl().entrySet().stream()
                .filter(entry -> !entry.getKey().equals("default"))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> defaultConfig.entryTtl(e.getValue())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfiguration)
                .build();
    }


    private static GenericJacksonJsonRedisSerializer jsonValueSerializer(ObjectMapper objectMapper) {
        // com.sajo 하위(이 프로젝트가 정의한 타입) 클래스만 역직렬화 허용
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.sajo.")
                .build();

        return GenericJacksonJsonRedisSerializer.builder(objectMapper::rebuild) // 앱 objectMapper 설정(모듈 등)을 이어받아 Redis 전용 복사본을 만듦
                .enableDefaultTyping(typeValidator) // json에 실제 클래스 타입 정보를 같이 넣음 (화이트리스트 검증 포함)
                .build();
    }
}
