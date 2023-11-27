package org.broadinstitute.dsm.model.elastic.mapping;

import org.broadinstitute.dsm.util.ElasticSearchUtil;

public interface TypeExtractor<T> {
    T extract();

    default String getRightMostFieldName(String fullFieldName) {
        String[] splittedFieldName = fullFieldName.split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
        return splittedFieldName[splittedFieldName.length - 1];
    }

    void setIndex(String index);

    default void setFields(String... fields) {}
}
