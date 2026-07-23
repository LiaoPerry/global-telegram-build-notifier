package io.jenkins.plugins.globaltelegrambuildnotifier;

final class TelegramRequest {
    private final String chatId;
    private final String messageThreadId;
    private final String text;

    TelegramRequest(String chatId, String messageThreadId, String text) {
        this.chatId = chatId;
        this.messageThreadId = messageThreadId;
        this.text = text;
    }

    String toFormBody() {
        StringBuilder body = new StringBuilder();
        body.append("chat_id=").append(Texts.formEncode(chatId));
        if (Texts.trimToNull(messageThreadId) != null) {
            body.append("&message_thread_id=").append(Texts.formEncode(messageThreadId));
        }
        body.append("&text=").append(Texts.formEncode(text));
        body.append("&disable_web_page_preview=true");
        return body.toString();
    }
}
