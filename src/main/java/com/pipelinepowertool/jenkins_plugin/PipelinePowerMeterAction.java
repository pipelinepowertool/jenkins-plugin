package com.pipelinepowertool.jenkins_plugin;

import hudson.model.Action;

public class PipelinePowerMeterAction implements Action {

    private final String offsetDateTime;

    private final String tempDir;

    public PipelinePowerMeterAction(String offsetDateTime, String tempDir) {
        this.offsetDateTime = offsetDateTime;
        this.tempDir = tempDir;
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
