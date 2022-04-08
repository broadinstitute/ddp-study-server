package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.model.NameValue;

import java.util.Map;
import java.util.Optional;

public class SMIDPatch extends BasePatch {

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
        String smIdPk = new TissueSMIDDao().createNewSMIDForTissue(patch.getParentId(), patch.getUser(), getSMIDType(), getSMIDValue());
        if (Integer.parseInt(smIdPk) > 0) {
            NameValue nameValue = new NameValue("sm.smIdValue", getSMIDValue());
            exportToESWithId(smIdPk, nameValue);
        }
        return resultMap;
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
