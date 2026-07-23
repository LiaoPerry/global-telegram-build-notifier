package io.jenkins.plugins.globaltelegrambuildnotifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextsTest {
    @Test
    void parsesCommaAndNewLineSeparatedChatIds() {
        assertEquals(3, Texts.parseChatIds("123, 456\n-100789\r\n").size());
        assertEquals("123", Texts.parseChatIds("123, 456\n-100789\r\n").get(0));
    }

    @Test
    void truncatesWithoutBreakingSurrogatePair() {
        String longText = "a".repeat(3899) + "🚀" + "tail";
        String truncated = Texts.truncateTelegramMessage(longText);
        assertTrue(truncated.contains("...[truncated]"));
        assertTrue(truncated.length() <= Texts.TELEGRAM_MAX_TEXT_LENGTH);
    }
}
