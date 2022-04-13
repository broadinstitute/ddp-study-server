package org.broadinstitute.ddp.db.dto.dsm;

import lombok.Data;

/**
 * The POJO for DSM kit requests. Implemented to match JSON API requirements for
 * DSM integration.
 */
@Data
public class DsmKitRequest {
    /**
     * The database id for the related database row.
     */
    private transient long id;

    /**
     * The identifier for the participant, which will be a legacy datstat altpid if present. Otherwise, it will be the user guid.
     */
    private String participantId;

    /**
     * The identifier for the kit request used by DSM as a handle.
     * Check elsewhere for what we set this to.
     */
    private String kitRequestId;

    /**
     * The name of the type of kit being requested.
     */
    private String kitType;

    /**
     * Does the kit need manual approval?
     */
    private boolean needsApproval;
}
