package org.broadinstitute.dsm.model.patch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.service.onchistory.OncHistoryService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Patch class only for new added oncHistoryDetails otherwise ExistingRecordPatch is used
public class OncHistoryDetailPatch extends BasePatch {

    static final Logger logger = LoggerFactory.getLogger(OncHistoryDetailPatch.class);
    public static final String ONC_HISTORY_DETAIL_ID = "oncHistoryDetailId";

    static {
        NULL_KEY = new HashMap<>();
        NULL_KEY.put(NAME_VALUE, null);
    }

    private Integer mrID;
    private String oncHistoryDetailId;

    {
        nameValues.add(new NameValue("request", OncHistoryDetail.STATUS_REVIEW));
        resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
    }

    public OncHistoryDetailPatch(Patch patch) {
        super(patch);
    }

    @Override
    public Object doPatch() {
        return isNameValuePairs() ? patchNameValuePairs() : patchNameValuePair();
    }

    private void prepare() {
        // TODO this method needs to be rewritten, but for now at least verify the input at the correct points -DC
        String parentId = patch.getParentId();
        String ddpParticipantId = patch.getDdpParticipantId();
        if (StringUtils.isBlank(ddpParticipantId)) {
            throw new DSMBadRequestException("DDP participant ID not provided for patch");
        }
        String realm = patch.getRealm();
        if (StringUtils.isBlank(realm)) {
            throw new DSMBadRequestException("Realm not provided for patch");
        }

        // TODO: unclear if the parent ID should always be the participant ID (bad request if not), or if can
        // be empty or a different ID. Once that is established we can clean up this code. -DC
        if (StringUtils.isNotBlank(parentId)) {
            mrID = MedicalRecordUtil.isInstitutionTypeInDB(parentId);
        }
        if (mrID == null) {
            Integer participantId = MedicalRecordUtil.getParticipantIdByDdpParticipantId(ddpParticipantId, realm);
            if (participantId == null) {
                throw new DSMBadRequestException("Participant does not exist. DDP participant ID=" + ddpParticipantId);
            }
            patch.setParentId(participantId.toString());

            logger.info("Medical record not found for participant {}, creating one", ddpParticipantId);
            mrID = OncHistoryDetail.verifyOrCreateMedicalRecord(participantId, ddpParticipantId, realm, true);
        }

        oncHistoryDetailId = Integer.toString(OncHistoryDetail.createOncHistoryDetail(mrID, patch.getUser()));
        logger.info("[OncHistoryDetailPatch] Created oncHistoryDetail record (ID={}) for participant {}, medicalRecordId={}",
                oncHistoryDetailId, ddpParticipantId, mrID);
        resultMap.put(ONC_HISTORY_DETAIL_ID, oncHistoryDetailId);
    }

    @Override
    protected Object patchNameValuePairs() {
        prepare();
        if (mrID == null) {
            logger.error("No medical record id for oncHistoryDetails ");
            return NULL_KEY;
        }
        processMultipleNameValues();
        return resultMap;
    }

    @Override
    protected Object patchNameValuePair() {
        prepare();
        Optional<Object> maybeSingleNameValue = processSingleNameValue();
        return maybeSingleNameValue.orElse(resultMap);
    }

    @Override
    Object handleSingleNameValue() {
        if (Patch.patch(oncHistoryDetailId, patch.getUser(), patch.getNameValue(), dbElement)) {
            nameValues.addAll(setWorkflowRelatedFields(patch));
            DDPInstance instance = DDPInstance.getDDPInstance(patch.getRealm());
            List<NameValue> nameValuesOd = new ArrayList<>();
            nameValuesOd.add(patch.getNameValue());
            nameValuesOd.add(new NameValue("oD.ddpInstanceId", instance.getDdpInstanceId()));
            exportToESWithId(oncHistoryDetailId, nameValuesOd);
            //set oncHistoryDetails created if it is a oncHistoryDetails value without a ID, otherwise created should already be set
            if (dbElement.getTableName().equals(DBConstants.DDP_ONC_HISTORY_DETAIL)) {
                int participantId = Integer.parseInt(patch.getParentId());
                String createdDate = OncHistoryService.setCreatedNow(participantId, patch.getUser());
                if (createdDate != null) {
                    NameValue oncHistoryCreated = new NameValue("o.created", createdDate);
                    exportToESWithId(patch.getParentId(), oncHistoryCreated);
                    nameValues.add(oncHistoryCreated);
                }
            }
        }
        resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
        return resultMap;
    }

    @Override
    protected String getIdForES() {
        return oncHistoryDetailId;
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        Patch.patch(oncHistoryDetailId, patch.getUser(), nameValue, dbElement);
        return Optional.empty();
    }

}
