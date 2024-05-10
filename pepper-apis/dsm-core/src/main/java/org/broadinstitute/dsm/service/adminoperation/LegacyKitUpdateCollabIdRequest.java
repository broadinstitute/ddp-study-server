package org.broadinstitute.dsm.service.adminoperation;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;

@Getter
@AllArgsConstructor
@NoArgsConstructor
/**
 * Request to resample or rename a kit for a participant with a legacy short ID
 * The request is for resampling a kit from Pepper Guid and Hruid to their old legacy ids
 */
public class LegacyKitUpdateCollabIdRequest {
    String currentCollaboratorSampleId;
    String newCollaboratorSampleId;
    String newCollaboratorParticipantId;
    String shortId;
    String legacyShortId;

    /**
     * Verify that the request is valid,
     * that all the fields are present,
     * that the participant exists in the ES with the given short ID,
     * that the given legacy short ID matches the one on file,
     * that the kit request exists for the given collaborator sample ID to resmaple,
     * and also that the new collaborator sample ID does not already exist
     *
     * @param ddpInstanceDto instance from the request
     * @param kitRequestDao DAO for kit requests
     */
    public void verify(DDPInstanceDto ddpInstanceDto, KitRequestDao kitRequestDao) {
        if (ddpInstanceDto == null) {
            throw new DsmInternalError("DDP instance not found");
        }
        checkNotEmptyRequestFields();
        Map<String, Object> profile = ElasticSearchService.getParticipantProfileByShortID(ddpInstanceDto.getInstanceName(),
                ddpInstanceDto.getEsParticipantIndex(), shortId);
        // Check if the participant exists in ES and if it has a legacy short ID
        String legacyShortIdOnFile = profile.getOrDefault("legacyShortId", "").toString();
        if (!legacyShortId.equals(legacyShortIdOnFile)) {
            throw new DSMBadRequestException(("Legacy short ID %s does not match legacy short ID on file %s for participant short ID %s, "
                    + " will not update kit %s").formatted(legacyShortId, legacyShortIdOnFile, shortId, currentCollaboratorSampleId));
        }
        if (!existsKitRequestWithCollaboratorSampleId(currentCollaboratorSampleId, (String) profile.get("guid"), kitRequestDao)) {
            throw new DSMBadRequestException("Kit request not found for collaborator sample ID %s".formatted(currentCollaboratorSampleId));
        }
        if (existsKitRequestWithCollaboratorSampleId(newCollaboratorSampleId, (String) profile.get("guid"), kitRequestDao)) {
            throw new DSMBadRequestException("Kit request with the new collaboratorSampleId %s already exists!"
                    .formatted(newCollaboratorSampleId));
        }

    }

    /**
     * Check that all required fields are present in the request
     */
    private void checkNotEmptyRequestFields() {
        if (StringUtils.isBlank(currentCollaboratorSampleId)) {
            throw new DSMBadRequestException("Missing required field: currentCollaboratorSampleId");
        }

        if (StringUtils.isBlank(newCollaboratorSampleId)) {
            throw new DSMBadRequestException("Missing required field: newCollaboratorSampleId");
        }

        if (StringUtils.isBlank(shortId)) {
            throw new DSMBadRequestException("Missing required field: shortId");
        }

        if (StringUtils.isBlank(newCollaboratorParticipantId)) {
            throw new DSMBadRequestException("Missing required field: newCollaboratorParticipantId");
        }

        if (StringUtils.isBlank(legacyShortId)) {
            throw new DSMBadRequestException("Missing required field: legacyShortId");
        }
    }

    /**
     * Collaborator sample id is unique in DSM, so we can use it to find a kit request, and to verify if we can use a
     * collaborator sample id to resample a kit. This method checks if a kit request with the given collaborator sample id
     * already exists in DSM, and if it belongs to the given participant.
     *
     * @param collaboratorSampleId collaborator sample id to check
     * @param ddpParticipantId participant id to check
     * @return true if a kit request with the given collaborator sample id exists in DSM and belongs to the given participant
     * */
    public boolean existsKitRequestWithCollaboratorSampleId(String collaboratorSampleId, String ddpParticipantId,
                                                            KitRequestDao kitRequestDao) {
        KitRequestShipping kitRequestShipping = kitRequestDao.getKitRequestForCollaboratorSampleId(collaboratorSampleId);
        if (kitRequestShipping == null) {
            return false;
        }
        return StringUtils.isNotBlank(ddpParticipantId) ? ddpParticipantId.equals(kitRequestShipping.getDdpParticipantId()) : true;
    }
}
