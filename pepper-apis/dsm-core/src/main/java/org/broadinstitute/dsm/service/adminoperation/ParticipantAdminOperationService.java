package org.broadinstitute.dsm.service.adminoperation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.admin.AdminOperation;

public abstract class ParticipantAdminOperationService implements AdminOperation {

    protected final ParticipantDataDao participantDataDao = new ParticipantDataDao();

    protected boolean isRequiredDryRun(Map<String, String> attributes) {
        String dryRun = attributes.get("dryRun");
        if (StringUtils.isBlank(dryRun)) {
            throw new DSMBadRequestException("Missing required attribute 'dryRun'");
        }

        if (!dryRun.equalsIgnoreCase("true") && !dryRun.equalsIgnoreCase("false")) {
            throw new DSMBadRequestException("Invalid dryRun parameter ('true' or 'false' accepted): " + dryRun);
        }
        return Boolean.parseBoolean(dryRun);
    }

    protected boolean isBooleanProperty(String property, Map<String, String> attributes) {
        if (attributes.containsKey(property)) {
            String prop = attributes.get(property);
            if (!StringUtils.isBlank(prop)) {
                throw new DSMBadRequestException("Invalid '%s' attribute: not expecting a value".formatted(property));
            }
            return true;
        }
        return false;
    }

    protected DDPInstance getDDPInstance(String realm, List<String> validRealms) {
        if (!validRealms.contains(realm.toLowerCase())) {
            throw new DsmInternalError("Invalid realm for ParticipantDataFixupService: " + realm);
        }

        DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
        if (ddpInstance == null) {
            throw new DsmInternalError("Invalid realm: " + realm);
        }
        if (StringUtils.isEmpty(ddpInstance.getParticipantIndexES())) {
            throw new DsmInternalError("No ES participant index for realm " + realm);
        }
        return ddpInstance;
    }

    protected Map<String, List<ParticipantData>> getParticipantData(String payload) {
        ParticipantListRequest req = ParticipantListRequest.fromJson(payload);
        List<String> participants = req.getParticipants();

        Map<String, List<ParticipantData>> participantDataByPtpId = new HashMap<>();
        for (String participantId: participants) {
            List<ParticipantData> ptpData = participantDataDao.getParticipantData(participantId);
            if (ptpData.isEmpty()) {
                throw new DSMBadRequestException("Invalid participant ID: " + participantId);
            }
            participantDataByPtpId.put(participantId, ptpData);
        }
        return participantDataByPtpId;
    }
}
