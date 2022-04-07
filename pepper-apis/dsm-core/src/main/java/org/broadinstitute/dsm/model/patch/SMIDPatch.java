package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.model.NameValue;

import java.util.Optional;

public class SMIDPatch extends BasePatch {

    private String smIdPk;

    public SMIDPatch(Patch patch) {
        super(patch);
    }

    @Override
    public Object doPatch() {
        return null;
    }

    @Override
    protected Object patchNameValuePairs() {
        return null;
    }

    @Override
    protected Object patchNameValuePair() {
        prepare();
        Optional<Object> maybeNameValue = processSingleNameValue();
        return maybeNameValue.orElse(resultMap);
    }

    private void prepare() {
        smIdPk = Tissue.createNewTissue(patch.getParentId(), patch.getUser());
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
