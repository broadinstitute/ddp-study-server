package org.broadinstitute.dsm.model.patch;

import java.util.Optional;

import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.dao.settings.EventTypeDao;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.elastic.export.generate.ParticipantDataNameValue;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class ParticipantDataPatch extends BasePatch {

    private String participantDataId;

    public ParticipantDataPatch(Patch patch) {
        super(patch);
    }

    @Override
    public Object doPatch() {
        return patchNameValuePairs();
    }

    @Override
    protected Object patchNameValuePairs() {
        processMultipleNameValues();
        return resultMap;
    }

    @Override
    protected Object patchNameValuePair() {
        return null;
    }

    @Override
    Object handleSingleNameValue() {
        return null;
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        if (participantDataId == null) {
            participantDataId =
                    ParticipantData.createNewParticipantData(patch.getParentId(), ddpInstance.getDdpInstanceId(), patch.getFieldId(),
                            String.valueOf(nameValue.getValue()), patch.getUser());
            resultMap.put(ESObjectConstants.PARTICIPANT_DATA_ID, participantDataId);
        } else {
            Patch.patch(participantDataId, patch.getUser(), nameValue, dbElement);
        }
        ParticipantDataNameValue participantDataNameValue =
                new ParticipantDataNameValue(nameValue.getName(), nameValue.getValue(), ddpInstance.getDdpInstanceIdAsInt(),
                        patch.getDdpParticipantId(), patch.getFieldId());
        exportToESWithId(participantDataId, participantDataNameValue);
        if (patch.getActions() != null) {
            profile = ElasticSearchUtil.getParticipantProfileByGuidOrAltPid(ddpInstance.getParticipantIndexES(), patch.getParentId())
                    .orElseThrow(() -> new RuntimeException("Unable to find ES profile for participant: " + patch.getParentId()));
            for (Value action : patch.getActions()) {
                if (hasProfileAndESWorkflowType(profile, action)) {
                    writeESWorkflow(patch, nameValue, action, ddpInstance, profile.getGuid());
                } else if (EventTypeDao.EVENT.equals(action.getType())) {
                    triggerParticipantEvent(ddpInstance, patch, action);
                }
            }
        }
        return Optional.empty();
    }
}

