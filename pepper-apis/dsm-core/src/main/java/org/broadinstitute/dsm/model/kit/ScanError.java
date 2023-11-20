package org.broadinstitute.dsm.model.kit;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class ScanError {
    private String kit;
    private String error;
    private String shortId;

    /**
     * Use this to construct a scan result when there is
     * no error.
     */
    public ScanError(String kit) {
        this.kit = kit;
    }

    public ScanError(String kit, String error) {
        this(kit);
        this.error = error;
    }

    public ScanError(String kit, String error, String shortId) {
        this(kit, error);
        this.shortId = shortId;
    }

    public boolean isScanErrorOnlyBspParticipantId(String bspCollaboratorParticipantId) {
        return (!hasError() && StringUtils.isBlank(getKit())) || (StringUtils.isBlank(getError())
                && StringUtils.isNotBlank(getShortId()) && getShortId().equalsIgnoreCase(bspCollaboratorParticipantId));
    }

    public boolean hasError() {
        return StringUtils.isNotBlank(error);
    }

}
