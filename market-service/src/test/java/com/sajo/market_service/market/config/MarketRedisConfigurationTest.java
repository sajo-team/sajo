package com.sajo.market_service.market.config;

import com.sajo.market_service.market.dto.response.QuoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MarketRedisConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void serializesAndDeserializesQuoteResponseAsJson() {
        RedisTemplate<String, QuoteResponse> redisTemplate = new MarketRedisConfiguration()
                .quoteRedisTemplate(mock(RedisConnectionFactory.class));
        RedisSerializer<QuoteResponse> serializer =
                (RedisSerializer<QuoteResponse>) redisTemplate.getValueSerializer();
        QuoteResponse quote = new QuoteResponse(
                "005930", 70_000L, 69_000L, 70_500L, 68_800L, 69_500L,
                500L, new BigDecimal("0.7194"), 123_456L, 8_610_000_000L,
                4_180_000L, new BigDecimal("15.20"), new BigDecimal("1.35"),
                new BigDecimal("4605.00"), new BigDecimal("51850.00"), "2026-09-04T14:30:00+09:00"
        );

        byte[] serialized = serializer.serialize(quote);

        assertThat(serializer.deserialize(serialized)).isEqualTo(quote);
    }
}
