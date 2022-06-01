package org.broadinstitute.dsm.model.elastic.converters;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;

public interface Converter {

    Map<String, Object> convert();

    default void setParser(BaseParser parser) {}

}
