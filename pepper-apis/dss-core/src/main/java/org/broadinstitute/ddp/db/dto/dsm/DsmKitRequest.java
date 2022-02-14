package org.broadinstitute.ddp.db.dto.dsm;

import lombok.Data;

/**
 * The POJO for DSM kit requests. Implemented to match JSON API requirements for
 * DSM integration.
 */
@Data
public class DsmKitRequest {
    //marked transient to exclude from GSON serialization
    private transient long id;
    private String participantId;
    private String kitRequestId;
    private String kitType;
    private boolean needsApproval;
}
