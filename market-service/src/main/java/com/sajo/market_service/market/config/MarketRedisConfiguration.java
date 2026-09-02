package com.sajo.market_service.market.config;

import com.sajo.market_service.market.dto.response.QuoteResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class MarketRedisConfiguration {

    @Bean
    RedisTemplate<String, QuoteResponse> quoteRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, QuoteResponse> redisTemplate = new RedisTemplate<>();
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        JacksonJsonRedisSerializer<QuoteResponse> valueSerializer =
                new JacksonJsonRedisSerializer<>(QuoteResponse.class);

        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashKeySerializer(keySerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
