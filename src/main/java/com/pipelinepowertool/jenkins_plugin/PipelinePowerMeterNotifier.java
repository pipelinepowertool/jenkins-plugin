package com.pipelinepowertool.jenkins_plugin;

import co.elastic.clients.elasticsearch._types.HealthStatus;
import com.pipelinepowertool.common.core.database.EnergyReading;
import com.pipelinepowertool.common.core.database.EnergyReadingRecord;
import com.pipelinepowertool.common.core.database.elasticsearch.ElasticSearchService;
import com.pipelinepowertool.common.core.pipeline.jenkins.JenkinsMetadata;
import com.pipelinepowertool.common.pipelineplugin.csv.CsvService;
import com.pipelinepowertool.common.pipelineplugin.csv.CsvServiceImpl;
import com.pipelinepowertool.common.pipelineplugin.utils.Constants;
import com.pipelinepowertool.jenkins_plugin.utils.MappingUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import jenkins.tasks.SimpleBuildStep;
import org.apache.http.HttpHost;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class PipelinePowerMeterNotifier extends Notifier implements SimpleBuildStep {

    private static final CsvService csvService = new CsvServiceImpl();
    private static final String BUILD_NR = "BUILD_NUMBER";
    private static final String JOB_NAME = "JOB_NAME";
    private static final String BRANCH_NAME = "BRANCH_NAME";
    private static final String JSON_FILENAME = "energy-reading.json";
    private static final String HTTP_SCHEME = "HTTPS";

    private static final String CERT = "certs/http_ca.crt";

    private final String userName;
    private final String password;
    private final String hostName;
    private final short port;

    @DataBoundConstructor
    public PipelinePowerMeterNotifier(String userName, String password, String hostName, short port) {
        this.userName = userName;
        this.password = password;
        this.hostName = hostName;
        this.port = port;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getHostName() {
        return hostName;
    }

    public short getPort() {
        return port;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public void perform(
            @NonNull Run<?, ?> build,
            @NonNull FilePath workspace,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws InterruptedException, IOException {
        ElasticSearchService elasticSearchService = createDatabaseConnection(listener);
        PipelinePowerMeterAction action = build.getAction(PipelinePowerMeterAction.class);

        FilePath tempDir = new FilePath(launcher.getChannel(), action.getTempDir());

        try {
            File csvFile = getCsvFile(tempDir);
            EnergyReadingRecord energyReadingRecord = getEnergyReadingRecord(build, listener, action, csvFile);
            String json = MappingUtils.objectMapper.writeValueAsString(energyReadingRecord);
            FilePath tempJson = tempDir.createTextTempFile(JSON_FILENAME, "tmp", json);

            Map<String, String> archiveArtifacts = Map.of(JSON_FILENAME, tempJson.getName());
            build.getArtifactManager().archive(tempDir, launcher, (BuildListener) listener, archiveArtifacts);

            listener.getLogger().println("Energy reading artifact created");

            CompletableFuture<Void> elasticIndexInFuture = elasticSearchService
                    .healthCheck()
                    .thenAccept(response -> {
                        String value = response.getValue();
                        if (!value.equalsIgnoreCase(HealthStatus.Green.jsonValue())
                                && !value.equalsIgnoreCase(HealthStatus.Yellow.jsonValue())) {
                            listener.getLogger().println("Database is not healthy");
                            throw new RuntimeException();
                        }
                    })
                    .whenComplete((voidResponse, throwable) -> {
                        if (throwable != null) {
                            return;
                        }
                        elasticSearchService.send(energyReadingRecord);
                        listener.getLogger().println("Indexing energy record");
                    });
            elasticIndexInFuture.get();
            listener.getLogger().println("Energy record indexed in ElasticSearch");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            listener.getLogger().println("No energy readings file found");
        } catch (ExecutionException e) {
            listener.getLogger().println("Something went wrong indexing the data");
            throw new RuntimeException(e);
        }
    }

    private EnergyReadingRecord getEnergyReadingRecord(
            Run<?, ?> build, TaskListener listener, PipelinePowerMeterAction action, File csvFile)
            throws IOException, InterruptedException {

        EnergyReading aggregate = csvService.aggregateReadings(csvFile);

        EnvVars environment = build.getEnvironment(listener);
        String buildNumber = environment.get(BUILD_NR);
        String pipeline = environment.get(JOB_NAME);
        String branch = environment.get(BRANCH_NAME);

        OffsetDateTime pipelineStart = OffsetDateTime.parse(action.getOffsetDateTime());
        OffsetDateTime pipelineEnd = OffsetDateTime.now();

        JenkinsMetadata metadata =
                new JenkinsMetadata(pipeline, branch, Long.valueOf(buildNumber), pipelineStart, pipelineEnd);
        return new EnergyReadingRecord(aggregate, metadata);
    }

    private File getCsvFile(FilePath tempDir) throws IOException, InterruptedException {
        FilePath filePath = Arrays.stream(tempDir.list(Constants.ENERGY_READINGS_CSV))
                .findFirst()
                .orElseThrow(FileNotFoundException::new);

        return new File(filePath.getRemote());
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return ((DescriptorImpl) super.getDescriptor());
    }

    private ElasticSearchService createDatabaseConnection(TaskListener listener) {
        HttpHost httpHost = new HttpHost(hostName, port, HTTP_SCHEME);
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream(CERT)) {
            if (resource == null) {
                listener.getLogger().println("Cannot find certificates!");
                throw new IOException();
            }
            byte[] certAsBytes = resource.readAllBytes();
            return new ElasticSearchService(userName, password, certAsBytes, httpHost);
        } catch (IOException e) {
            listener.getLogger().println("Something went wrong while connecting to the database");
            throw new RuntimeException(e);
        }
    }

    @Symbol("pipelinePowerToolElasticPublisher")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public FormValidation doCheckParameters(
                @QueryParameter String userName,
                @QueryParameter String password,
                @QueryParameter String hostName,
                @QueryParameter short port) {
            if (userName.length() == 0) {
                return noValueError("username");
            }
            if (hostName.length() == 0) {
                return noValueError("hostname");
            }
            if (password.length() == 0) {
                return noValueError("password");
            }
            if (port == 0) {
                return noValueError("port");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Pipeline Power Tool Elastic Publisher";
        }

        private FormValidation noValueError(String parameter) {
            return FormValidation.error("No value for" + parameter);
        }
    }
}
