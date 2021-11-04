package org.broadinstitute.dsm.model.patch;

import java.util.Optional;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;

public class NullPatch extends BasePatch {

    @Override
    public Object doPatch() {
        return new Object();
    }

    @Override
    protected Object patchNameValuePairs() {
        return new Object();
    }

    @Override
    public Object patchNameValuePair() {
        return new Object();
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        return Optional.empty();
    }

    @Override
    Object handleSingleNameValue() {
        return new Object();
    }
}
