package com.pipelinepowertool.jenkins_plugin;

import java.io.IOException;
import java.util.Optional;
import jenkins.security.MasterToSlaveCallable;

public class EnergyMeterKillerCallable extends MasterToSlaveCallable<Void, IOException> {

    private final Long pid;

    public EnergyMeterKillerCallable(Long pid) {
        this.pid = pid;
    }

    @Override
    public Void call() throws IOException {
        Optional<ProcessHandle> optionalProcessHandle = ProcessHandle.of(pid);
        optionalProcessHandle.ifPresent(ProcessHandle::destroy);
        return null;
    }
}
