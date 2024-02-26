package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;

import lombok.Data;

/**
 * A log entry for part of an Elastic export operation.
 */
@Data
public class ExportLog {
    public enum Status {
        NO_FAILURES, FAILURES, ERROR, NO_PARTICIPANTS
    }

    private String entity;
    private Status status;
    private int participantCount;
    private int exportedCount;
    private List<String> successIds;
    private List<String> failureIds;
    private String message;

    public ExportLog(String entity) {
        this.entity = entity;
        this.participantCount = 0;
        this.exportedCount = 0;
    }

    public void setError(String message) {
        this.status = Status.ERROR;
        this.message = message;
    }
}
