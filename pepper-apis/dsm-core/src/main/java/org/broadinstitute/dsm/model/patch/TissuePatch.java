package org.broadinstitute.dsm.model.patch;

import java.util.Optional;

import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;

public class TissuePatch extends BasePatch {

    public static final String TISSUE_ID = "tissueId";
    private String tissueId;
    
    private void prepare() {
        tissueId = Tissue.createNewTissue(patch.getParentId(), patch.getUser());
    }

    public TissuePatch(Patch patch) {
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

    @Override
    protected Object patchNameValuePair() {
        prepare();
        Optional<Object> maybeNameValue = processSingleNameValue();
        return maybeNameValue.orElse(resultMap);
    }

    @Override
    Object handleSingleNameValue() {
        if (Patch.patch(tissueId, patch.getUser(), patch.getNameValue(), dbElement)) {
            nameValues = setWorkflowRelatedFields(patch);
            resultMap.put(TISSUE_ID, tissueId);
            if (!nameValues.isEmpty()) {
                resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
            }
            exportToESWithId(tissueId, patch.getNameValue());
        }
        return resultMap;
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        return Optional.empty();
    }
}
