package com.other.redis;

import com.sajo.other.redis.TestValue;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
class CountingService {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Cacheable(cacheNames = "test-cache", key = "#key")
    public TestValue getValue(String key) {
        invocationCount.incrementAndGet();
        return new TestValue(key, invocationCount.get(), Instant.now());
    }

    int getInvocationCount() {
        return invocationCount.get();
    }

    void reset() {
        invocationCount.set(0);
    }
}
