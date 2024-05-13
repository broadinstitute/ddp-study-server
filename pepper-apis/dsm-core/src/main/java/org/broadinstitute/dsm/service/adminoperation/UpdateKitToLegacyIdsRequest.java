package org.broadinstitute.dsm.service.adminoperation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;

@Getter
@NoArgsConstructor
/**
 * Request to resample or rename a kit for a participant with a legacy short ID
 * The request is for resampling a kit from Pepper Guid and Hruid to their old legacy ids
 */
public class UpdateKitToLegacyIdsRequest {
    private String currentCollaboratorSampleId;
    private String newCollaboratorSampleId;
    private String newCollaboratorParticipantId;
    private String shortId;
    private String legacyShortId;

    /**
     * Creates a new request to update a kit for a participant with a legacy short ID
     * Will throw a DSMBadRequestException if any of the required fields are missing
     * */
    public UpdateKitToLegacyIdsRequest(String currentCollaboratorSampleId, String newCollaboratorSampleId,
                                       String newCollaboratorParticipantId, String shortId, String legacyShortId) {
        if (StringUtils.isBlank(currentCollaboratorSampleId)) {
            throw new DSMBadRequestException("Missing required field: currentCollaboratorSampleId");
        }
        this.currentCollaboratorSampleId = currentCollaboratorSampleId;
        if (StringUtils.isBlank(newCollaboratorSampleId)) {
            throw new DSMBadRequestException("Missing required field: newCollaboratorSampleId");
        }
        this.newCollaboratorSampleId = newCollaboratorSampleId;
        if (StringUtils.isBlank(newCollaboratorParticipantId)) {
            throw new DSMBadRequestException("Missing required field: newCollaboratorParticipantId");
        }
        this.newCollaboratorParticipantId = newCollaboratorParticipantId;
        if (StringUtils.isBlank(shortId)) {
            throw new DSMBadRequestException("Missing required field: shortId");
        }
        this.shortId = shortId;

        if (StringUtils.isBlank(legacyShortId)) {
            throw new DSMBadRequestException("Missing required field: legacyShortId");
        }
        this.legacyShortId = legacyShortId;
    }

    /**
     * Verify that the request is valid by checking if the DDP instance is found
     *
     * @param ddpInstanceDto instance from the request
     */
    public void verify(DDPInstanceDto ddpInstanceDto) {
        if (ddpInstanceDto == null) {
            throw new DsmInternalError("DDP instance not found");
        }

    }
}
