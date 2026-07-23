package io.jenkins.plugins.globaltelegrambuildnotifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class BuildCompletionListenerTest {
    @Test
    void startedAndCompletedAreEnqueuedOnceAndFinalizedDoesNotNotify(JenkinsRule jenkins) throws Exception {
        GlobalTelegramBuildNotifierConfiguration config = GlobalTelegramBuildNotifierConfiguration.get();
        config.setEnabled(true);
        config.setNotifyOnStarted(true);
        config.setNotifyOnSuccess(true);

        RecordingService service = new RecordingService();
        BuildCompletionListener listener = new BuildCompletionListener(new CauseResolver(), service);
        FreeStyleProject project = jenkins.createFreeStyleProject("demo");
        Run<?, ?> build = jenkins.buildAndAssertSuccess(project);
        removeMarkerAddedByRegisteredListener(build);

        listener.onStarted(build, TaskListener.NULL);
        listener.onStarted(build, TaskListener.NULL);
        listener.onCompleted(build, TaskListener.NULL);
        listener.onCompleted(build, TaskListener.NULL);
        listener.onFinalized(build);

        assertEquals(2, service.notifications.size());
        assertEquals(BuildEvent.STARTED, service.notifications.get(0).getEvent());
        assertEquals(BuildEvent.COMPLETED, service.notifications.get(1).getEvent());
    }

    @Test
    void disabledPluginSkipsEnqueue(JenkinsRule jenkins) throws Exception {
        GlobalTelegramBuildNotifierConfiguration config = GlobalTelegramBuildNotifierConfiguration.get();
        config.setEnabled(false);
        config.setNotifyOnStarted(true);
        config.setNotifyOnSuccess(true);

        RecordingService service = new RecordingService();
        BuildCompletionListener listener = new BuildCompletionListener(new CauseResolver(), service);
        FreeStyleProject project = jenkins.createFreeStyleProject("disabled");
        Run<?, ?> build = jenkins.buildAndAssertSuccess(project);
        removeMarkerAddedByRegisteredListener(build);

        listener.onStarted(build, TaskListener.NULL);
        listener.onCompleted(build, TaskListener.NULL);

        assertEquals(0, service.notifications.size());
    }

    private void removeMarkerAddedByRegisteredListener(Run<?, ?> build) throws Exception {
        BuildNotificationMarkerAction marker = build.getAction(BuildNotificationMarkerAction.class);
        if (marker != null) {
            build.removeAction(marker);
        }
    }

    private static final class RecordingService extends TelegramNotificationService {
        private final List<BuildNotification> notifications = new ArrayList<>();

        @Override
        void enqueue(BuildNotification notification) {
            notifications.add(notification);
        }
    }
}
