package com.pipelinepowertool.jenkins_plugin;

import hudson.model.Action;

public class PipelinePowerMeterAction implements Action {

    private final String offsetDateTime;

    private final String tempDir;
    private final Long pid;

    public PipelinePowerMeterAction(String offsetDateTime, String tempDir, Long pid) {
        this.offsetDateTime = offsetDateTime;
        this.tempDir = tempDir;
        this.pid = pid;
    }

    public Long getPid() {
        return pid;
    }

    public String getOffsetDateTime() {
        return offsetDateTime;
    }

    public String getTempDir() {
        return tempDir;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
