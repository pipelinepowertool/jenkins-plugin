package com.pipelinepowertool.jenkins_plugin;

import com.pipelinepowertool.common.pipelineplugin.utils.Constants;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class PipelinePowerMeterBuilder extends Builder implements SimpleBuildStep {

    private static final String OSRELEASE_COMMAND = "cat /etc/os-release";

    @DataBoundConstructor
    public PipelinePowerMeterBuilder() {}

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        OffsetDateTime start = OffsetDateTime.now();
        FilePath tempDir = workspace.createTempDir(Constants.ENERGY_READER_FILENAME, "tmp");

        run.addAction(new PipelinePowerMeterAction(start.toString(), tempDir.getRemote()));

        String url = Constants.ENERGY_READER_URL_DEFAULT;
        try {
            CommandLauncher commandLauncher = new CommandLauncher(launcher, listener, run, tempDir);
            url = getEnergyReaderUrl(launcher, url);
            joinCommand("curl -o " + Constants.ENERGY_READER_FILENAME + " " + url, commandLauncher);
            joinCommand("chmod +x " + Constants.ENERGY_READER_FILENAME, commandLauncher);
            startCommand("./" + Constants.ENERGY_READER_FILENAME + " &", commandLauncher);
        } catch (InterruptedException e) {
            listener.getLogger().println("Something went wrong executing commands");
            Thread.currentThread().interrupt();
        }
    }

    private String getEnergyReaderUrl(Launcher launcher, String url) throws IOException, InterruptedException {
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
                url = Constants.ENERGY_READER_URL_JENKINS_ALPINE;
            }
        }
        return url;
    }

    private void startCommand(String command, CommandLauncher commandLauncher) throws IOException {
        commandLauncher
                .launcher
                .launch()
                .cmdAsSingleString(command)
                .pwd(commandLauncher.workspace)
                .start();
    }

    private void joinCommand(String command, CommandLauncher commandLauncher) throws IOException, InterruptedException {
        commandLauncher
                .launcher
                .launch()
                .cmdAsSingleString(command)
                .stdout(commandLauncher.listener)
                .pwd(commandLauncher.workspace)
                .envs(commandLauncher.run.getEnvironment(commandLauncher.listener))
                .join();
    }

    private static final class CommandLauncher {

        private final Launcher launcher;
        private final TaskListener listener;
        private final Run<?, ?> run;
        private final FilePath workspace;

        public CommandLauncher(Launcher launcher, TaskListener listener, Run<?, ?> run, FilePath workspace) {
            this.launcher = launcher;
            this.listener = listener;
            this.run = run;
            this.workspace = workspace;
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
