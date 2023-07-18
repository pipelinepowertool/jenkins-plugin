package com.pipelinepowertool.jenkins_plugin;

import com.pipelinepowertool.common.pipelineplugin.utils.Constants;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import java.time.OffsetDateTime;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.lang.NonNull;

public class PipelinePowerMeterBuilder extends Builder implements SimpleBuildStep {

    private static final String OSRELEASE_COMMAND = "cat /etc/os-release";

    @DataBoundConstructor
    public PipelinePowerMeterBuilder() {}

    @Override
    public void perform(
            @NonNull Run<?, ?> run,
            @NonNull FilePath workspace,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws IOException, InterruptedException {

        VirtualChannel channel = launcher.getChannel();
        if (channel == null) {
            listener.getLogger().println("No channel to agent found");
            return;
        }

        OffsetDateTime start = OffsetDateTime.now();
        FilePath tempDir = workspace.createTempDir(Constants.ENERGY_READER_FILENAME, "tmp");
        Long pid = channel.call(new EnergyMeterStarterCallable(tempDir));
        run.addAction(new PipelinePowerMeterAction(start.toString(), tempDir.getRemote(), pid));
    }

    @Symbol("pipelinePowerToolInitiator")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Pipeline Power Tool Initiator";
        }
    }
}
