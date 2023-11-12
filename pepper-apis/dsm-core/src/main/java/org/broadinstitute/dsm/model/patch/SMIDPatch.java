package org.broadinstitute.dsm.model.patch;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.statics.DBConstants;

public class SMIDPatch extends BasePatch {

    public static final String SM_ID_PK = "smIdPk";
    public static final String SM_ID_VALUE = "smIdValue";

    public SMIDPatch(Patch patch) {
        super(patch);
    }

    @Override
    public Object doPatch() {
        return patchNameValuePair();
    }

    @Override
    protected Object patchNameValuePairs() {
        return null;
    }

    private String getSMIDValue() {
        NameValue nameValue = patch.getNameValues().get(1);
        return String.valueOf(nameValue.getValue());
    }

    private String getSMIDType() {
        return String.valueOf(patch.getNameValue().getValue());
    }

    @Override
    protected Object patchNameValuePair() {
        if (StringUtils.isNotBlank(getSMIDValue()) && SmId.isUniqueSmId(getSMIDValue())) {
            int smIdPk = new TissueSMIDDao().createNewSMIDForTissue(patch.getParentIdAsInt(), patch.getUser(), getSMIDType(), getSMIDValue());
            if (smIdPk > 0) {
                resultMap.put(SM_ID_PK, smIdPk);
                NameValue nameValue =
                        new SMIDNameValue(String.join(DBConstants.ALIAS_DELIMITER, DBConstants.SM_ID_ALIAS, SM_ID_VALUE), getSMIDValue(),
                                getSMIDType());
                exportToESWithId(Integer.toString(smIdPk), nameValue);
            }
            return resultMap;
        } else {
            throw new DsmInternalError("Duplicate or blank value for sm id value " + getSMIDValue());
        }
    }


    @Override
    Object handleSingleNameValue() {
        return null;
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        return Optional.empty();
    }
}
