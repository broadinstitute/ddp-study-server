package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class ParamsGenerator implements Generator {

    private Object source;
    private String realm;

    public ParamsGenerator(Object source, String realm) {
        this.source = source;
        this.realm = realm;
    }

    @Override
    public Map<String, Object> generate() {
        Map<String, Object> fieldsMap = new ObjectTransformer(realm).transformObjectToMap(source);
        return new HashMap<>(Map.of(ESObjectConstants.DSM,
                new HashMap<>(Map.of(getPropertyName(), fieldsMap))));
    }

    @Override
    public String getPropertyName() {
        return Util.capitalCamelCaseToLowerCamelCase(source.getClass().getSimpleName());
    }
}
