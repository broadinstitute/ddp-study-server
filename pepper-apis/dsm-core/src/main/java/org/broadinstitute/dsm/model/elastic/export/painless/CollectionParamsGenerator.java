package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class CollectionParamsGenerator<T> implements Generator {
    private List<T> sources;
    private String realm;

    public CollectionParamsGenerator(List<T> sources, String realm) {
        this.sources = sources;
        this.realm = realm;
    }

    @Override
    public Map<String, Object> generate() {
        List<Map<String, Object>> objects = new ObjectTransformer(realm).transformObjectCollectionToCollectionMap((List<Object>) sources);
        return new HashMap<>(Map.of(ESObjectConstants.DSM,
                new HashMap<>(Map.of(getPropertyName(), objects))));
    }

    @Override
    public String getPropertyName() {
        if (sources.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return Util.capitalCamelCaseToLowerCamelCase(sources.get(0).getClass().getSimpleName());
    }
}
