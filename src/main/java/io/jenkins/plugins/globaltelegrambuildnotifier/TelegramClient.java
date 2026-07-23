package io.jenkins.plugins.globaltelegrambuildnotifier;

import hudson.ProxyConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TelegramClient {
    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile("\"retry_after\"\\s*:\\s*(\\d+)");

    TelegramResponse send(TelegramRequest request, String token, GlobalTelegramBuildNotifierConfiguration config)
            throws IOException {
        URL endpoint = new URL(config.getTelegramApiUrl().replaceAll("/+$", "") + "/bot" + token + "/sendMessage");
        HttpURLConnection connection = (HttpURLConnection) ProxyConfiguration.open(endpoint);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(config.getConnectTimeoutMillis());
        connection.setReadTimeout(config.getReadTimeoutMillis());
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        byte[] body = request.toFormBody().getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body);
        }

        int status = connection.getResponseCode();
        String responseBody = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        return new TelegramResponse(status, retryAfterSeconds(responseBody));
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream inputStream = stream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Integer retryAfterSeconds(String body) {
        Matcher matcher = RETRY_AFTER_PATTERN.matcher(body == null ? "" : body);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
}
