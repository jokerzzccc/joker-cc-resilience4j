package com.joker.resilience4j.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimiterFactory {

    public enum ConfigTemplate {
        STRICT,
        LENIENT,
        BURST
    }

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFactory.class);

    private final RateLimiterConfig baseConfig;
    private final EnumMap<ConfigTemplate, RateLimiterConfig> templates;
    private final ConcurrentMap<String, RateLimiter> cache = new ConcurrentHashMap<>();
    private final Set<String> listenerAttached = ConcurrentHashMap.newKeySet();

    public RateLimiterFactory(RateLimiterConfig baseConfig) {
        this.baseConfig = baseConfig;
        this.templates = new EnumMap<>(ConfigTemplate.class);
        this.templates.put(ConfigTemplate.STRICT, RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        this.templates.put(ConfigTemplate.LENIENT, RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(500))
                .build());
        this.templates.put(ConfigTemplate.BURST, baseConfig);
    }

    public RateLimiter create(String name, ConfigTemplate template) {
        RateLimiterConfig config = templates.getOrDefault(template, baseConfig);
        return create(name, config);
    }

    public RateLimiter create(String name, RateLimiterConfig config) {
        RateLimiter rateLimiter = cache.computeIfAbsent(name, key -> RateLimiter.of(key, config));
        attachListenersIfNeeded(rateLimiter);
        return rateLimiter;
    }

    public RateLimiter create(String name, Consumer<RateLimiterConfig.Builder> customizer) {
        RateLimiterConfig.Builder builder = RateLimiterConfig.custom();
        customizer.accept(builder);
        return create(name, builder.build());
    }

    public RateLimiter update(String name, RateLimiterConfig config) {
        listenerAttached.remove(name);
        RateLimiter rateLimiter = RateLimiter.of(name, config);
        cache.put(name, rateLimiter);
        attachListenersIfNeeded(rateLimiter);
        return rateLimiter;
    }

    public Optional<RateLimiter> get(String name) {
        return Optional.ofNullable(cache.get(name));
    }

    public Set<String> listNames() {
        return new TreeSet<>(cache.keySet());
    }

    private void attachListenersIfNeeded(RateLimiter rateLimiter) {
        if (!listenerAttached.add(rateLimiter.getName())) {
            return;
        }

        rateLimiter.getEventPublisher()
                .onSuccess(event -> log.info(
                        "rateLimiter={} permitted type={}",
                        event.getRateLimiterName(),
                        event.getEventType()
                ))
                .onFailure(event -> log.warn(
                        "rateLimiter={} rejected type={}",
                        event.getRateLimiterName(),
                        event.getEventType()
                ));
    }
}
