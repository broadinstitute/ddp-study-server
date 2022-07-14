package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/** handles questions with lists of lists for answers */
public class CompositeValueProvider extends TextValueProvider {
    protected Collection<?> mapToCollection(Object o) {
        if (o != null && o instanceof Collection) {
            List<Object> allValues = new ArrayList<>();
            // flatten any nested lists
            for (Object item : ((Collection<?>) o)) {
                if (item instanceof Collection) {
                    allValues.addAll((Collection) item);
                } else {
                    allValues.add(item);
                }
            }
            // replace any nulls with empty string
            return allValues.stream().map(val -> val == null ? StringUtils.EMPTY : val).collect(Collectors.toList());
        } else {
            return super.mapToCollection(o);
        }
    }
}
