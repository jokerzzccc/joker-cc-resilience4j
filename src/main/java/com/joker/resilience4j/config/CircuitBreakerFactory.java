package com.joker.resilience4j.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
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

public class CircuitBreakerFactory {

    public enum ConfigTemplate {
        FAST_FAIL,
        SLOW_CALL,
        HYBRID
    }

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerFactory.class);

    private final CircuitBreakerConfig baseConfig;
    private final EnumMap<ConfigTemplate, CircuitBreakerConfig> templates;
    private final ConcurrentMap<String, CircuitBreaker> cache = new ConcurrentHashMap<>();
    private final Set<String> listenerAttached = ConcurrentHashMap.newKeySet();

    public CircuitBreakerFactory(CircuitBreakerConfig baseConfig) {
        this.baseConfig = baseConfig;
        this.templates = new EnumMap<>(ConfigTemplate.class);
        this.templates.put(ConfigTemplate.FAST_FAIL, CircuitBreakerConfig.custom()
                .failureRateThreshold(25.0f)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .permittedNumberOfCallsInHalfOpenState(1)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .build());
        this.templates.put(ConfigTemplate.SLOW_CALL, CircuitBreakerConfig.custom()
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofMillis(400))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(3))
                .build());
        this.templates.put(ConfigTemplate.HYBRID, baseConfig);
    }

    public CircuitBreaker create(String name, ConfigTemplate template) {
        CircuitBreakerConfig config = templates.getOrDefault(template, baseConfig);
        return create(name, config);
    }

    public CircuitBreaker create(String name, CircuitBreakerConfig config) {
        CircuitBreaker circuitBreaker = cache.computeIfAbsent(name, key -> CircuitBreaker.of(key, config));
        attachListenersIfNeeded(circuitBreaker);
        return circuitBreaker;
    }

    public CircuitBreaker create(String name, Consumer<CircuitBreakerConfig.Builder> customizer) {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
        customizer.accept(builder);
        return create(name, builder.build());
    }

    public CircuitBreaker update(String name, CircuitBreakerConfig config) {
        listenerAttached.remove(name);
        CircuitBreaker circuitBreaker = CircuitBreaker.of(name, config);
        cache.put(name, circuitBreaker);
        attachListenersIfNeeded(circuitBreaker);
        return circuitBreaker;
    }

    public Optional<CircuitBreaker> get(String name) {
        return Optional.ofNullable(cache.get(name));
    }

    public Set<String> listNames() {
        return new TreeSet<>(cache.keySet());
    }

    private void attachListenersIfNeeded(CircuitBreaker circuitBreaker) {
        if (!listenerAttached.add(circuitBreaker.getName())) {
            return;
        }

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.info(
                        "circuitBreaker={} transition {} -> {}",
                        event.getCircuitBreakerName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()
                ))
                .onFailureRateExceeded(event -> log.warn(
                        "circuitBreaker={} failureRateExceeded={}%",
                        event.getCircuitBreakerName(),
                        event.getFailureRate()
                ))
                .onSlowCallRateExceeded(event -> log.warn(
                        "circuitBreaker={} slowCallRateExceeded={}%",
                        event.getCircuitBreakerName(),
                        event.getSlowCallRate()
                ))
                .onCallNotPermitted(event -> log.warn(
                        "circuitBreaker={} callNotPermitted",
                        event.getCircuitBreakerName()
                ));
    }
}
