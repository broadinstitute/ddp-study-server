package org.broadinstitute.dsm.model.elastic.converters;

import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;

public interface Converter<T> {

    T convert();

    default void setParser(BaseParser parser) {}

}
