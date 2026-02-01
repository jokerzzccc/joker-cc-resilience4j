package examples.basic.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;

public class BasicCircuitBreakerExample {

    public static void main(String[] args) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("example", config);

        for (int i = 0; i < 4; i++) {
            try {
                String result = circuitBreaker.executeSupplier(() -> {
                    if (i % 2 == 0) {
                        throw new IllegalStateException("boom");
                    }
                    return "ok";
                });
                System.out.println("call=" + i + " result=" + result);
            } catch (Exception ex) {
                System.out.println("call=" + i + " error=" + ex.getMessage());
            }
        }

        System.out.println("state=" + circuitBreaker.getState());
    }
}
