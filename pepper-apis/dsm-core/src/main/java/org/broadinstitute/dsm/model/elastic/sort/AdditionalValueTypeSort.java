package org.broadinstitute.dsm.model.elastic.sort;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;

public class AdditionalValueTypeSort extends Sort {

    AdditionalValueTypeSort(SortBy sortBy, TypeExtractor<Map<String, String>> typeExtractor) {
        super(sortBy, typeExtractor);
    }

    @Override
    String handleOuterPropertySpecialCase() {
        return Type.ADDITIONALVALUE.getValue();
    }

    @Override
    protected String getKeywordIfText(Type innerType) {
        return super.getKeywordIfText(Type.of(sortBy.getAdditionalType()));
    }
}
