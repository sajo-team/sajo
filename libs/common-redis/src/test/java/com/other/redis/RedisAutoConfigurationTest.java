package com.other.redis;

import com.other.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@DisplayName("Redis 자동 설정(빈 등록) 테스트")
class RedisAutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("Boot 기본 redisTemplate 빈은 안 만들어지고 우리가 등록한 것만 남는다")
    void bootDoesNotCreateItsOwnPlainRedisTemplateAlongsideOurs() {
        assertThat(redisTemplate.getClass()).isEqualTo(RedisTemplate.class);
        assertThat(context.getBeansOfType(RedisTemplate.class))
                .hasSize(2)
                .containsKeys("redisTemplate", "stringRedisTemplate");
        assertThat(context.getBean("stringRedisTemplate")).isInstanceOf(StringRedisTemplate.class);
    }

    @Test
    @DisplayName("redisTemplate은 key는 String, value는 JSON 직렬화기를 쓴다")
    void redisTemplateUsesJsonValueSerializerAndStringKeySerializer() {
        assertThat(redisTemplate.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(redisTemplate.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(redisTemplate.getValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
        assertThat(redisTemplate.getHashValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
    }
}
