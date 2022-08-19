package org.broadinstitute.dsm.model.patch;

import java.util.Optional;

import org.broadinstitute.dsm.model.NameValue;

public class OncHistoryPatch extends BasePatch {

    @Override
    public Object doPatch() {
        return patchNameValuePair();
    }

    @Override
    protected Object patchNameValuePairs() {
        return null;
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
        return Optional.empty();
    }

}
