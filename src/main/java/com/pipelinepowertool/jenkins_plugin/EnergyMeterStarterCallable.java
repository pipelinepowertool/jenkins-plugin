package com.pipelinepowertool.jenkins_plugin;

import com.pipelinepowertool.common.pipelineplugin.utils.Constants;
import hudson.FilePath;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import jenkins.security.MasterToSlaveCallable;

public class EnergyMeterStarterCallable extends MasterToSlaveCallable<Void, IOException> {

    private final FilePath path;
    private final String url;

    public EnergyMeterStarterCallable(FilePath path, String url) {
        this.path = path;
        this.url = url;
    }

    @Override
    public Void call() throws IOException {
        try (ReadableByteChannel readableByteChannel =
                        Channels.newChannel(URI.create(url).toURL().openStream());
                FileOutputStream fileOutputStream =
                        new FileOutputStream(path.getRemote() + "/" + Constants.ENERGY_READER_FILENAME);
                FileChannel fileChannel = fileOutputStream.getChannel()) {
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        }
        File file = new File(path.getRemote(), Constants.ENERGY_READER_FILENAME);
        file.setExecutable(true);
        return null;
    }
}
