package org.broadinstitute.dsm.jobs;

import lombok.Data;

import java.util.Map;
@Data
public class PubsubMessage {
        String data;
        Map<String, String> attributes;
        String messageId;
        String publishTime;
    }
