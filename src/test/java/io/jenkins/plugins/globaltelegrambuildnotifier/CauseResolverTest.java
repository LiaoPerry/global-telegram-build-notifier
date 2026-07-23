package io.jenkins.plugins.globaltelegrambuildnotifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Cause;
import hudson.triggers.TimerTrigger;
import java.lang.reflect.Constructor;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CauseResolverTest {
    private final CauseResolver resolver = new CauseResolver();

    @Test
    void resolvesTimerCause() {
        assertEquals("Timer Trigger", resolver.resolve(new TimerTrigger.TimerTriggerCause()));
    }

    @Test
    void resolvesUpstreamCauseWithoutRecursing() throws Exception {
        Constructor<Cause.UpstreamCause> constructor = Cause.UpstreamCause.class.getDeclaredConstructor(
                String.class, int.class, String.class, java.util.List.class);
        constructor.setAccessible(true);
        assertEquals(
                "Upstream: folder/job #123",
                resolver.resolve(constructor.newInstance("folder/job", 123, "folder/job #123", Collections.emptyList())));
    }

    @Test
    void resolvesRemoteCause() {
        assertEquals("Remote: 10.0.0.1", resolver.resolve(new Cause.RemoteCause("10.0.0.1", "note")));
    }

    @Test
    void resolvesOtherCauseSafely() {
        assertTrue(resolver.resolve(new Cause() {
            @Override
            public String getShortDescription() {
                return "Custom trigger";
            }
        }).contains("Custom trigger"));
    }
}
