package org.broadinstitute.dsm.service.adminoperation;

import java.util.Map;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Getter
public class LegacyKitResampleRequest {
    String currentCollaboratorSampleId;
    String newCollaboratorSampleId;
    String newCollaboratorParticipantId;
    String shortId;
    String legacyShortId;

    public void verify(DDPInstance ddpInstance, KitRequestDao kitRequestDao) {
        if (ddpInstance == null) {
            throw new DsmInternalError("DDP instance not found");
        }
        if (StringUtils.isBlank(currentCollaboratorSampleId) || StringUtils.isBlank(newCollaboratorSampleId)
                || StringUtils.isBlank(shortId) || StringUtils.isBlank(newCollaboratorParticipantId)) {
            throw new DSMBadRequestException("Missing required fields in legacy kit resample request for currentCollaboratorSampleId %s "
                    + " newCollaboratorSampleId %s, shortId %s, newCollaboratorParticipantId %s".formatted(currentCollaboratorSampleId,
                    newCollaboratorSampleId, shortId, newCollaboratorParticipantId));
        }
        Map<String, Map<String, Object>> esParticipantData = ElasticSearchUtil.getSingleParticipantFromES(ddpInstance.getName(),
                ddpInstance.getParticipantIndexES(), shortId);
        if (esParticipantData.size() != 1) {
            throw new DSMBadRequestException("Invalid participant short ID " + shortId);
        }
        Map<String, Object> ptp = esParticipantData.values().stream().findFirst().orElseThrow();
        Map<String, Object> profile = (Map<String, Object>) ptp.get("profile");
        if (profile == null) {
            throw new DSMBadRequestException("No profile found for participant short ID " + shortId);
        }
        String legacyShortIdOnFile = profile.get("legacyShortId").toString();
        if (!legacyShortId.equals(legacyShortIdOnFile)) {
            throw new DSMBadRequestException("Legacy short ID %s does not match legacy short ID on file %s for participant short ID %s, "
                    + " will not resample kit %s".formatted(legacyShortId, legacyShortIdOnFile, shortId, currentCollaboratorSampleId));
        }
        if (!kitRequestDao.hasKitRequestWithCollaboratorSampleId(currentCollaboratorSampleId, (String) profile.get("guid"))) {
            throw new DSMBadRequestException("Kit request not found for collaborator sample ID %s".formatted(currentCollaboratorSampleId));
        }

    }
}
