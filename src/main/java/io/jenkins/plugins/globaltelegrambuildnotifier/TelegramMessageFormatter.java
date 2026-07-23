package io.jenkins.plugins.globaltelegrambuildnotifier;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class TelegramMessageFormatter {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z").withZone(ZoneId.systemDefault());

    String format(BuildNotification notification) {
        StringBuilder message = new StringBuilder();
        if (notification.getEvent() == BuildEvent.STARTED) {
            message.append("🚀 Jenkins Build Started\n\n");
        } else {
            message.append(resultIcon(notification.getResult()))
                    .append(" Jenkins Build ")
                    .append(resultTitle(notification.getResult()))
                    .append("\n\n");
        }
        message.append("Controller: ").append(notification.getControllerDisplayName()).append("\n");
        if (notification.getControllerUrl() != null) {
            message.append("Controller URL: ").append(notification.getControllerUrl()).append("\n");
        }
        message.append("Job: ").append(notification.getJobFullName()).append("\n");
        message.append("Build: #").append(notification.getBuildNumber()).append("\n");
        if (notification.getEvent() == BuildEvent.COMPLETED) {
            message.append("Result: ").append(notification.getResult()).append("\n");
        }
        message.append("Triggered by: ").append(notification.getCause()).append("\n");
        message.append("Started at: ").append(DATE_TIME_FORMATTER.format(notification.getStartedAt())).append("\n");
        if (notification.getEvent() == BuildEvent.COMPLETED) {
            message.append("Finished at: ").append(DATE_TIME_FORMATTER.format(notification.getFinishedAt())).append("\n");
            message.append("Duration: ").append(notification.getDurationSeconds()).append(" seconds\n");
        }
        message.append("URL: ").append(notification.getBuildAbsoluteUrl());
        return Texts.truncateTelegramMessage(message.toString());
    }

    private String resultIcon(String result) {
        if ("SUCCESS".equals(result)) {
            return "✅";
        }
        if ("UNSTABLE".equals(result)) {
            return "⚠️";
        }
        if ("ABORTED".equals(result)) {
            return "⏹️";
        }
        return "❌";
    }

    private String resultTitle(String result) {
        if ("SUCCESS".equals(result)) {
            return "Succeeded";
        }
        if ("UNSTABLE".equals(result)) {
            return "Unstable";
        }
        if ("ABORTED".equals(result)) {
            return "Aborted";
        }
        return "Failed";
    }
}
