package io.jenkins.plugins.globaltelegrambuildnotifier;

import hudson.model.InvisibleAction;

public class BuildNotificationMarkerAction extends InvisibleAction {
    private boolean startedEnqueued;
    private boolean completedEnqueued;

    synchronized boolean markIfNew(BuildEvent event) {
        if (event == BuildEvent.STARTED) {
            if (startedEnqueued) {
                return false;
            }
            startedEnqueued = true;
            return true;
        }
        if (completedEnqueued) {
            return false;
        }
        completedEnqueued = true;
        return true;
    }
}
