package org.broadinstitute.dsm.model.elastic.migration;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.statics.ESObjectConstants;

public abstract class BaseSingleMigrator extends BaseMigrator {

    private Map<String, Object> transformedObject;

    public BaseSingleMigrator(String index, String realm, String object) {
        super(index, realm, object);
    }

    @Override
    public Map<String, Object> generate() {
        return new HashMap<>(Map.of(ESObjectConstants.DSM, new HashMap<>(Map.of(entity, transformedObject))));
    }

    @Override
    protected void transformObject(Object object) {
        transformedObject = objectTransformer.transformObjectToMap(object);
    }
}
