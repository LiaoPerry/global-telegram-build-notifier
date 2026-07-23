package io.jenkins.plugins.globaltelegrambuildnotifier;

final class TelegramResponse {
    private final int statusCode;
    private final Integer retryAfterSeconds;

    TelegramResponse(int statusCode, Integer retryAfterSeconds) {
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    int getStatusCode() {
        return statusCode;
    }

    Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
