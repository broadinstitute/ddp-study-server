package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.model.AbstractionWrapper;
import org.broadinstitute.dsm.model.NameValue;
import spark.utils.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AbstractionPatch extends BasePatch {

    String primaryKeyId;
    public static final int FIRST_PRIMARY_KEY_ID = 0;

    static {
        NULL_KEY = new HashMap<>();
        NULL_KEY.put(PRIMARY_KEY_ID, null);
    }

    public AbstractionPatch(Patch patch) {
        super(patch);
    }

    @Override
    public Object doPatch() {
        return isNameValuePairs() ? patchNameValuePairs() : patchNameValuePair();
    }

    @Override
    protected Object patchNameValuePairs() {
        List<Object> firstPrimaryKey = processMultipleNameValues();
        return firstPrimaryKey.isEmpty() ? NULL_KEY : firstPrimaryKey.get(FIRST_PRIMARY_KEY_ID);
    }

    @Override
    public Object patchNameValuePair() {
        Optional<Object> maybeMap = processSingleNameValue();
        return maybeMap.orElse(NULL_KEY);
    }

    @Override
    Object handleSingleNameValue() {
        String primaryKeyId = AbstractionWrapper.createNewAbstractionFieldValue(patch.getParentId(), patch.getFieldId(), patch.getUser(), patch.getNameValue(), dbElement);
        return Map.of(PRIMARY_KEY_ID, primaryKeyId);
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        if (StringUtils.isBlank(primaryKeyId)) {
            primaryKeyId = AbstractionWrapper.createNewAbstractionFieldValue(patch.getParentId(), patch.getFieldId(), patch.getUser(), nameValue, dbElement);
        }
        Patch.patch(primaryKeyId, patch.getUser(), nameValue, dbElement);
        return Optional.ofNullable(primaryKeyId);
    }
}
