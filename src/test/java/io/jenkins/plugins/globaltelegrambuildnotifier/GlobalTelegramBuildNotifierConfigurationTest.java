package io.jenkins.plugins.globaltelegrambuildnotifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Result;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class GlobalTelegramBuildNotifierConfigurationTest {
    @Test
    void disabledPluginDoesNotNotify(JenkinsRule jenkins) {
        GlobalTelegramBuildNotifierConfiguration config = GlobalTelegramBuildNotifierConfiguration.get();
        config.setEnabled(false);
        config.setNotifyOnFailure(true);
        assertFalse(config.shouldNotifyCompleted("folder/job", Result.FAILURE));
    }

    @Test
    void excludeRegexWinsOverIncludeRegex(JenkinsRule jenkins) {
        GlobalTelegramBuildNotifierConfiguration config = GlobalTelegramBuildNotifierConfiguration.get();
        config.setEnabled(true);
        config.setNotifyOnFailure(true);
        config.setIncludeJobRegex("^team/.*");
        config.setExcludeJobRegex("^team/noisy/.*");
        assertTrue(config.shouldNotifyCompleted("team/service", Result.FAILURE));
        assertFalse(config.shouldNotifyCompleted("team/noisy/service", Result.FAILURE));
        assertFalse(config.shouldNotifyCompleted("other/service", Result.FAILURE));
    }

    @Test
    void resultFiltersAreApplied(JenkinsRule jenkins) {
        GlobalTelegramBuildNotifierConfiguration config = GlobalTelegramBuildNotifierConfiguration.get();
        config.setEnabled(true);
        config.setNotifyOnSuccess(false);
        config.setNotifyOnFailure(true);
        config.setNotifyOnUnstable(true);
        config.setNotifyOnAborted(true);
        assertFalse(config.shouldNotifyCompleted("job", Result.SUCCESS));
        assertTrue(config.shouldNotifyCompleted("job", Result.FAILURE));
        assertTrue(config.shouldNotifyCompleted("job", Result.UNSTABLE));
        assertTrue(config.shouldNotifyCompleted("job", Result.ABORTED));
    }
}
