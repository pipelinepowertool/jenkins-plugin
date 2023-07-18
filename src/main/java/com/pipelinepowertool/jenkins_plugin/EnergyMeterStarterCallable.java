package com.pipelinepowertool.jenkins_plugin;

import com.pipelinepowertool.common.pipelineplugin.utils.Constants;
import hudson.FilePath;
import java.io.*;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import jenkins.security.MasterToSlaveCallable;

public class EnergyMeterStarterCallable extends MasterToSlaveCallable<Long, IOException> {

    private final FilePath path;

    public EnergyMeterStarterCallable(FilePath path) {
        this.path = path;
    }

    @Override
    public Long call() throws IOException {
        ProcessBuilder osReleaseProcessBuilder = new ProcessBuilder();
        osReleaseProcessBuilder.command("cat", "/etc/os-release");
        Process osReleaseProcess = osReleaseProcessBuilder.start();
        String url = Constants.ENERGY_READER_URL_DEFAULT;
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(osReleaseProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.toUpperCase().contains("ALPINE")) {
                    url = Constants.ENERGY_READER_URL_JENKINS_ALPINE;
                    break;
                }
            }
        }
        try (ReadableByteChannel readableByteChannel =
                        Channels.newChannel(URI.create(url).toURL().openStream());
                FileOutputStream fileOutputStream =
                        new FileOutputStream(path.getRemote() + "/" + Constants.ENERGY_READER_FILENAME);
                FileChannel fileChannel = fileOutputStream.getChannel()) {
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        }
        File file = new File(path.getRemote(), Constants.ENERGY_READER_FILENAME);
        file.setExecutable(true);
        ProcessBuilder energyMeterProcessBuilder = new ProcessBuilder();
        energyMeterProcessBuilder.command(file.getAbsolutePath(), "&");
        energyMeterProcessBuilder.directory(file.getParentFile());
        return energyMeterProcessBuilder.start().pid();
    }
}
