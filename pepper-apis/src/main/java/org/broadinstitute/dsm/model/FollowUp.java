package org.broadinstitute.dsm.model;

import lombok.Data;

@Data
public class FollowUp {
    private String fReceived;
    private String fRequest1;
    private String fRequest2;
    private String fRequest3;

    public FollowUp(String fRequest1, String fRequest2, String fRequest3, String fReceived) {
        this.fReceived = fReceived;
        this.fRequest1 = fRequest1;
        this.fRequest2 = fRequest2;
        this.fRequest3 = fRequest3;

    }
}
