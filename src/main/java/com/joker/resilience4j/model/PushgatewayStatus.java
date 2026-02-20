package com.joker.resilience4j.model;

public record PushgatewayStatus(
        String jobName,
        String instanceName,
        String pushgatewayState,
        String lastPushTime,
        String lastPushResult
) {
}
