package io.jenkins.plugins.globaltelegrambuildnotifier;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Result;
import hudson.model.Run;
import java.net.URI;
import java.time.Instant;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

final class BuildNotification {
    private final BuildEvent event;
    private final String controllerDisplayName;
    private final String controllerUrl;
    private final String jobFullName;
    private final int buildNumber;
    private final String buildRelativeUrl;
    private final String buildAbsoluteUrl;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final long durationSeconds;
    private final String result;
    private final String cause;

    private BuildNotification(Builder builder) {
        this.event = builder.event;
        this.controllerDisplayName = builder.controllerDisplayName;
        this.controllerUrl = builder.controllerUrl;
        this.jobFullName = builder.jobFullName;
        this.buildNumber = builder.buildNumber;
        this.buildRelativeUrl = builder.buildRelativeUrl;
        this.buildAbsoluteUrl = builder.buildAbsoluteUrl;
        this.startedAt = builder.startedAt;
        this.finishedAt = builder.finishedAt;
        this.durationSeconds = builder.durationSeconds;
        this.result = builder.result;
        this.cause = builder.cause;
    }

    static BuildNotification fromRun(
            @NonNull Run<?, ?> run,
            @NonNull BuildEvent event,
            @CheckForNull Result result,
            @NonNull GlobalTelegramBuildNotifierConfiguration config,
            @NonNull CauseResolver causeResolver) {
        String rootUrl = normalizeRootUrl(JenkinsLocationConfiguration.get().getUrl());
        String relativeUrl = run.getUrl();
        return new Builder()
                .event(event)
                .controllerDisplayName(config.effectiveJenkinsDisplayName())
                .controllerUrl(rootUrl)
                .jobFullName(run.getParent().getFullName())
                .buildNumber(run.getNumber())
                .buildRelativeUrl(relativeUrl)
                .buildAbsoluteUrl(resolveBuildUrl(rootUrl, relativeUrl))
                .startedAt(Instant.ofEpochMilli(run.getTimeInMillis()))
                .finishedAt(event == BuildEvent.COMPLETED ? Instant.ofEpochMilli(run.getTimeInMillis() + run.getDuration()) : null)
                .durationSeconds(event == BuildEvent.COMPLETED ? Math.max(0L, run.getDuration() / 1000L) : 0L)
                .result(result == null ? "UNKNOWN" : result.toString())
                .cause(causeResolver.resolve(run))
                .build();
    }

    private static String normalizeRootUrl(String rootUrl) {
        String trimmed = Texts.trimToNull(rootUrl);
        if (trimmed == null) {
            return null;
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private static String resolveBuildUrl(String rootUrl, String relativeUrl) {
        if (rootUrl == null) {
            return relativeUrl;
        }
        return URI.create(rootUrl).resolve(relativeUrl).toString();
    }

    BuildEvent getEvent() {
        return event;
    }

    String getControllerDisplayName() {
        return controllerDisplayName;
    }

    String getControllerUrl() {
        return controllerUrl;
    }

    String getJobFullName() {
        return jobFullName;
    }

    int getBuildNumber() {
        return buildNumber;
    }

    String getBuildRelativeUrl() {
        return buildRelativeUrl;
    }

    String getBuildAbsoluteUrl() {
        return buildAbsoluteUrl;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getFinishedAt() {
        return finishedAt;
    }

    long getDurationSeconds() {
        return durationSeconds;
    }

    String getResult() {
        return result;
    }

    String getCause() {
        return cause;
    }

    private static final class Builder {
        private BuildEvent event;
        private String controllerDisplayName;
        private String controllerUrl;
        private String jobFullName;
        private int buildNumber;
        private String buildRelativeUrl;
        private String buildAbsoluteUrl;
        private Instant startedAt;
        private Instant finishedAt;
        private long durationSeconds;
        private String result;
        private String cause;

        private Builder event(BuildEvent event) {
            this.event = event;
            return this;
        }

        private Builder controllerDisplayName(String controllerDisplayName) {
            this.controllerDisplayName = controllerDisplayName;
            return this;
        }

        private Builder controllerUrl(String controllerUrl) {
            this.controllerUrl = controllerUrl;
            return this;
        }

        private Builder jobFullName(String jobFullName) {
            this.jobFullName = jobFullName;
            return this;
        }

        private Builder buildNumber(int buildNumber) {
            this.buildNumber = buildNumber;
            return this;
        }

        private Builder buildRelativeUrl(String buildRelativeUrl) {
            this.buildRelativeUrl = buildRelativeUrl;
            return this;
        }

        private Builder buildAbsoluteUrl(String buildAbsoluteUrl) {
            this.buildAbsoluteUrl = buildAbsoluteUrl;
            return this;
        }

        private Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        private Builder finishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        private Builder durationSeconds(long durationSeconds) {
            this.durationSeconds = durationSeconds;
            return this;
        }

        private Builder result(String result) {
            this.result = result;
            return this;
        }

        private Builder cause(String cause) {
            this.cause = cause;
            return this;
        }

        private BuildNotification build() {
            return new BuildNotification(this);
        }
    }
}
