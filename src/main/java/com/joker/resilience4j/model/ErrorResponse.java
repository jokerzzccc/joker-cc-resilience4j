package com.joker.resilience4j.model;

public record ErrorResponse(String message, String exception, String componentState) {

    public static ErrorResponse from(Throwable throwable, String componentState) {
        String message = throwable.getMessage() == null ? "Unexpected error" : throwable.getMessage();
        return new ErrorResponse(message, throwable.getClass().getSimpleName(), componentState);
    }
}
