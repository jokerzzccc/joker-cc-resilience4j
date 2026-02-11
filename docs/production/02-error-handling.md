# 错误处理指南

## 概述

Resilience4j 与 Reactor 集成时，错误通过 Reactor 的错误信号传播。本文档介绍统一错误处理机制和最佳实践。

---

## 异常分类

### Resilience4j 异常

| 异常类 | 触发条件 | HTTP 状态码 | 含义 |
|--------|---------|-----------|------|
| `CallNotPermittedException` | CircuitBreaker 处于 OPEN 状态 | 503 | 服务暂时不可用 |
| `RequestNotPermitted` | RateLimiter 许可耗尽 | 429 | 请求频率过高 |

### 业务异常

| 异常类 | 触发条件 | HTTP 状态码 |
|--------|---------|-----------|
| `IllegalStateException` | 外部服务调用失败 | 500 |
| `TimeoutException` | 调用超时 | 504 |
| 其他 `Exception` | 未预期错误 | 500 |

---

## GlobalExceptionHandler

`@ControllerAdvice` 捕获 Controller 层未处理的异常，返回统一格式：

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        return Mono.just(ApiResponse.failure(
                ErrorResponse.from(ex, "OPEN")));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleRateLimited(RequestNotPermitted ex) {
        return Mono.just(ApiResponse.failure(
                ErrorResponse.from(ex, "RATE_LIMITED")));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleGeneric(Exception ex) {
        return Mono.just(ApiResponse.failure(
                ErrorResponse.from(ex, "ERROR")));
    }
}
```

---

## Service 层错误处理

Service 层通过 `onErrorResume` 在 Reactor 链中处理错误，将异常转为 `ApiResponse`：

```java
return source
    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
    .map(ApiResponse::success)
    .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
            ErrorResponse.from(throwable, circuitBreaker.getState().name())
    )));
```

### 处理层次

```
请求 → Controller → Service → Resilience4j → 外部调用
                                    │
                                    ├── Service.onErrorResume (首选)
                                    │   捕获并转为 ApiResponse
                                    │
                                    └── GlobalExceptionHandler (兜底)
                                        捕获未处理的异常
```

**最佳实践**：
- Service 层处理预期异常（CB open、rate limited、业务异常）
- GlobalExceptionHandler 处理非预期异常
- 两层配合确保没有异常泄露到客户端

---

## ErrorResponse 模型

```java
public record ErrorResponse(String message, String exception, String componentState) {

    public static ErrorResponse from(Throwable throwable, String componentState) {
        String message = throwable.getMessage() == null
                ? "Unexpected error" : throwable.getMessage();
        return new ErrorResponse(message,
                throwable.getClass().getSimpleName(), componentState);
    }
}
```

`componentState` 字段提供额外的上下文信息：
- `OPEN` / `HALF_OPEN` / `CLOSED` - CircuitBreaker 状态
- `RATE_LIMITED` - 限流状态
- `DEGRADED` - 降级状态
- `ERROR` - 通用错误

---

## 错误处理原则

1. **不吞异常**：所有异常必须被处理或记录，不能静默丢弃
2. **统一格式**：所有错误响应使用 `ApiResponse.failure(ErrorResponse)` 格式
3. **分类处理**：区分 Resilience4j 异常和业务异常，返回不同 HTTP 状态码
4. **日志记录**：GlobalExceptionHandler 中记录日志（WARN 级别用于预期异常，ERROR 用于非预期异常）
5. **不暴露内部信息**：错误消息对外部调用方安全，不泄露堆栈信息
