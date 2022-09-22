package org.broadinstitute.dsm.model.elastic.converters.split;

public interface FieldNameSplittingStrategy {
    String[] split(String words);
}
