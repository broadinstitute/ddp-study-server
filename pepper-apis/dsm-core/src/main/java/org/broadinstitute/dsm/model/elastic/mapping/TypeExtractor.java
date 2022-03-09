package org.broadinstitute.dsm.model.elastic.mapping;

public interface TypeExtractor<T> {
    T extract();
    void setIndex(String index);
    default void setFields(String... fields) {}
}
