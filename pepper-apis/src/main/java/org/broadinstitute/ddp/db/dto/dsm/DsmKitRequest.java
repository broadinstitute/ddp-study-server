package org.broadinstitute.ddp.db.dto.dsm;

/**
 * The POJO for DSM kit requests. Implemented to match JSON API requirements for
 * DSM integration.
 */
public class DsmKitRequest {
    //marked transient to exclude from GSON serialization
    private transient long id;
    private String participantId;
    private String kitRequestId;
    private String kitType;
    private boolean needsApproval;

    /**
     * The database id for the related database row.
     *
     * @return the id
     */
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    /**
     * The identifier for the participant, which will be a legacy datstat altpid if present.  Otherwise, it will be the user guid.
     */
    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantUserId) {
        this.participantId = participantUserId;
    }

    /**
     * The identifier for the kit request used by DSM as a handle.
     * Check elsewhere for what we set this to.
     *
     * @return the kit request id
     */
    public String getKitRequestId() {
        return kitRequestId;
    }

    public void setKitRequestId(String kitRequestId) {
        this.kitRequestId = kitRequestId;
    }

    /**
     * The name of the type of kit being requested.
     *
     * @return the name of the kit type (e.g., SALIVA, BLOOD, etc.)
     */
    public String getKitType() {
        return kitType;
    }

    public void setKitType(String kitType) {
        this.kitType = kitType;
    }

    /**
     * Does the kit need manual approval?
     *
     * @return true if need approval, false otherwise
     */
    public boolean getNeedsApproval() {
        // Note: This class is used in bean mapper, so use get/set naming convention.
        return needsApproval;
    }

    public void setNeedsApproval(boolean needsApproval) {
        this.needsApproval = needsApproval;
    }
}
