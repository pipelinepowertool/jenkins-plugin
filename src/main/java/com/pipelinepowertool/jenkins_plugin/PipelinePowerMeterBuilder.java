package com.pipelinepowertool.jenkins_plugin;

import com.pipelinepowertool.common.pipelineplugin.utils.Constants;
import hudson.*;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        String url = getEnergyReaderUrl(launcher);
        channel.call(new EnergyMeterStarterCallable(tempDir, url));
        launcher.launch()
                .cmdAsSingleString("./" + Constants.ENERGY_READER_FILENAME + " &")
                .pwd(tempDir)
                .start();
        run.addAction(new PipelinePowerMeterAction(start.toString(), tempDir.getRemote()));
    }

    private String getEnergyReaderUrl(Launcher launcher) throws IOException, InterruptedException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Proc starter = launcher.launch()
                    .cmdAsSingleString(OSRELEASE_COMMAND)
                    .stdout(baos)
                    .start();
            int exitCode = starter.join();
            if (exitCode != 0) {
                throw new IOException(
                        "Fail to execute " + OSRELEASE_COMMAND + " because: " + baos.toString(StandardCharsets.UTF_8));
            }
            if (baos.toString(StandardCharsets.UTF_8).trim().toUpperCase().contains("ALPINE")) {
                return Constants.ENERGY_READER_URL_JENKINS_ALPINE;
            }
            return Constants.ENERGY_READER_URL_DEFAULT;
        }
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
