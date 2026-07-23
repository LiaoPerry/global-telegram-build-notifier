package io.jenkins.plugins.globaltelegrambuildnotifier;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class BuildCompletionListener extends RunListener<Run<?, ?>> {
    private final CauseResolver causeResolver;
    private final TelegramNotificationService notificationService;

    public BuildCompletionListener() {
        this(new CauseResolver(), TelegramNotificationService.get());
    }

    BuildCompletionListener(CauseResolver causeResolver, TelegramNotificationService notificationService) {
        this.causeResolver = causeResolver;
        this.notificationService = notificationService;
    }

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        GlobalTelegramBuildNotifierConfiguration config = GlobalTelegramBuildNotifierConfiguration.get();
        String jobFullName = run.getParent().getFullName();
        if (!config.shouldNotifyStarted(jobFullName) || !markIfNew(run, BuildEvent.STARTED)) {
            return;
        }
        notificationService.enqueue(BuildNotification.fromRun(run, BuildEvent.STARTED, null, config, causeResolver));
    }

    @Override
    public void onCompleted(Run<?, ?> run, TaskListener listener) {
        GlobalTelegramBuildNotifierConfiguration config = GlobalTelegramBuildNotifierConfiguration.get();
        Result result = run.getResult();
        String jobFullName = run.getParent().getFullName();
        if (!config.shouldNotifyCompleted(jobFullName, result) || !markIfNew(run, BuildEvent.COMPLETED)) {
            return;
        }
        notificationService.enqueue(BuildNotification.fromRun(run, BuildEvent.COMPLETED, result, config, causeResolver));
    }

    @Override
    public void onFinalized(Run<?, ?> run) {
        // Jenkins calls onFinalized after build records are finalized. Terminal Telegram messages are sent only
        // from onCompleted so the same build does not receive a duplicate completion notification.
    }

    private boolean markIfNew(Run<?, ?> run, BuildEvent event) {
        BuildNotificationMarkerAction marker = run.getAction(BuildNotificationMarkerAction.class);
        if (marker == null) {
            marker = new BuildNotificationMarkerAction();
            run.addAction(marker);
        }
        return marker.markIfNew(event);
    }
}
