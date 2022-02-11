package org.broadinstitute.dsm.model.patch;

import java.util.HashMap;
import java.util.Optional;

import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OncHistoryDetailPatch extends BasePatch {

    private static final String ONC_HISTORY_DETAIL_ID = "oncHistoryDetailId";

    private Number mrID;
    private String oncHistoryDetailId;

    static final Logger logger = LoggerFactory.getLogger(OncHistoryDetailPatch.class);


    public OncHistoryDetailPatch(Patch patch) {
        super(patch);
    }

    @Override
    public Object doPatch() {
        return isNameValuePairs() ? patchNameValuePairs() : patchNameValuePair();
    }

    {
        nameValues.add(new NameValue("request", OncHistoryDetail.STATUS_REVIEW));
        resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
    }
    
    static {
        NULL_KEY = new HashMap<>();
        NULL_KEY.put(NAME_VALUE, null);
    }

    private void prepare() {
        mrID = MedicalRecordUtil.isInstitutionTypeInDB(patch.getParentId());
        if (mrID == null) {
            // mr of that type doesn't exist yet, so create an institution and mr
            MedicalRecordUtil.writeInstitutionIntoDb(patch.getParentId(), MedicalRecordUtil.NOT_SPECIFIED);
            mrID = MedicalRecordUtil.isInstitutionTypeInDB(patch.getParentId());
        }
        if (mrID != null) {
            oncHistoryDetailId = OncHistoryDetail.createNewOncHistoryDetail(mrID.toString(), patch.getUser());
        }
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
            exportToESWithId(oncHistoryDetailId, patch.getNameValue());
            //set oncHistoryDetails created if it is a oncHistoryDetails value without a ID, otherwise created should already be set
            if (dbElement.getTableName().equals(DBConstants.DDP_ONC_HISTORY_DETAIL)) {
                NameValue oncHistoryCreated = OncHistory.setOncHistoryCreated(patch.getParentId(), patch.getUser());
                if (oncHistoryCreated.getValue() != null) {
                    nameValues.add(oncHistoryCreated);
                }
            }
        }
        resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
        return resultMap;
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        Patch.patch(oncHistoryDetailId, patch.getUser(), nameValue, dbElement);
        exportToESWithId(oncHistoryDetailId, nameValue);
        return Optional.empty();
    }

}
