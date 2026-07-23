package io.jenkins.plugins.globaltelegrambuildnotifier;

import hudson.model.Cause;
import hudson.model.Run;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import java.util.List;

class CauseResolver {
    String resolve(Run<?, ?> run) {
        List<Cause> causes = run.getCauses();
        if (causes == null || causes.isEmpty()) {
            return "Unknown";
        }
        return resolve(causes.get(0));
    }

    String resolve(Cause cause) {
        if (cause == null) {
            return "Unknown";
        }
        if (cause instanceof Cause.UserIdCause) {
            return resolveUser((Cause.UserIdCause) cause);
        }
        if (cause instanceof SCMTrigger.SCMTriggerCause) {
            return "SCM Trigger";
        }
        if (cause instanceof TimerTrigger.TimerTriggerCause) {
            return "Timer Trigger";
        }
        if (cause instanceof Cause.UpstreamCause) {
            Cause.UpstreamCause upstream = (Cause.UpstreamCause) cause;
            return "Upstream: " + upstream.getUpstreamProject() + " #" + upstream.getUpstreamBuild();
        }
        if (cause instanceof Cause.RemoteCause) {
            Cause.RemoteCause remote = (Cause.RemoteCause) cause;
            return "Remote: " + remote.getAddr();
        }
        String description = Texts.trimToNull(cause.getShortDescription());
        return description == null ? "Unknown" : "Other: " + description;
    }

    private String resolveUser(Cause.UserIdCause cause) {
        String userName = Texts.trimToNull(cause.getUserName());
        if (userName != null) {
            return "Manual: " + userName;
        }
        String userId = Texts.trimToNull(cause.getUserId());
        return userId == null ? "Manual: unknown user" : "Manual: " + userId;
    }
}
