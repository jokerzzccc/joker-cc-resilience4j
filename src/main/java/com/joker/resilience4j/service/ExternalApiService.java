package com.joker.resilience4j.service;

import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class ExternalApiService {

    public String callSuccess() {
        return "external-api-success";
    }

    public String callFailure() {
        throw new IllegalStateException("External API failure");
    }

    public String callSlow(Duration delay) {
        sleep(delay);
        return "external-api-slow";
    }

    private void sleep(Duration delay) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("External API interrupted", ex);
        }
    }
}
