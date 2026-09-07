package com.sajo.market_service.market.config;

import com.sajo.market_service.market.dto.response.QuoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

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

    @Test
    @SuppressWarnings("unchecked")
    void deserializesLegacyQuoteJsonWithoutBusinessDate() {
        RedisTemplate<String, QuoteResponse> redisTemplate = new MarketRedisConfiguration()
                .quoteRedisTemplate(mock(RedisConnectionFactory.class));
        RedisSerializer<QuoteResponse> serializer =
                (RedisSerializer<QuoteResponse>) redisTemplate.getValueSerializer();
        String legacyJson = """
                {"stockCode":"005930","currentPrice":70000,"openPrice":69000,"highPrice":70500,
                "lowPrice":68800,"previousClosePrice":69500,"changePrice":500,"changeRate":0.7194,
                "accumulatedVolume":123456,"tradeAmount":8610000000,"marketCapitalization":4180000,
                "per":15.20,"pbr":1.35,"eps":4605.00,"bps":51850.00,
                "baseTime":"2026-09-04T14:30:00+09:00"}
                """;

        QuoteResponse quote = serializer.deserialize(legacyJson.getBytes(StandardCharsets.UTF_8));

        assertThat(quote.businessDate()).isNull();
        assertThat(quote.stockCode()).isEqualTo("005930");
        assertThat(quote.currentPrice()).isEqualTo(70_000L);
        assertThat(quote.per()).isEqualByComparingTo("15.20");
    }
}
