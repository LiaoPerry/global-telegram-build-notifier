package io.jenkins.plugins.globaltelegrambuildnotifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PipelineBuildTest {
    @Test
    void pipelineSuccessIsSupported(JenkinsRule jenkins) throws Exception {
        WorkflowRun run = runPipeline(jenkins, "pipeline-success", "echo 'ok'");
        assertEquals(Result.SUCCESS, run.getResult());
    }

    @Test
    void pipelineFailureIsSupported(JenkinsRule jenkins) throws Exception {
        WorkflowRun run = runPipeline(jenkins, "pipeline-failure", "error 'boom'");
        assertEquals(Result.FAILURE, run.getResult());
    }

    @Test
    void pipelineUnstableIsSupported(JenkinsRule jenkins) throws Exception {
        WorkflowRun run = runPipeline(jenkins, "pipeline-unstable", "currentBuild.result = 'UNSTABLE'; echo 'warn'");
        assertEquals(Result.UNSTABLE, run.getResult());
    }

    @Test
    void pipelineAbortedIsSupported(JenkinsRule jenkins) throws Exception {
        WorkflowRun run = runPipeline(jenkins, "pipeline-aborted", "currentBuild.result = 'ABORTED'; echo 'stop'");
        assertEquals(Result.ABORTED, run.getResult());
    }

    private WorkflowRun runPipeline(JenkinsRule jenkins, String name, String script) throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, name);
        job.setDefinition(new CpsFlowDefinition(script, true));
        return job.scheduleBuild2(0).get();
    }
}
