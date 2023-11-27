package org.broadinstitute.dsm.jobs;

import java.util.Map;

import lombok.Data;

@Data
public class PubsubMessage {
    String data;
    Map<String, String> attributes;
    String messageId;
    String publishTime;
}
