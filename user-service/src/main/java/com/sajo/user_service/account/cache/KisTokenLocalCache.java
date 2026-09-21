package com.sajo.user_service.account.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class KisTokenLocalCache {
    private final AsyncCache<String, KisTokenEntry> cache;

    public KisTokenEntry getOrLoad(String key, Supplier<KisTokenEntry> loader) {
        CompletableFuture<KisTokenEntry> mine = new CompletableFuture<>();
        CompletableFuture<KisTokenEntry> existing = cache.asMap().putIfAbsent(key, mine);

        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException re ? re : e;
            }
        }
        try {
            KisTokenEntry result = loader.get();
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e); // 실패한 future는 캐시에서 자동 제거 - 다음 요청이 재시도
            throw e;
        }
    }

    public Optional<String> peek(String key) {
        CompletableFuture<KisTokenEntry> future = cache.getIfPresent(key);
        if (future != null && future.isDone() && !future.isCompletedExceptionally()) {

            return Optional.of(future.join().value());
        }
        return Optional.empty();
    }

    public void evict(String key) {
        cache.synchronous().invalidate(key);
    }
}
