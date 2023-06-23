package org.broadinstitute.dsm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SomaticResultTriggerActivityPayload {
    private String participantId;
    private long triggerId;
    private String bucketName;
    private String resultsFilePath;
}
