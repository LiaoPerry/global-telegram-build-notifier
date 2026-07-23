package io.jenkins.plugins.globaltelegrambuildnotifier;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class Texts {
    static final int TELEGRAM_MAX_TEXT_LENGTH = 4096;
    private static final int SAFE_TELEGRAM_TEXT_LENGTH = 3900;

    private Texts() {}

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static List<String> parseChatIds(String chatIds) {
        List<String> parsed = new ArrayList<>();
        String trimmed = trimToNull(chatIds);
        if (trimmed == null) {
            return parsed;
        }
        for (String token : trimmed.split("[,\\r\\n]+")) {
            String chatId = trimToNull(token);
            if (chatId != null) {
                parsed.add(chatId);
            }
        }
        return parsed;
    }

    static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String truncateTelegramMessage(String value) {
        if (value == null || value.length() <= SAFE_TELEGRAM_TEXT_LENGTH) {
            return value;
        }
        int end = value.offsetByCodePoints(0, value.codePointCount(0, SAFE_TELEGRAM_TEXT_LENGTH));
        return value.substring(0, end) + "\n...[truncated]";
    }
}
