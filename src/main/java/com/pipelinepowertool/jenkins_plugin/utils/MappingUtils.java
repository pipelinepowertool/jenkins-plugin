package com.pipelinepowertool.jenkins_plugin.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class MappingUtils {

    public static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule((new JavaTimeModule()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
}
